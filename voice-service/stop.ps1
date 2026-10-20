# 结束本地语音服务并释放显存（后端已经挂掉、页面点不到"停止"时用）
#
# 用法（Windows PowerShell 5.1 与 PowerShell 7 都可以）：
#   powershell -NoProfile -File stop.ps1
#   pwsh -NoProfile -File stop.ps1 -Port 9100
#
# 为什么需要它：venv 里的 python.exe 只是启动器，它会再拉起一个 base 解释器去跑 uvicorn，
# 真正占显存的是后者。只结束启动器（或只看 tasklist 里的第一条 python）会留下占着几个 GB 的
# 孤儿进程——后端已经停了、显存却没降，就是这种情况。
#
# 注意：本文件必须以 UTF-8 **带 BOM** 保存。PowerShell 5.1 对没有 BOM 的 .ps1 按系统 ANSI(cp936) 读，
# 中文会被解坏，连语法都可能一起坏掉（踩过：脚本"跑完了"但一个进程都没杀）。

param(
    [int]$Port = 9100
)

$ErrorActionPreference = "Stop"

# 端口上的监听进程
function Get-ListenerPids {
    try {
        return @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop |
            Select-Object -ExpandProperty OwningProcess |
            Sort-Object -Unique)
    } catch {
        # 老系统没有 Get-NetTCPConnection 时退回 netstat
        $lines = netstat -ano | Select-String ":$Port\s" | Select-String "LISTENING"
        $pids = @()
        foreach ($line in $lines) {
            $fields = ($line.ToString().Trim() -split '\s+')
            $pids += [int]$fields[-1]
        }
        return @($pids | Sort-Object -Unique)
    }
}

# sidecar 的 python 进程：命令行里既跑 uvicorn app.main:app、又用本端口
function Get-SidecarPids {
    try {
        return @(Get-CimInstance Win32_Process -Filter "Name = 'python.exe'" -ErrorAction Stop |
            Where-Object {
                $_.CommandLine -like "*uvicorn*app.main:app*" -and $_.CommandLine -like "*--port $Port*"
            } |
            Select-Object -ExpandProperty ProcessId)
    } catch {
        Write-Warning "无法枚举 python 进程（$($_.Exception.Message)），只按端口处理。"
        return @()
    }
}

$targets = @()
$targets += Get-ListenerPids
$targets += Get-SidecarPids
$targets = @($targets | Sort-Object -Unique)

if ($targets.Count -eq 0) {
    Write-Host "端口 $Port 没有监听者，也没有找到 voice-service 进程，显存应当已经释放。" -ForegroundColor Green
    exit 0
}

foreach ($procId in $targets) {
    $name = "?"
    $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if ($proc) { $name = $proc.ProcessName }
    Write-Host "结束 PID $procId ($name)"
    # /T 连子孙一起结束：显存挂在被启动器拉起的那个进程上
    $output = & taskkill /T /F /PID $procId 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  taskkill 返回 $LASTEXITCODE：$output"
    }
}

Start-Sleep -Milliseconds 800

if ((Get-ListenerPids).Count -gt 0) {
    Write-Warning "端口 $Port 仍在监听，请用「资源监视器」或 nvidia-smi 确认残留进程后手动结束。"
    exit 1
}

Write-Host "端口 $Port 已释放。" -ForegroundColor Green
Write-Host "若仍觉得显存没下来，执行 nvidia-smi 看还有没有 python.exe 占着。" -ForegroundColor DarkGray
exit 0

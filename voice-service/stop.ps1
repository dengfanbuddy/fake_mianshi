# 结束本地语音服务并释放显存（后端已经挂掉、页面点不到"停止"时用）
#
# 用法：
#   pwsh -File stop.ps1            # 默认端口 9100
#   pwsh -File stop.ps1 -Port 9100
#
# 为什么需要它：venv 里的 python.exe 只是启动器，它会再拉起一个 base 解释器去跑 uvicorn，
# 真正占显存的是后者。只结束启动器（或只看 tasklist 里的第一条 python）会留下占着几个 GB 的
# 孤儿进程——后端已经停了、显存却没降，就是这种情况。

param(
    [int]$Port = 9100
)

$ErrorActionPreference = "Continue"

function Get-Listeners {
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess |
        Sort-Object -Unique
}

# sidecar 的 python 进程：命令行里既跑 uvicorn app.main:app、又用本端口
function Get-SidecarPythons {
    Get-CimInstance Win32_Process -Filter "Name = 'python.exe'" -ErrorAction SilentlyContinue |
        Where-Object {
            $_.CommandLine -like "*uvicorn*app.main:app*" -and $_.CommandLine -like "*--port $Port*"
        } |
        Select-Object -ExpandProperty ProcessId
}

$targets = @()
$targets += Get-Listeners
$targets += Get-SidecarPythons
$targets = $targets | Sort-Object -Unique

if (-not $targets) {
    Write-Host "端口 $Port 没有监听者，也没有找到 voice-service 进程，显存应当已经释放。" -ForegroundColor Green
    exit 0
}

foreach ($procId in $targets) {
    $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
    $name = if ($proc) { $proc.ProcessName } else { "?" }
    Write-Host "结束 PID $procId ($name)"
    # /T 连子孙一起结束：显存挂在被启动器拉起的那个进程上
    & taskkill /T /F /PID $procId 2>&1 | Out-Null
}

Start-Sleep -Milliseconds 800

if (Get-Listeners) {
    Write-Warning "端口 $Port 仍在监听，请用「资源监视器」或 nvidia-smi 确认残留进程后手动结束。"
    exit 1
}

Write-Host "端口 $Port 已释放。" -ForegroundColor Green
Write-Host "若仍觉得显存没下来，执行 nvidia-smi 看还有没有 python.exe 占着。" -ForegroundColor DarkGray
exit 0

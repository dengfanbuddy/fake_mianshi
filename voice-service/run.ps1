# 启动本地语音服务（默认 http://127.0.0.1:9100）
# 用法：pwsh -File run.ps1 [-Port 9100] [-Reload]

param(
    [int]$Port = 9100,
    [switch]$Reload
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

# 国内下载模型走 HF 镜像（仅在未指定本地模型目录时生效）
if (-not $env:HF_ENDPOINT) { $env:HF_ENDPOINT = "https://hf-mirror.com" }
$env:VOICE_PORT = "$Port"

$reloadArg = if ($Reload) { "--reload" } else { "" }
Write-Host "voice-service 启动中（端口 $Port，模型 $($env:QWEN_TTS_MODEL)）..." -ForegroundColor Cyan
if ($reloadArg) {
    uv run python -m uvicorn app.main:app --host 127.0.0.1 --port $Port $reloadArg
} else {
    uv run python -m uvicorn app.main:app --host 127.0.0.1 --port $Port
}

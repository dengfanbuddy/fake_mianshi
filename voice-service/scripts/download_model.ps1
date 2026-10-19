# 下载 Qwen3-TTS 权重到本地目录（国内走 ModelScope，比 HF 稳）
# 用法：pwsh -File scripts/download_model.ps1 [-Model Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice]

param(
    [string]$Model = "Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice",
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) {
    $OutDir = Join-Path $root ("models/" + ($Model -split "/")[-1])
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

Write-Host "下载 $Model → $OutDir" -ForegroundColor Cyan
# modelscope 会复用同一份缓存，重复执行是增量的
uv run --project $root --with modelscope modelscope download --model $Model --local_dir $OutDir

Write-Host "完成。启动服务时指定：" -ForegroundColor Green
Write-Host "  `$env:QWEN_TTS_MODEL='$OutDir'" -ForegroundColor Green

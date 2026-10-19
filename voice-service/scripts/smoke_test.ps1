#!/usr/bin/env pwsh
# 端到端冒烟测试：sidecar /health → /tts → /tts/stream
# 用法：pwsh -File scripts/smoke_test.ps1 [-BaseUrl http://127.0.0.1:9100]

param(
    [string]$BaseUrl = "http://127.0.0.1:9100",
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) { $OutDir = Join-Path $root "smoke_out" }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

Write-Host "== 1) GET /health ==" -ForegroundColor Cyan
$health = Invoke-RestMethod -Uri "$BaseUrl/health" -TimeoutSec 20
$health | ConvertTo-Json -Depth 4

if (-not $health.loaded) {
    Write-Host "模型未加载完成，先 POST /warmup（首次约 15-60s）..." -ForegroundColor Yellow
    Invoke-RestMethod -Uri "$BaseUrl/warmup" -Method Post -TimeoutSec 300 | ConvertTo-Json -Depth 3
}

Write-Host "== 2) POST /tts（整句 wav） ==" -ForegroundColor Cyan
$body = @{
    text     = "你好，我是今天的面试官，我们先聊一下你最近做的项目。"
    speaker  = "uncle_fu"
    instruct = "冷静、专业的技术面试官，语速中等"
    language = "Chinese"
} | ConvertTo-Json -Compress

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$resp = Invoke-WebRequest -Uri "$BaseUrl/tts" -Method Post -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 180
$sw.Stop()
$wavPath = Join-Path $OutDir "tts_sentence.wav"
[System.IO.File]::WriteAllBytes($wavPath, $resp.Content)
Write-Host ("耗时 {0} ms, TTFA {1} ms, RTF {2}, 音频 {3}s → {4}" -f `
    $sw.ElapsedMilliseconds, $resp.Headers['X-TTFA-Ms'], $resp.Headers['X-Rtf'], `
    $resp.Headers['X-Audio-Seconds'], $wavPath) -ForegroundColor Green

Write-Host "== 3) POST /tts/stream（PCM 分块） ==" -ForegroundColor Cyan
$sw2 = [System.Diagnostics.Stopwatch]::StartNew()
$resp2 = Invoke-WebRequest -Uri "$BaseUrl/tts/stream" -Method Post -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 180
$sw2.Stop()
$pcmPath = Join-Path $OutDir "stream.pcm"
[System.IO.File]::WriteAllBytes($pcmPath, $resp2.Content)
$seconds = $resp2.Content.Length / 32000.0
Write-Host ("流式总耗时 {0} ms，PCM {1} 字节 ≈ {2:N2}s 音频 → {3}" -f `
    $sw2.ElapsedMilliseconds, $resp2.Content.Length, $seconds, $pcmPath) -ForegroundColor Green

Write-Host "全部通过。用播放器打开 $wavPath 试听音色。" -ForegroundColor Green

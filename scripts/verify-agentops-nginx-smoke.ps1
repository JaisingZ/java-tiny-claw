param(
    [switch]$KeepRunning
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$workspace = Join-Path $repoRoot "examples\agentops-nginx-workspace"
$remote = Join-Path $workspace "remote"
$composeDir = Join-Path $repoRoot "examples\agentops-nginx-docker"

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "缺少命令：$Name"
    }
}

Require-Command docker

@'
events {}

http {
    server {
        listen 8080;
        server_name localhost;

        locat / {
            return 200 "agentops nginx ok\n";
        }
    }
}
'@ | Set-Content -Path (Join-Path $remote "nginx.conf") -Encoding ASCII

@'
2026/06/15 20:58:00 [info] 100#100: *1 client 192.168.1.10 connected
2026/06/15 20:58:01 [info] 100#100: *2 client 192.168.1.11 connected
2026/06/15 20:59:12 [emerg] 100#100: unknown directive "locat" in /workspace/nginx.conf:8
2026/06/15 20:59:59 [emerg] 100#100: configuration file /workspace/nginx.conf test failed
'@ | Set-Content -Path (Join-Path $remote "error.log") -Encoding ASCII

Push-Location $composeDir
try {
    docker compose up -d --build | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up 失败，无法启动 AgentOps nginx 场景。"
    }
} finally {
    Pop-Location
}

$containerName = "tinyclaw-agentops-nginx"

$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    $output = docker exec $containerName sh -lc "tail -n 5 /workspace/error.log && nginx -t -c /workspace/nginx.conf" 2>&1 |
        ForEach-Object { $_.ToString() }
    $exitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorActionPreference
}
$output | Out-Host

if ($exitCode -eq 0) {
    throw "预期 nginx -t 因 locat 指令失败，但命令成功了。"
}
$joinedOutput = ($output | Out-String)
if ($joinedOutput -notmatch "unknown directive" -and $joinedOutput -notmatch "configuration file .*test failed") {
    throw "Docker/nginx smoke 未到达预期 nginx 配置错误，实际输出：$joinedOutput"
}

Write-Host ""
Write-Host "Docker nginx 故障现场已就绪。"
Write-Host "本地验证命令："
Write-Host "  docker exec tinyclaw-agentops-nginx nginx -t -c /workspace/nginx.conf"
Write-Host "  docker exec tinyclaw-agentops-nginx nginx -s reload"
Write-Host ""
Write-Host "建议 agent.properties："
Write-Host "  agent.workdir=examples/agentops-nginx-workspace"
Write-Host "  agent.permissions.enabled=true"
Write-Host "  agent.intentFilter.enabled=true"
Write-Host "  agent.intentFilter.marker.1=/agent"
Write-Host ""
Write-Host "Telegram 触发消息："
Write-Host "  /agent 帮我排查 nginx 起不来并尝试修复"
Write-Host ""
Write-Host "审批时回复：/approve <id> 或 /reject <id>"

if (-not $KeepRunning) {
    Write-Host ""
    Write-Host "保持容器运行以便继续 Telegram smoke；完成后可执行："
    Write-Host "  docker compose -f examples/agentops-nginx-docker/docker-compose.yml down"
}

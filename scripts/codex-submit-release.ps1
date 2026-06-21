param(
    [string]$Message = "chore: submit codex changes for release"
)

$ErrorActionPreference = "Stop"

function Run-Git {
    & git @args
    if ($LASTEXITCODE -ne 0) {
        throw "git $($args -join ' ') failed with exit code $LASTEXITCODE"
    }
}

$branch = (& git branch --show-current).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($branch)) {
    throw "Unable to resolve current branch."
}

if ($branch -notlike "codex/*") {
    throw "codex-submit-release.ps1 only runs on codex/** branches. Current branch: $branch"
}

$javaHome = "C:\Program Files\BellSoft\LibericaJDK-21"
if (-not (Test-Path (Join-Path $javaHome "bin\java.exe"))) {
    throw "Java 21 not found at $javaHome"
}

$env:JAVA_HOME = $javaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

& mvn -B test package
if ($LASTEXITCODE -ne 0) {
    throw "mvn -B test package failed with exit code $LASTEXITCODE"
}

Run-Git add -A -- . ":(exclude).tinyclaw/**" ":(exclude)examples/agentops-nginx-workspace/.tinyclaw/traces/**"

$staged = (& git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) {
    throw "git diff --cached --name-only failed with exit code $LASTEXITCODE"
}

if ([string]::IsNullOrWhiteSpace(($staged -join ""))) {
    Write-Host "No staged changes. Pushing current branch only."
    Run-Git push origin HEAD
    exit 0
}

Write-Host "Staged changes:"
$staged | ForEach-Object { Write-Host "  $_" }

Run-Git commit -m $Message
Run-Git push origin HEAD

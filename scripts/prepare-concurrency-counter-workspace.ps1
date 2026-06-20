param()

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$workspace = Join-Path $repoRoot "target\concurrency-counter-workspace"
$targetRoot = Join-Path $repoRoot "target"
$resolvedTargetRoot = [System.IO.Path]::GetFullPath($targetRoot)
$resolvedWorkspace = [System.IO.Path]::GetFullPath($workspace)

if (-not $resolvedWorkspace.StartsWith($resolvedTargetRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refuse to prepare workspace outside target: $resolvedWorkspace"
}

Write-Host "[concurrency-counter] prepare workspace: $workspace"

if (Test-Path $workspace) {
    Remove-Item -Recurse -Force $workspace
}
New-Item -ItemType Directory -Path $workspace | Out-Null

$source = @'
public final class CounterRaceCheck {
    private static int counter = 0;

    public static void main(String[] args) throws Exception {
        int expected = args.length == 0 ? 100000 : Integer.parseInt(args[0]);
        int workers = 8;
        int perWorker = expected / workers;
        Thread[] threads = new Thread[workers];
        for (int i = 0; i < workers; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < perWorker; j++) {
                    int next = counter + 1;
                    if ((j & 255) == 0) {
                        Thread.yield();
                    }
                    counter = next;
                }
            }, "counter-" + i);
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("expected=" + expected);
        System.out.println("actual=" + counter);
        if (counter == expected) {
            System.out.println("result=ok");
        } else {
            System.out.println("result=failed");
        }
    }
}
'@

$validation = @'
$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot

javac CounterRaceCheck.java
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$out = java CounterRaceCheck 100000
$javaExitCode = $LASTEXITCODE
if ($javaExitCode -ne 0) {
    Write-Host "counter run exit code: $javaExitCode"
    Write-Host $out
    exit $javaExitCode
}
$outputText = $out -join "`n"

if ($outputText -notmatch "expected=100000") {
    Write-Host $outputText
    throw "expected not matched"
}
if ($outputText -notmatch "actual=100000") {
    Write-Host $outputText
    throw "actual not matched"
}
if ($outputText -notmatch "result=ok") {
    Write-Host $outputText
    throw "validation failed"
}

Write-Host "validation ok"
Write-Host $outputText
'@

$readme = @'
Concurrency Counter Workspace
=============================

1) Compile:
   javac CounterRaceCheck.java

2) Run:
   java CounterRaceCheck 100000

3) One-shot validation:
   powershell -File .\validation.ps1
'@

Set-Content -Path (Join-Path $workspace "CounterRaceCheck.java") -Value $source -Encoding ASCII
Set-Content -Path (Join-Path $workspace "validation.ps1") -Value $validation -Encoding ASCII
Set-Content -Path (Join-Path $workspace "README.txt") -Value $readme -Encoding ASCII

Write-Host "[concurrency-counter] done:"
Write-Host ("  project: " + $workspace)
Write-Host "  files: CounterRaceCheck.java, validation.ps1, README.txt"
Write-Host "  note: script only prepares workspace; it does not run model."

param(
    [switch] $Live
)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $repoRoot

$javaHome = 'C:\Program Files\BellSoft\LibericaJDK-21'
if (Test-Path $javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

Write-Host '=== Build Tiny Agent Harness ==='
mvn -q -DskipTests package

Write-Host '=== Build runtime classpath ==='
mvn -q -DincludeScope=runtime dependency:build-classpath `
    "-Dmdep.outputFile=target\main-loop-startup-classpath.txt"

$dependencyClasspath = (Get-Content 'target\main-loop-startup-classpath.txt' -Raw).Trim()
$classpath = "target\classes;$dependencyClasspath"

$runnerArgs = @()
if ($Live) {
    $runnerArgs += '--live'
}

Write-Host '=== Run Main Loop startup check ==='
java -cp $classpath io.github.tinyclaw.agent.app.AgentApplication startup-check @runnerArgs

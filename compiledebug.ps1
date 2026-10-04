param(
    [string]$Module = "app"
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Kotlin Debug Compilation" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# --------------------------------------------------
# 1. Validate project root
# --------------------------------------------------

if (-not (Test-Path ".\gradlew.bat")) {
    Write-Host "[ERROR] gradlew.bat was not found." -ForegroundColor Red
    Write-Host "Run this script from the Android project root." -ForegroundColor Yellow
    exit 1
}

# --------------------------------------------------
# 2. Compile Kotlin
# --------------------------------------------------

$task = ":${Module}:compileDebugKotlin"

Write-Host "Compiling Kotlin: $Module" -ForegroundColor Yellow
Write-Host "Task: $task" -ForegroundColor Gray
Write-Host ""

& .\gradlew.bat $task "--console=plain"

$exitCode = $LASTEXITCODE

if ($exitCode -ne 0) {
    Write-Host ""
    Write-Host "============================================" -ForegroundColor Red
    Write-Host " KOTLIN COMPILATION FAILED" -ForegroundColor Red
    Write-Host "============================================" -ForegroundColor Red
    Write-Host ""

    exit $exitCode
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host " KOTLIN COMPILATION SUCCESSFUL" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
param(
    [string]$Emulator = "medium_phone"
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Android Emulator Launcher" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "AVD: $Emulator" -ForegroundColor Gray
Write-Host ""

# --------------------------------------------------
# Find the emulator process for the selected AVD
# --------------------------------------------------

function Get-TargetEmulatorProcess {
    $escapedName = [regex]::Escape($Emulator)

    @(
        Get-CimInstance Win32_Process `
            -Filter "Name = 'emulator.exe'" `
            -ErrorAction SilentlyContinue |
        Where-Object {
            $_.CommandLine -and
            $_.CommandLine -match "(?i)(-avd\s+|\@)$escapedName(\s|$)"
        }
    )
}

# --------------------------------------------------
# 1. Detect an existing emulator instance
# --------------------------------------------------

$existingProcesses = @(Get-TargetEmulatorProcess)

if ($existingProcesses.Count -gt 0) {

    Write-Host "Existing emulator detected." -ForegroundColor Yellow
    Write-Host "Stopping $Emulator..." -ForegroundColor Yellow
    Write-Host ""

    # Attempt graceful shutdown through Android CLI.
    & android emulator stop $Emulator 2>$null | Out-Null

    $stopped = $false

    # Wait up to 30 seconds for graceful shutdown.
    for ($i = 1; $i -le 30; $i++) {

        Start-Sleep -Seconds 1

        $remainingProcesses = @(Get-TargetEmulatorProcess)

        if ($remainingProcesses.Count -eq 0) {
            $stopped = $true
            break
        }
    }

    # Force-stop only the selected AVD if necessary.
    if (-not $stopped) {

        Write-Host "Graceful shutdown timed out." -ForegroundColor Yellow
        Write-Host "Forcing emulator shutdown..." -ForegroundColor Yellow
        Write-Host ""

        $remainingProcesses = @(Get-TargetEmulatorProcess)

        foreach ($process in $remainingProcesses) {

            Stop-Process `
                -Id $process.ProcessId `
                -Force `
                -ErrorAction SilentlyContinue
        }

        Start-Sleep -Seconds 2
    }

    Write-Host "[OK] Existing emulator stopped." -ForegroundColor Green
    Write-Host ""
}
else {
    Write-Host "No running emulator instance detected." -ForegroundColor Gray
    Write-Host ""
}

# --------------------------------------------------
# 2. Start the emulator
# --------------------------------------------------

Write-Host "Starting $Emulator..." -ForegroundColor Yellow
Write-Host ""

& android emulator start $Emulator

$startExitCode = $LASTEXITCODE

if ($startExitCode -ne 0) {
    Write-Host ""
    Write-Host "============================================" -ForegroundColor Red
    Write-Host " EMULATOR START FAILED" -ForegroundColor Red
    Write-Host "============================================" -ForegroundColor Red
    Write-Host ""

    exit $startExitCode
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host " EMULATOR READY" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "AVD: $Emulator"
Write-Host ""
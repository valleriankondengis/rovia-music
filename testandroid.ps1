param(
    [string]$Module = "app",
    [string]$Device = ""
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Android Build -> Install -> Launch" -ForegroundColor Cyan
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
# 2. Build debug APK
# --------------------------------------------------

$buildTask = ":${Module}:assembleDebug"

Write-Host "[1/3] Building $Module debug APK..." -ForegroundColor Yellow
Write-Host "Task: $buildTask" -ForegroundColor Gray
Write-Host ""

& .\gradlew.bat $buildTask "--console=plain"

$buildExitCode = $LASTEXITCODE

if ($buildExitCode -ne 0) {
    Write-Host ""
    Write-Host "============================================" -ForegroundColor Red
    Write-Host " BUILD FAILED" -ForegroundColor Red
    Write-Host "============================================" -ForegroundColor Red
    Write-Host ""

    exit $buildExitCode
}

Write-Host ""
Write-Host "[OK] Build completed successfully." -ForegroundColor Green
Write-Host ""

# --------------------------------------------------
# 3. Locate debug APK
# --------------------------------------------------

$apkDirectory = Join-Path $Module "build\outputs\apk\debug"

$apk = Get-ChildItem `
    -Path $apkDirectory `
    -Filter "*.apk" `
    -File `
    -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $apk) {
    Write-Host "============================================" -ForegroundColor Red
    Write-Host " DEBUG APK NOT FOUND" -ForegroundColor Red
    Write-Host "============================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Expected location: $apkDirectory" -ForegroundColor Yellow
    Write-Host ""

    exit 1
}

Write-Host "APK: $($apk.FullName)" -ForegroundColor Gray
Write-Host ""

# --------------------------------------------------
# 4. Detect ready Android devices
# --------------------------------------------------

Write-Host "[2/3] Checking connected Android devices..." -ForegroundColor Yellow
Write-Host ""

$readyDevices = @(
    adb devices |
    Select-String "^\S+\s+device$"
)

if ($readyDevices.Count -eq 0) {
    Write-Host ""
    Write-Host "============================================" -ForegroundColor Red
    Write-Host " NO READY DEVICE FOUND" -ForegroundColor Red
    Write-Host "============================================" -ForegroundColor Red
    Write-Host ""

    exit 1
}

# --------------------------------------------------
# 5. Select target device
# --------------------------------------------------

$deviceSerial = $null

if ($Device -ne "") {

    $escapedDevice = [regex]::Escape($Device)

    $matchedDevice = $readyDevices |
        Where-Object {
            $_.ToString() -match "^${escapedDevice}\s+device$"
        } |
        Select-Object -First 1

    if (-not $matchedDevice) {
        Write-Host "[ERROR] Device '$Device' was not found." -ForegroundColor Red
        Write-Host ""

        exit 1
    }

    $deviceSerial = (($matchedDevice.ToString()) -split "\s+")[0]
}
else {

    # Prefer an emulator when multiple devices are connected.
    $emulatorDevice = $readyDevices |
        Where-Object {
            $_.ToString() -match "^emulator-\d+\s+device$"
        } |
        Select-Object -First 1

    if ($emulatorDevice) {
        $deviceSerial = (($emulatorDevice.ToString()) -split "\s+")[0]
    }
    else {
        $deviceSerial = (($readyDevices[0].ToString()) -split "\s+")[0]
    }
}

Write-Host "[OK] Target device: $deviceSerial" -ForegroundColor Green
Write-Host ""

# --------------------------------------------------
# 6. Install and launch
# --------------------------------------------------

Write-Host "[3/3] Installing and launching application..." -ForegroundColor Yellow
Write-Host ""

& android run `
    "--device=$deviceSerial" `
    "--apks=$($apk.FullName)" `
    "--use-delta-install"

$runExitCode = $LASTEXITCODE

if ($runExitCode -ne 0) {
    Write-Host ""
    Write-Host "============================================" -ForegroundColor Red
    Write-Host " DEPLOYMENT FAILED" -ForegroundColor Red
    Write-Host "============================================" -ForegroundColor Red
    Write-Host ""

    exit $runExitCode
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host " DEPLOYMENT SUCCESSFUL" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "Build  : OK"
Write-Host "Install: OK"
Write-Host "Launch : OK"
Write-Host "Device : $deviceSerial"
Write-Host ""
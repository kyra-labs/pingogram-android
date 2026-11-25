# Records the measure-first sections while you use the app, on a phone connected with adb (docs/PERFORMANCE.md §9,
# "Measure first"): the PowerShell twin of measure-first.sh, for Windows.
#
#   powershell -ExecutionPolicy Bypass -File tools\perfetto\measure-first.ps1 [-Out <directory>] [-Package <id>]
#       [-Seconds <n>]
#
# Install a profileable build first and sign in (.\gradlew.bat :app:installFossBenchmarkRelease), and open the app,
# the phone unlocked. While one Perfetto trace records (tools\perfetto\measure-first.pbtxt) for -Seconds (default
# 120), do on the phone what the script prints.
#
# It writes measure-first.perfetto-trace and device.txt into the output directory (default: .\measure-first-<date>),
# and summary.txt where Perfetto's Python package is installed (py -m pip install perfetto; summarize.py with
# measure-first.sql).
param(
    [string]$Out = "measure-first-$(Get-Date -Format 'yyyyMMdd-HHmmss')",
    [string]$Package = "dev.kyralabs.pingogram",
    [int]$Seconds = 120
)
$ErrorActionPreference = "Stop"

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$deviceTrace = "/data/misc/perfetto-traces/pingogram-measure-first.perfetto-trace"
$deviceConfig = "/data/local/tmp/pingogram-measure-first.pbtxt"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw "adb is not on the PATH." }
adb get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "No device: connect the phone with USB debugging on." }
adb shell pidof $Package | Out-Null
if ($LASTEXITCODE -ne 0) { throw "$Package is not running: open it first." }
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$packageInfo = adb shell dumpsys package $Package
@(
    (adb shell getprop ro.product.model),
    (adb shell getprop ro.build.version.release),
    (adb shell getprop ro.soc.model),
    ($packageInfo | Select-String -Pattern "versionName" | Select-Object -First 1),
    ($packageInfo | Select-String -Pattern "PROFILEABLE[A-Z_]*|DEBUGGABLE" | Select-Object -First 1),
    # On battery saver the cores run slower, and every section with them.
    "battery saver: $(adb shell settings get global low_power)"
) | Set-Content -Path (Join-Path $Out "device.txt")

$config = (Get-Content -Raw -Path (Join-Path $here "measure-first.pbtxt")) `
    -replace "dev\.kyralabs\.pingogram", $Package `
    -replace "(?m)^duration_ms: .*$", "duration_ms: $($Seconds * 1000)"
$localConfig = Join-Path (Resolve-Path -LiteralPath $Out).Path "measure-first.pbtxt"
# Unix line ends: Perfetto reads the file on the phone.
[System.IO.File]::WriteAllText($localConfig, $config.Replace("`r`n", "`n"))
adb push $localConfig $deviceConfig | Out-Null
# In the background on the phone, so it records while you use the app; it stops after duration_ms.
adb shell "cat $deviceConfig | perfetto --background --txt -c - -o $deviceTrace" | Out-Null

Write-Host "Recording for $Seconds s. On the phone, in this order (skip what the account has not set up):"
Write-Host "  1. Passcode: with Settings, Privacy and security, Passcode lock on and Auto-lock `"Immediately`", switch to another"
Write-Host "     app and back and enter the passcode; 3 times."
Write-Host "  2. Transcription: in a chat, tap `"Transcribe voice message`" on a voice message of 30 s or longer."
Write-Host "  3. QR, camera: Contacts, Scan; hold a QR code in front of the camera for 10 s."
Write-Host "  4. QR, image: on the same page, `"Scan from image`", and pick an image with a QR code."

do {
    Start-Sleep -Seconds 2
    adb shell pidof perfetto | Out-Null
} while ($LASTEXITCODE -eq 0)
$trace = Join-Path $Out "measure-first.perfetto-trace"
adb pull $deviceTrace $trace | Out-Null
adb shell rm -f $deviceTrace $deviceConfig | Out-Null
$summary = Join-Path $Out "summary.txt"
$python = Get-Command py -ErrorAction SilentlyContinue
if (-not $python) { $python = Get-Command python -ErrorAction SilentlyContinue }
$hasPerfetto = $false
if ($python) {
    & $python.Source -c "import perfetto.trace_processor" 2>$null
    $hasPerfetto = $LASTEXITCODE -eq 0
}
if ($hasPerfetto) {
    & $python.Source (Join-Path $here "summarize.py") $trace (Join-Path $here "measure-first.sql") |
        Set-Content -Encoding utf8 -Path $summary
    Write-Host "Done: $Out (send device.txt and summary.txt)."
} else {
    Write-Host "Done: $Out. To summarize the trace: py -m pip install perfetto, then"
    Write-Host "  py tools\perfetto\summarize.py $trace tools\perfetto\measure-first.sql > $summary"
    Write-Host "and send device.txt and summary.txt."
}

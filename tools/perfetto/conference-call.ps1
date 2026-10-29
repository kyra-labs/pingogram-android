# Records 3 minutes of a conference call on a phone connected with adb (docs/PERFORMANCE.md §9, "Conference calls"):
# the PowerShell twin of conference-call.sh, for Windows.
#
#   powershell -ExecutionPolicy Bypass -File tools\perfetto\conference-call.ps1 [-Out <directory>] [-Package <id>]
#
# Join the conference call first (a t.me/call link, three others speaking, one with the camera on) and keep its screen
# in front, then run this. It records a Perfetto trace with tools\perfetto\conference-call.pbtxt and writes
# conference-call.perfetto-trace and device.txt into the output directory (default: .\conference-call-<date>), and
# summary.txt where Perfetto's Python package is installed (py -m pip install perfetto; summarize.py).
param(
    [string]$Out = "conference-call-$(Get-Date -Format 'yyyyMMdd-HHmmss')",
    [string]$Package = "dev.kyralabs.pingogram"
)
$ErrorActionPreference = "Stop"

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$deviceTrace = "/data/misc/perfetto-traces/pingogram-conference-call.perfetto-trace"
$deviceConfig = "/data/local/tmp/pingogram-conference-call.pbtxt"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw "adb is not on the PATH." }
adb get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "No device: connect the phone with USB debugging on." }
adb shell pidof $Package | Out-Null
if ($LASTEXITCODE -ne 0) { throw "$Package is not running: join the conference call first." }
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$packageInfo = adb shell dumpsys package $Package
@(
    (adb shell getprop ro.product.model),
    (adb shell getprop ro.build.version.release),
    ($packageInfo | Select-String -Pattern "versionName" | Select-Object -First 1),
    ($packageInfo | Select-String -Pattern "PROFILEABLE[A-Z_]*|DEBUGGABLE" | Select-Object -First 1)
) | Set-Content -Path (Join-Path $Out "device.txt")

Write-Host "Recording for 3 minutes. Keep the call going with its screen in front."
$config = (Get-Content -Raw -Path (Join-Path $here "conference-call.pbtxt")) -replace "dev\.kyralabs\.pingogram", $Package
$localConfig = Join-Path (Resolve-Path -LiteralPath $Out).Path "conference-call.pbtxt"
# Unix line ends: Perfetto reads the file on the phone.
[System.IO.File]::WriteAllText($localConfig, $config.Replace("`r`n", "`n"))
adb push $localConfig $deviceConfig | Out-Null
adb shell "cat $deviceConfig | perfetto --txt -c - -o $deviceTrace" | Out-Null
$trace = Join-Path $Out "conference-call.perfetto-trace"
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
    & $python.Source (Join-Path $here "summarize.py") $trace (Join-Path $here "conference-call.sql") |
        Set-Content -Encoding utf8 -Path $summary
    Write-Host "Done: $Out (send device.txt and summary.txt)."
} else {
    Write-Host "Done: $Out. To summarize the trace: py -m pip install perfetto, then"
    Write-Host "  py tools\perfetto\summarize.py $trace tools\perfetto\conference-call.sql > $summary"
    Write-Host "and send device.txt and summary.txt."
}

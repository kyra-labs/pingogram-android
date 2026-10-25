# Records Pingogram's cold and warm starts on a phone connected with adb (docs/PERFORMANCE.md §2, §9): the PowerShell
# twin of startup.sh, for Windows.
#
#   powershell -ExecutionPolicy Bypass -File tools\perfetto\startup.ps1 [-Out <directory>] [-Package <id>] [-Starts <n>]
#
# Install a profileable build first and sign in (.\gradlew.bat :app:installFossBenchmarkRelease), then run this with
# the phone unlocked and the screen on. It records one Perfetto trace (tools\perfetto\startup.pbtxt) while it starts the
# app -Starts times cold (the process stopped first) and as many times warm (the process kept, the activity created
# again), and saves what `am start -W` reports for each start. Warm starts use Android's developer option "Don't keep
# activities" for their duration, so leaving the app destroys its activity as Macrobenchmark's warm starts do; the option
# is put back as it was, also when the script stops early.
#
# It writes startup.perfetto-trace, starts.txt and device.txt into the output directory (default: .\startup-<date>),
# and summary.txt where Perfetto's Python package is installed (py -m pip install perfetto; summarize.py). Open the trace
# in https://ui.perfetto.dev (read in the browser, not uploaded), or query it with tools\perfetto\startup.sql.
param(
    [string]$Out = "startup-$(Get-Date -Format 'yyyyMMdd-HHmmss')",
    [string]$Package = "dev.kyralabs.pingogram",
    [int]$Starts = 5
)
$ErrorActionPreference = "Stop"

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$deviceTrace = "/data/misc/perfetto-traces/pingogram-startup.perfetto-trace"
$deviceConfig = "/data/local/tmp/pingogram-startup.pbtxt"
$activity = "$Package/dev.kyralabs.pingogram.MainActivity"
# Each start gets this long to finish drawing and settle before the next one.
$settle = 6

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw "adb is not on the PATH." }
adb get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "No device: connect the phone with USB debugging on." }
adb shell pm path $Package | Out-Null
if ($LASTEXITCODE -ne 0) { throw "$Package is not installed." }
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$packageInfo = adb shell dumpsys package $Package
@(
    (adb shell getprop ro.product.model),
    (adb shell getprop ro.build.version.release),
    ($packageInfo | Select-String -Pattern "versionName" | Select-Object -First 1),
    ($packageInfo | Select-String -Pattern "PROFILEABLE[A-Z_]*|DEBUGGABLE" | Select-Object -First 1)
) | Set-Content -Path (Join-Path $Out "device.txt")

$keepActivities = (adb shell settings get global always_finish_activities).Trim()
if ($keepActivities -eq "null") { $keepActivities = "0" }

function Invoke-Home {
    adb shell input keyevent KEYCODE_HOME | Out-Null
    Start-Sleep -Seconds 2
}

function Invoke-Start([string]$label) {
    Add-Content -Path (Join-Path $Out "starts.txt") -Value "=== $label ==="
    adb shell am start-activity -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $activity |
        Add-Content -Path (Join-Path $Out "starts.txt")
    Start-Sleep -Seconds $settle
}

try {
    $durationMs = ($Starts * 2 * ($settle + 4) + 15) * 1000
    Write-Host "Recording $Starts cold and $Starts warm starts (about $($durationMs / 1000) s). Leave the phone alone."
    $config = (Get-Content -Raw -Path (Join-Path $here "startup.pbtxt")) `
        -replace "dev\.kyralabs\.pingogram", $Package `
        -replace "(?m)^duration_ms: .*$", "duration_ms: $durationMs"
    $localConfig = Join-Path (Resolve-Path -LiteralPath $Out).Path "startup.pbtxt"
    # Unix line ends: Perfetto reads the file on the phone.
    [System.IO.File]::WriteAllText($localConfig, $config.Replace("`r`n", "`n"))
    adb push $localConfig $deviceConfig | Out-Null
    # In the background on the phone, so it records while this script starts the app; it stops after duration_ms.
    adb shell "cat $deviceConfig | perfetto --background --txt -c - -o $deviceTrace" | Out-Null
    Start-Sleep -Seconds 3

    Set-Content -Path (Join-Path $Out "starts.txt") -Value ""
    for ($i = 1; $i -le $Starts; $i++) {
        Invoke-Home
        adb shell am force-stop $Package | Out-Null
        Start-Sleep -Seconds 1
        Invoke-Start "cold $i"
    }

    adb shell settings put global always_finish_activities 1 | Out-Null
    for ($i = 1; $i -le $Starts; $i++) {
        # Leaving the app destroys its activity ("Don't keep activities"); the process stays.
        Invoke-Home
        Invoke-Start "warm $i"
    }
} finally {
    adb shell settings put global always_finish_activities $keepActivities | Out-Null
}
Invoke-Home

Write-Host "Waiting for the trace to end."
do {
    Start-Sleep -Seconds 2
    adb shell pidof perfetto | Out-Null
} while ($LASTEXITCODE -eq 0)
adb pull $deviceTrace (Join-Path $Out "startup.perfetto-trace") | Out-Null
adb shell rm -f $deviceTrace $deviceConfig | Out-Null
# The trace is hundreds of MB; its summary is a few KB of numbers, which is what to send.
$trace = Join-Path $Out "startup.perfetto-trace"
$summary = Join-Path $Out "summary.txt"
$python = Get-Command py -ErrorAction SilentlyContinue
if (-not $python) { $python = Get-Command python -ErrorAction SilentlyContinue }
$hasPerfetto = $false
if ($python) {
    & $python.Source -c "import perfetto.trace_processor" 2>$null
    $hasPerfetto = $LASTEXITCODE -eq 0
}
if ($hasPerfetto) {
    & $python.Source (Join-Path $here "summarize.py") $trace (Join-Path $here "startup.sql") |
        Set-Content -Encoding utf8 -Path $summary
    Write-Host "Done: $Out (send device.txt, starts.txt and summary.txt)."
} else {
    Write-Host "Done: $Out. To summarize the trace: py -m pip install perfetto, then"
    Write-Host "  py tools\perfetto\summarize.py $trace > $summary"
    Write-Host "and send device.txt, starts.txt and summary.txt."
}

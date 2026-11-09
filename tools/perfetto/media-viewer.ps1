# Records opening and closing a photo in the media viewer on a phone connected with adb (docs/PERFORMANCE.md §9,
# "Opening and closing media"): the PowerShell twin of media-viewer.sh, for Windows.
#
#   powershell -ExecutionPolicy Bypass -File tools\perfetto\media-viewer.ps1 [-Out <directory>] [-Package <id>]
#       [-Openings <n>] [-TapInsetDp <dp>]
#
# Install a profileable build first and sign in (.\gradlew.bat :app:installFossBenchmarkRelease), open a conversation
# with a photo among its newest messages (Saved Messages, a photo without a caption, sent by yourself) and keep it on
# screen, the phone unlocked. The script finds the newest photo's bubble on screen (its description names a photo),
# then, while one Perfetto trace records (tools\perfetto\media-viewer.pbtxt), taps it near its end (an outgoing
# bubble's photo; pass a larger -TapInsetDp for a wide bubble), waits for the viewer, presses back and waits again,
# -Openings times (default 10).
#
# It writes media-viewer.perfetto-trace and device.txt into the output directory (default: .\media-viewer-<date>),
# and summary.txt where Perfetto's Python package is installed (py -m pip install perfetto; summarize.py with
# media-viewer.sql). Open the trace in https://ui.perfetto.dev (read in the browser, not uploaded) to look at the
# frames inside the overlayEnter and overlayExit sections.
param(
    [string]$Out = "media-viewer-$(Get-Date -Format 'yyyyMMdd-HHmmss')",
    [string]$Package = "dev.kyralabs.pingogram",
    [int]$Openings = 10,
    [int]$TapInsetDp = 48
)
$ErrorActionPreference = "Stop"

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$deviceTrace = "/data/misc/perfetto-traces/pingogram-media-viewer.perfetto-trace"
$deviceConfig = "/data/local/tmp/pingogram-media-viewer.pbtxt"
$deviceDump = "/data/local/tmp/pingogram-ui.xml"
# Each opening and each closing gets this long to finish its transition and settle.
$settle = 2

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw "adb is not on the PATH." }
adb get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "No device: connect the phone with USB debugging on." }
adb shell pidof $Package | Out-Null
if ($LASTEXITCODE -ne 0) { throw "$Package is not running: open a conversation with a photo first." }
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$packageInfo = adb shell dumpsys package $Package
@(
    (adb shell getprop ro.product.model),
    (adb shell getprop ro.build.version.release),
    ($packageInfo | Select-String -Pattern "versionName" | Select-Object -First 1),
    ($packageInfo | Select-String -Pattern "PROFILEABLE[A-Z_]*|DEBUGGABLE" | Select-Object -First 1),
    (adb shell wm size),
    (adb shell wm density),
    # The display's refresh rate the phone chose: it changes frame times.
    "refresh rate: $((adb shell dumpsys display | Select-String -Pattern 'mRefreshRate=[0-9.]+|fps=[0-9.]+' | Select-Object -First 1).Matches.Value)",
    # Accessibility services make Compose work out its semantics in every frame: which ones run, by package name.
    "accessibility services: $(adb shell settings get secure enabled_accessibility_services)",
    "animator duration scale: $(adb shell settings get global animator_duration_scale)"
) | Set-Content -Path (Join-Path $Out "device.txt")

# The newest photo's bubble on screen: the lowest node whose description names a photo ("Ada, 9:41 AM: Photo…"), from
# a dump of the window's accessibility tree, as UiAutomator sees it.
adb shell uiautomator dump $deviceDump | Out-Null
$dump = Join-Path $Out "ui.xml"
adb pull $deviceDump $dump | Out-Null
adb shell rm -f $deviceDump | Out-Null
$nodes = [regex]::Matches((Get-Content -Raw -Path $dump), '<node[^>]*content-desc="[^"]*: Photo[^"]*"[^>]*>')
Remove-Item $dump
$photo = $nodes | ForEach-Object {
    $m = [regex]::Match($_.Value, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if ($m.Success) { [pscustomobject]@{ Left = [int]$m.Groups[1].Value; Top = [int]$m.Groups[2].Value; Right = [int]$m.Groups[3].Value; Bottom = [int]$m.Groups[4].Value } }
} | Sort-Object Top | Select-Object -Last 1
if (-not $photo) { throw "No photo bubble on screen (the window has no node described as a photo)." }
$density = [int]([regex]::Match((adb shell wm density), '\d+').Value)
$inset = [int]($TapInsetDp * $density / 160)
$tapX = $photo.Right - $inset
$tapY = $photo.Top + [int](($photo.Bottom - $photo.Top) / 3)
Write-Host "Photo bubble at [$($photo.Left),$($photo.Top)][$($photo.Right),$($photo.Bottom)]; tapping ($tapX, $tapY)."

$durationMs = ($Openings * 2 * ($settle + 1) + 10) * 1000
Write-Host "Recording $Openings openings and closings (about $($durationMs / 1000) s). Leave the phone alone."
$config = (Get-Content -Raw -Path (Join-Path $here "media-viewer.pbtxt")) `
    -replace "dev\.kyralabs\.pingogram", $Package `
    -replace "(?m)^duration_ms: .*$", "duration_ms: $durationMs"
$localConfig = Join-Path (Resolve-Path -LiteralPath $Out).Path "media-viewer.pbtxt"
# Unix line ends: Perfetto reads the file on the phone.
[System.IO.File]::WriteAllText($localConfig, $config.Replace("`r`n", "`n"))
adb push $localConfig $deviceConfig | Out-Null
# In the background on the phone, so it records while this script drives the app; it stops after duration_ms.
adb shell "cat $deviceConfig | perfetto --background --txt -c - -o $deviceTrace" | Out-Null
Start-Sleep -Seconds 3

for ($i = 1; $i -le $Openings; $i++) {
    adb shell input tap $tapX $tapY | Out-Null
    Start-Sleep -Seconds $settle
    adb shell input keyevent KEYCODE_BACK | Out-Null
    Start-Sleep -Seconds $settle
}

Write-Host "Waiting for the trace to end."
do {
    Start-Sleep -Seconds 2
    adb shell pidof perfetto | Out-Null
} while ($LASTEXITCODE -eq 0)
$trace = Join-Path $Out "media-viewer.perfetto-trace"
adb pull $deviceTrace $trace | Out-Null
adb shell rm -f $deviceTrace $deviceConfig | Out-Null
# The trace is tens of MB; its summary is a few KB of numbers, which is what to send.
$summary = Join-Path $Out "summary.txt"
$python = Get-Command py -ErrorAction SilentlyContinue
if (-not $python) { $python = Get-Command python -ErrorAction SilentlyContinue }
$hasPerfetto = $false
if ($python) {
    & $python.Source -c "import perfetto.trace_processor" 2>$null
    $hasPerfetto = $LASTEXITCODE -eq 0
}
if ($hasPerfetto) {
    & $python.Source (Join-Path $here "summarize.py") $trace (Join-Path $here "media-viewer.sql") |
        Set-Content -Encoding utf8 -Path $summary
    Write-Host "Done: $Out (send device.txt and summary.txt)."
} else {
    Write-Host "Done: $Out. To summarize the trace: py -m pip install perfetto, then"
    Write-Host "  py tools\perfetto\summarize.py $trace tools\perfetto\media-viewer.sql > $summary"
    Write-Host "and send device.txt and summary.txt."
}

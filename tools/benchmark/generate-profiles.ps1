# Regenerates the Baseline and Startup Profiles from scratch (docs/PERFORMANCE.md §2, §9): the PowerShell twin of
# generate-profiles.sh, for Windows.
#
#   powershell -ExecutionPolicy Bypass -File tools\benchmark\generate-profiles.ps1 [extra Gradle arguments]
#
# Quote each -P argument, or Windows PowerShell splits it at its dots. The full command (one line):
#   powershell -ExecutionPolicy Bypass -File tools\benchmark\generate-profiles.ps1
#     "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
#     "-Pandroid.testInstrumentationRunnerArguments.class=dev.kyralabs.pingogram.benchmark.BaselineProfileGenerator"
#
# Start from a clean install (docs/PERFORMANCE.md §2, §9): uninstall Pingogram, install nonMinifiedRelease and sign in
# again. During collection Android seeds the profile from the one it keeps next to the installed app, from earlier
# installs and runs, and only an uninstall removes it; without that, every regeneration carries the last profile
# forward and the Startup Profile holds everything the Baseline Profile does. This deletes the committed
# profiles and the plugin's per-test files, runs `.\gradlew.bat :app:generateBaselineProfile` with the arguments given
# (for instance -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true, §9), and restores the committed
# profiles with git if the run fails. Run it with the phone connected and Pingogram signed in.
$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "..\..")
Set-Location $root
$profiles = "app\src\main\generated\baselineProfiles"

Remove-Item -Path "$profiles\*.txt" -Force -ErrorAction SilentlyContinue
foreach ($dir in @(
        "benchmark\build\intermediates\baselineprofiles",
        "benchmark\build\outputs\connected_android_test_additional_output"
    )) {
    if (Test-Path $dir) { Remove-Item -Path $dir -Recurse -Force }
}

& .\gradlew.bat :app:generateBaselineProfile @args
if ($LASTEXITCODE -ne 0) {
    Write-Error "Generation failed: restoring the committed profiles." -ErrorAction Continue
    git restore --source=HEAD --staged --worktree -- $profiles
    exit 1
}

# The Startup Profile should now be much smaller than the Baseline Profile.
foreach ($name in @("baseline-prof.txt", "startup-prof.txt")) {
    $lines = (Get-Content "$profiles\$name" | Measure-Object -Line).Lines
    Write-Output "$lines $profiles\$name"
}

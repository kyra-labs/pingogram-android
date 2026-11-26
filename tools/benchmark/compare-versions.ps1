# Measures the start of an older and a newer Pingogram on the account and database already on the phone
# (docs/PERFORMANCE.md §9), to tell the code's share of a slower start from the database's growth.
#
#   powershell -ExecutionPolicy Bypass -File tools\benchmark\compare-versions.ps1 [-Old v2.3.0] [-New v2.5.0]
#
# Each version is built from its tag in a git worktree beside the repository and runs its own StartupBenchmark, in
# the order New, Old, New: the second run of New shows how much the phone itself drifted (heat, background work)
# while Old ran. Builds without -Pversion are all version 0.0.0-dev (versionCode 1) and signed with this computer's
# debug key, so each benchmarkRelease installs over the one before as a replacement: the session and TDLib's database
# stay, and every version starts on today's account and database. Run it with the phone connected and a
# benchmarkRelease or nonMinifiedRelease of Pingogram signed in (not a release from GitHub or a store, which is signed
# with another key), and with secrets.properties in the repository, which each worktree gets a copy of.
#
# Nothing of the account leaves the phone: the only files kept are the benchmarks' *-benchmarkData.json (times, the
# device's model and the benchmark names), in benchmark\build\versions. The worktrees, with the traces Gradle pulled
# into them, are deleted at the end.
param(
    [string]$Old = "v2.3.0",
    [string]$New = "v2.5.0"
)
$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "..\..")
Set-Location $root
if (-not (Test-Path "secrets.properties")) {
    throw "secrets.properties is missing: the builds need it (docs/OWNER_SETUP.md)."
}
$installed = adb shell pm list packages dev.kyralabs.pingogram
if (-not ($installed | Select-String -Pattern '^package:dev\.kyralabs\.pingogram$')) {
    throw "Pingogram is not installed: .\gradlew.bat :app:installFossBenchmarkRelease, then sign in on the phone."
}

foreach ($tag in @($Old, $New)) {
    git rev-parse --verify --quiet "$tag^{commit}" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "No tag $tag here: git fetch --tags origin" }
}

$out = Join-Path $root "benchmark\build\versions"
if (Test-Path $out) { Remove-Item -Path $out -Recurse -Force }
New-Item -ItemType Directory -Path $out | Out-Null

$worktrees = @{}
$runs = @($New, $Old, $New)
try {
    for ($i = 0; $i -lt $runs.Count; $i++) {
        $tag = $runs[$i]
        if (-not $worktrees.ContainsKey($tag)) {
            # Beside the repository, at its depth: the build's paths stay as short as the repository's own.
            $tree = Join-Path (Split-Path -Parent $root) "pingogram-$tag"
            if (Test-Path $tree) {
                git worktree remove --force $tree
                if (Test-Path $tree) { Remove-Item -Path $tree -Recurse -Force }
            }
            git worktree add --detach $tree $tag
            if ($LASTEXITCODE -ne 0) { throw "No worktree for $tag." }
            Copy-Item "secrets.properties" $tree
            if (Test-Path "local.properties") { Copy-Item "local.properties" $tree }
            $worktrees[$tag] = $tree
        }
        $tree = $worktrees[$tag]
        $results = Join-Path $tree "benchmark\build\outputs\connected_android_test_additional_output"
        if (Test-Path $results) { Remove-Item -Path $results -Recurse -Force }

        Write-Output "`n== Run $($i + 1) of $($runs.Count): $tag =="
        Push-Location $tree
        try {
            # Installs this version over the one on the phone, keeping its data, and leaves it there.
            & .\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest `
                "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true" `
                "-Pandroid.testInstrumentationRunnerArguments.class=dev.kyralabs.pingogram.benchmark.StartupBenchmark"
            if ($LASTEXITCODE -ne 0) { throw "StartupBenchmark failed on $tag." }
        } finally {
            Pop-Location
        }
        $dest = Join-Path $out "$($i + 1)-$tag"
        New-Item -ItemType Directory -Path $dest | Out-Null
        Get-ChildItem -Path $results -Recurse -Filter "*benchmarkData.json" | Copy-Item -Destination $dest
    }
} finally {
    foreach ($tree in $worktrees.Values) { git worktree remove --force $tree }
    git worktree prune
}

# Old's run as the baseline, then each run of New against it. A budget miss sets the exit code, not a failure here.
$report = Join-Path $out "comparison.txt"
& py tools\benchmark\compare.py (Join-Path $out "2-$Old") --write-baseline (Join-Path $out "$Old.json") --commit $Old |
    Tee-Object -FilePath $report
foreach ($run in @("1-$New", "3-$New")) {
    Write-Output "`n== $run against $Old ==" | Tee-Object -FilePath $report -Append
    & py tools\benchmark\compare.py (Join-Path $out $run) --baseline (Join-Path $out "$Old.json") |
        Tee-Object -FilePath $report -Append
}
Write-Output "`nResults: $out (send back comparison.txt and the *-benchmarkData.json files)."
Write-Output "The phone now runs $New."
Write-Output ".\gradlew.bat :app:installFossBenchmarkRelease puts main back, keeping the session."

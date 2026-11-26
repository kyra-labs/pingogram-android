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
# into them, are deleted at the end; the script fails if one cannot be.
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
# The user the phone shows now: pm asked about every user fails where another profile (Samsung's Secure Folder or Dual
# Messenger, a work profile) is one the shell may not read ("Shell does not have permission to access user 150").
$user = "$(adb shell am get-current-user)".Trim()
if ($user -notmatch '^\d+$') { $user = "0" }
$installed = adb shell pm list packages --user $user dev.kyralabs.pingogram
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

# Removes the worktree at [tree] and says whether it is gone. Gradle's outputs in it go deeper than Windows' 260
# characters, which git deletes only with core.longpaths, and Gradle's daemons keep files in it open until they stop.
function Remove-Worktree([string]$tree) {
    if (Test-Path -LiteralPath (Join-Path $tree "gradlew.bat")) {
        Push-Location $tree
        try { & .\gradlew.bat --stop | Out-Null } finally { Pop-Location }
    }
    git -c core.longpaths=true worktree remove --force $tree | Out-Host
    if (Test-Path -LiteralPath $tree) {
        # What git left, through Windows' extended-length paths.
        Remove-Item -LiteralPath "\\?\$tree" -Recurse -Force -ErrorAction SilentlyContinue
    }
    git worktree prune | Out-Host
    return -not (Test-Path -LiteralPath $tree)
}

$worktrees = @{}
$left = @()
$runs = @($New, $Old, $New)
try {
    for ($i = 0; $i -lt $runs.Count; $i++) {
        $tag = $runs[$i]
        if (-not $worktrees.ContainsKey($tag)) {
            # Beside the repository, at its depth: the build's paths stay as short as the repository's own.
            $tree = Join-Path (Split-Path -Parent $root) "pingogram-$tag"
            if ((Test-Path -LiteralPath $tree) -and -not (Remove-Worktree $tree)) {
                throw "$tree is left from an earlier run and could not be removed: delete it, then git worktree prune."
            }
            git -c core.longpaths=true worktree add --detach $tree $tag
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
    $left = @($worktrees.Values | Where-Object { -not (Remove-Worktree $_) })
    if ($left.Count -gt 0) {
        Write-Warning "Not removed: $($left -join ', '). Delete them by hand, then run git worktree prune."
    }
}
if ($left.Count -gt 0) { throw "Worktrees left behind: $($left -join ', ')." }

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

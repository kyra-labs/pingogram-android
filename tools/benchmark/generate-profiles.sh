#!/usr/bin/env bash
# Regenerates the Baseline and Startup Profiles from scratch (docs/PERFORMANCE.md §2, §9).
#
#   tools/benchmark/generate-profiles.sh [extra Gradle arguments]
#
# The profiles must be collected from an APK that embeds none: during collection the repeated speed-profile compiles
# fold the APK's embedded profile into what `pm dump-profiles` returns, so every regeneration would inherit the last
# committed profile, and the Startup Profile would hold everything the Baseline Profile does. This deletes the committed
# profiles and the plugin's per-test files, runs `./gradlew :app:generateBaselineProfile` with the arguments given (for
# instance -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true, §9), and restores the committed profiles
# with git if the run fails. Run it from anywhere in the repository, with the phone connected and Pingogram signed in.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"
profiles=app/src/main/generated/baselineProfiles

restore() {
  echo "Generation failed: restoring the committed profiles." >&2
  git restore --source=HEAD --staged --worktree -- "$profiles"
}

rm -f "$profiles"/*.txt
rm -rf benchmark/build/intermediates/baselineprofiles benchmark/build/outputs/connected_android_test_additional_output

if ! ./gradlew :app:generateBaselineProfile "$@"; then
  restore
  exit 1
fi

# The Startup Profile should now be much smaller than the Baseline Profile.
wc -l "$profiles"/baseline-prof.txt "$profiles"/startup-prof.txt

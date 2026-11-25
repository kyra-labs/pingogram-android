#!/usr/bin/env bash
# Regenerates the Baseline and Startup Profiles from scratch (docs/PERFORMANCE.md §2, §9).
#
#   tools/benchmark/generate-profiles.sh [extra Gradle arguments]
#
# Start from a clean install (docs/PERFORMANCE.md §2, §9): uninstall Pingogram, install nonMinifiedRelease and sign in
# again. During collection Android seeds the profile from the one it keeps next to the installed app, from earlier
# installs and runs, and only an uninstall removes it; without that, every regeneration carries the last profile
# forward and the Startup Profile holds everything the Baseline Profile does. This deletes the committed
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

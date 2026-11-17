#!/usr/bin/env bash
# PreToolUse hook for Edit and Write: blocks edits to translated resources, which come from
# Weblate (docs/TRANSLATING.md), and to keystore files. Exit 2 blocks the edit.
set -uo pipefail

input=$(cat)
path=$(printf '%s' "$input" | jq -r '.tool_input.file_path // .tool_input.notebook_path // empty' 2>/dev/null)
[ -z "$path" ] && exit 0

if printf '%s' "$path" | grep -Eq '/res/values-([a-z]{2,3}(-r[A-Z]{2})?|b\+[A-Za-z0-9+]+)(/|$)'; then
  echo "Blocked by .claude/hooks/protect-paths.sh: $path is a translation. Translations come from Weblate (docs/TRANSLATING.md); edit the English strings in res/values/ instead." >&2
  exit 2
fi
if printf '%s' "$path" | grep -Eq '(\.jks|\.keystore|(^|/)keystore\.properties)$'; then
  echo "Blocked by .claude/hooks/protect-paths.sh: $path is a keystore or signing file. Signing is the owner's (docs/RELEASE.md)." >&2
  exit 2
fi
exit 0

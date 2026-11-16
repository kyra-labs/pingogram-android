#!/usr/bin/env bash
# PreToolUse hook for Bash: checks a `git push` against the git rules of CLAUDE.md
# ("Git workflow"). Exit 2 blocks the command and shows the message to Claude.
set -uo pipefail

input=$(cat)
command=$(printf '%s' "$input" | jq -r '.tool_input.command // empty' 2>/dev/null)
[ -z "$command" ] && exit 0

# Only `git push` (optionally `git -C <dir> push`) is checked.
if ! printf '%s' "$command" |
  grep -Eq '(^|[^[:alnum:]_./-])git([[:space:]]+-C[[:space:]]+[^[:space:]]+)?[[:space:]]+push([[:space:]]|$)'; then
  exit 0
fi

problems=()
block() { problems+=("$1"); }

if printf '%s' "$command" | grep -Eq '(^|[[:space:]])(--force|--force-with-lease|-f)([[:space:]=]|$)'; then
  block "Force pushes are never allowed. Merge origin/main instead of rewriting history."
fi
if printf '%s' "$command" | grep -Eq -- '--no-verify'; then
  block "--no-verify is never allowed."
fi
if printf '%s' "$command" | grep -Eq '(^|[[:space:]:])(refs/heads/)?(main|master)([[:space:]]|$)'; then
  block "Never push to main. Push your own branch and open a pull request."
fi

dir=$(printf '%s' "$command" | sed -nE 's/.*git[[:space:]]+-C[[:space:]]+([^[:space:]]+).*/\1/p')
dir=${dir:-${CLAUDE_PROJECT_DIR:-.}}
branch=$(git -C "$dir" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")

if [ "$branch" = "main" ] || [ "$branch" = "master" ]; then
  block "You are on $branch. Start a branch from fresh origin/main: git switch -c <type>/<scope>-<description> origin/main"
elif [ "$branch" = "HEAD" ]; then
  block "HEAD is detached. Switch to a named branch before pushing."
elif [ -n "$branch" ]; then
  name_re='^((feat|fix|perf|refactor|test|docs|build|ci|chore)/[a-z0-9]+(-[a-z0-9]+)+|hotfix/[0-9]+\.[0-9]+\.[0-9]+)$'
  if ! printf '%s' "$branch" | grep -Eq "$name_re" || [ "${#branch}" -gt 50 ]; then
    block "Branch '$branch' does not follow <type>/<scope>-<short-kebab-description> (lowercase, hyphens, at most 50 characters; types: feat fix perf refactor test docs build ci chore). Rename it: git branch -m <new-name>"
  fi
fi

base=""
for ref in origin/main main; do
  if git -C "$dir" rev-parse --verify --quiet "$ref" >/dev/null; then base=$ref; break; fi
done

if [ -n "$base" ] && [ -n "$branch" ] && [ "$branch" != "HEAD" ]; then
  subject_re='^(feat|fix|perf|refactor|test|docs|build|ci|chore)(\([a-z0-9-]+\))?!?: [a-z0-9`].*[^.]$'
  signoff='Signed-off-by: YamTee <thalha.moha@gmail.com>'
  ai_re='Generated (with|by) Claude|Co-Authored-By:.*(Claude|anthropic)|Claude-Session:|claude\.ai/code|@anthropic\.com|🤖'
  while IFS= read -r sha; do
    [ -z "$sha" ] && continue
    subject=$(git -C "$dir" log -1 --format=%s "$sha")
    body=$(git -C "$dir" log -1 --format=%B "$sha")
    author=$(git -C "$dir" log -1 --format='%an <%ae>' "$sha")
    short=${sha:0:9}
    if ! printf '%s' "$subject" | grep -Eq "$subject_re"; then
      block "Commit $short subject is not <type>(<scope>): <lowercase imperative subject without a period>: \"$subject\""
    fi
    if [ "${#subject}" -gt 72 ]; then
      block "Commit $short subject is ${#subject} characters; at most 72."
    fi
    if ! printf '%s\n' "$body" | grep -Fxq "$signoff"; then
      block "Commit $short has no sign-off line \"$signoff\". Set the identity (git config user.name/user.email) and commit with -s."
    fi
    if printf '%s\n%s\n' "$body" "$author" | grep -Eiq "$ai_re"; then
      block "Commit $short mentions an AI tool as author or attribution. Remove that line."
    fi
  done < <(git -C "$dir" rev-list --no-merges "$base..HEAD" 2>/dev/null)
fi

if [ "${#problems[@]}" -gt 0 ]; then
  {
    echo "Push blocked by .claude/hooks/check-git.sh (CLAUDE.md, \"Git workflow\"):"
    for p in "${problems[@]}"; do echo "- $p"; done
    echo "Fix these with new commits or a renamed branch; never rewrite pushed history."
  } >&2
  exit 2
fi
exit 0

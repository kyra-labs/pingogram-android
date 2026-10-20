#!/usr/bin/env bash
# Posts build files to the owner's channel with a bot of the Bot API (docs/RELEASE.md, "Channel uploads").
#
#   tools/ci/telegram-publish.sh "<caption>" <file>...
#
# Needs TELEGRAM_BOT_TOKEN and TELEGRAM_CHANNEL_ID (GitHub Actions secrets); without them it says so and exits 0, so
# forks and repositories without a channel build as before. The caption goes with the first file sent. A bot may send
# files of at most 50 MB: larger ones are not sent, and the caption lists them, so add a link to where they are
# (the GitHub Release, the CI run) in the caption. Exits 1 if Telegram refused a request; callers run it with
# continue-on-error, so a channel problem never fails a build or a release.
#
# The token is a secret: it is passed to curl in a config file on stdin, never on the command line (which other
# processes could read) or in the output. TELEGRAM_API_BASE (default https://api.telegram.org) exists for tests.
set -euo pipefail

caption="${1:?usage: telegram-publish.sh <caption> <file>...}"
shift

if [[ -z "${TELEGRAM_BOT_TOKEN:-}" || -z "${TELEGRAM_CHANNEL_ID:-}" ]]; then
  echo "Channel upload skipped: TELEGRAM_BOT_TOKEN / TELEGRAM_CHANNEL_ID are not set."
  exit 0
fi

api_base="${TELEGRAM_API_BASE:-https://api.telegram.org}"
# The Bot API's limit for files a bot uploads (https://core.telegram.org/bots/api#senddocument), with some room for
# the multipart encoding.
max_bytes=$((49 * 1024 * 1024))
# The Bot API's limit for a caption.
max_caption=1024

sendable=()
too_large=()
for file in "$@"; do
  [[ -f "$file" ]] || { echo "::warning::Not a file, not sent: $file"; continue; }
  if (( $(stat -c %s "$file") > max_bytes )); then
    too_large+=("$(basename "$file")")
  else
    sendable+=("$file")
  fi
done

note=""
if (( ${#too_large[@]} > 0 )); then
  note=$'\n\n'"Over the 50 MB a bot may send, not attached: ${too_large[*]}"
fi
# A long caption is cut, never the note.
caption="${caption:0:$((max_caption - ${#note}))}$note"

# Sends one Bot API request: $1 the method, then curl --form arguments. Prints nothing of the token.
call() {
  local method="$1"
  shift
  local response
  response="$(
    printf 'url = "%s/bot%s/%s"\n' "$api_base" "$TELEGRAM_BOT_TOKEN" "$method" |
      curl --silent --show-error --max-time 300 --retry 2 --config - "$@"
  )" || { echo "::warning::Telegram request $method failed to connect."; return 1; }
  if ! [[ "$response" =~ \"ok\"[[:space:]]*:[[:space:]]*true ]]; then
    # The response holds Telegram's error description, never the token.
    echo "::warning::Telegram refused $method: $response"
    return 1
  fi
}

status=0
if (( ${#sendable[@]} == 0 )); then
  call sendMessage --form-string "chat_id=$TELEGRAM_CHANNEL_ID" --form-string "text=$caption" || status=1
else
  first=1
  for file in "${sendable[@]}"; do
    args=(--form-string "chat_id=$TELEGRAM_CHANNEL_ID" --form "document=@$file")
    if (( first )); then
      args+=(--form-string "caption=$caption")
      first=0
    else
      # The rest of a set arrives without a sound of its own.
      args+=(--form-string "disable_notification=true")
    fi
    if call sendDocument "${args[@]}"; then
      echo "Sent $(basename "$file") to the channel."
    else
      status=1
    fi
  done
fi
exit "$status"

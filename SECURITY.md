# Security policy

## Supported versions

Only the latest release on the Releases page receives fixes.

## Reporting a vulnerability

Please **do not** open a public issue for security problems.

Use GitHub's private vulnerability reporting ("Security" tab → "Report a vulnerability") on
this repository, or email the maintainer at: `security@pingogram.app` *(owner: replace with a
real address before the first release)*.

Include steps to reproduce, affected version/ABI, device and Android version, and whether the
issue involves TDLib or Pingogram's own code. We aim to acknowledge within 72 hours.

## Scope notes

- Cryptography, MTProto and secret chats are implemented by TDLib. Issues there should also be
  reported to https://github.com/tdlib/td and, for protocol matters, to Telegram's
  bug bounty program.
- Pingogram never sends data anywhere except Telegram's data centers via TDLib. If you observe
  otherwise, that is a critical bug — please report it.

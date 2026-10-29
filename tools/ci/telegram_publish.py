#!/usr/bin/env python3
"""Posts builds to the owner's Telegram channels (docs/RELEASE.md, "Channel uploads").

A bot logs in over MTProto with Telethon, which lets it upload files of up to 2 GB; the Bot API refuses files over
50 MB, the universal APK among them.

    telegram_publish.py release --version 1.0.0 --dist dist --changelog CHANGELOG.md --repo owner/name
                                [--channel releases|debug] [--dry-run]
    telegram_publish.py debug --sha <commit> --subject <subject> --run-url <url> --apk-dir <dir> [--dry-run]

release: one announcement (the version, "Beta" for pre-releases, the date, the CHANGELOG section, a link to the
GitHub Release), which notifies, then each of the 8 APKs as a silent document whose caption gives its architecture,
build, size, SHA-256 (checked against SHA256SUMS.txt) and download link. debug: one plain message (the commit, its
subject, the run), then the foss debug APKs of every ABI, silent. Debug builds go only to the debug channel.

Secrets come from the environment and are never printed, put on a command line or written to disk; the session lives
in memory only (StringSession). TELEGRAM_BOT_TOKEN logs in; TELEGRAM_UPLOAD_API_ID and TELEGRAM_UPLOAD_API_HASH when
both are set, otherwise the app's TELEGRAM_API_ID and TELEGRAM_API_HASH, identify the uploader to Telegram.
TELEGRAM_CHANNEL_ID is the releases channel, TELEGRAM_DEBUG_CHANNEL_ID the debug one, each "@username" or a numeric
"-100…" ID. Without the token, the API credentials or the channel, it says so and exits 0, so forks build as before.

--dry-run prints every message and caption and sends nothing: it needs no secrets, no network and no Telethon.
Exits 1 when anything was not posted; the workflows run it with continue-on-error.
"""

import argparse
import asyncio
import datetime
import hashlib
import html
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path

TEXT_LIMIT = 4096
CAPTION_LIMIT = 1024
MAX_MESSAGES = 3
MEGABYTE = 1_048_576
FLOOD_WAIT_MAX_SECONDS = 300
PAUSE_SECONDS = 2

ARCHITECTURES = [
    ("universal", "every device"),
    ("arm64-v8a", "most phones"),
    ("armeabi-v7a", "older 32-bit phones"),
    ("x86_64", "emulators and Chromebooks"),
]
FLAVORS = [
    ("foss", "FOSS", "FOSS, no proprietary services"),
    ("gms", "GMS", "GMS, push notifications through Firebase Cloud Messaging"),
]

CHANNEL_SECRETS = {"releases": "TELEGRAM_CHANNEL_ID", "debug": "TELEGRAM_DEBUG_CHANNEL_ID"}


@dataclass
class Post:
    """One message: a text (file None) or a document with its caption."""

    text: str
    html: bool = True
    silent: bool = True
    file: Path | None = None
    file_name: str | None = None
    # Why the file is not posted (missing, wrong checksum); the post then fails without sending.
    error: str | None = None


# --- Text ---------------------------------------------------------------------------------------------------------


def visible_length(text):
    """What Telegram counts against its limits: the text without its HTML tags, in UTF-16 code units."""
    plain = html.unescape(re.sub(r"<[^>]*>", "", text))
    return len(plain.encode("utf-16-le")) // 2


def escape(text):
    return html.escape(text, quote=False)


_INLINE = re.compile(
    r"`(?P<code>[^`]+)`"
    r"|\[(?P<label>[^\]]+)\]\((?P<url>[^)\s]+)\)"
    r"|\*\*(?P<bold>.+?)\*\*"
    r"|(?<![\w*])\*(?P<italic>[^*\s](?:[^*]*[^*\s])?)\*(?![\w*])"
)


def inline(markdown):
    """One line of CHANGELOG Markdown as Telegram HTML: code, links, bold and italics; the rest escaped."""
    out = []
    position = 0
    for match in _INLINE.finditer(markdown):
        out.append(escape(markdown[position : match.start()]))
        if match.group("code") is not None:
            out.append(f"<code>{escape(match.group('code'))}</code>")
        elif match.group("url") is not None:
            out.append(f'<a href="{html.escape(match.group("url"), quote=True)}">{inline(match.group("label"))}</a>')
        elif match.group("bold") is not None:
            out.append(f"<b>{inline(match.group('bold'))}</b>")
        else:
            out.append(f"<i>{inline(match.group('italic'))}</i>")
        position = match.end()
    out.append(escape(markdown[position:]))
    return "".join(out)


def changelog_section(changelog, version):
    """The date and the lines of `## [version] - date` in a Keep a Changelog file; (None, []) without one."""
    lines = changelog.splitlines()
    heading = re.compile(r"^## \[" + re.escape(version) + r"\](?:\s*-\s*(\d{4}-\d{2}-\d{2}))?\s*$")
    for index, line in enumerate(lines):
        match = heading.match(line)
        if match:
            body = []
            for following in lines[index + 1 :]:
                if following.startswith("## ") or re.match(r"^\[[^\]]+\]:\s", following):
                    break
                body.append(following)
            date = datetime.date.fromisoformat(match.group(1)) if match.group(1) else None
            return date, body
    return None, []


def changelog_units(body):
    """The section as units that are never split: each top-level bullet with its nested ones, the section heading
    joined to its first bullet. Each unit is (separator before it, Telegram HTML, its section's heading or None)."""
    entries = []  # (kind, level, markdown): kind "heading", "bullet" or "text"
    for raw in body:
        line = raw.rstrip()
        if not line.strip():
            continue
        if line.startswith("#"):
            entries.append(["heading", 0, line.lstrip("#").strip()])
            continue
        bullet = re.match(r"^(\s*)[-*+] (.*)$", line)
        if bullet:
            entries.append(["bullet", 0 if len(bullet.group(1)) < 2 else 1, bullet.group(2).strip()])
        elif entries and entries[-1][0] != "heading" and raw[:1].isspace():
            entries[-1][2] += " " + line.strip()  # a continuation line
        else:
            entries.append(["text", 0, line.strip()])

    units = []
    pending_heading = None
    heading = None
    for kind, level, markdown in entries:
        if kind == "heading":
            if pending_heading is not None:
                units.append(("\n\n", pending_heading, None))
            pending_heading = heading = f"<b>{inline(markdown)}</b>"
            continue
        if kind == "bullet" and level == 1:
            rendered = f"    ◦ {inline(markdown)}"
        elif kind == "bullet":
            rendered = f"• {inline(markdown)}"
        else:
            rendered = inline(markdown)
        if level == 1 and units and pending_heading is None:
            separator, previous, section = units[-1]
            units[-1] = (separator, previous + "\n" + rendered, section)
        elif pending_heading is not None:
            units.append(("\n\n", pending_heading + "\n" + rendered, None))
            pending_heading = None
        else:
            units.append(("\n", rendered, heading))
    if pending_heading is not None:
        units.append(("\n\n", pending_heading, None))
    return units


def _shorten(unit, limit):
    """A unit too long for a message on its own, as plain text cut at a word before [limit]."""
    plain = html.unescape(re.sub(r"<[^>]*>", "", unit))
    while visible_length(escape(plain)) > limit - 1:
        plain = plain[: max(0, len(plain) - 64)].rsplit(" ", 1)[0]
    return escape(plain) + "…"


def _fill(header, units, limits):
    """Packs [units] after [header] into at most len(limits) messages; returns them and how many units fit. A message
    that starts within a section starts with that section's heading again."""
    messages = [header]
    placed = 0
    for separator, unit, section in units:
        opening = f"{section}\n{unit}" if section else unit
        if visible_length(messages[-1] + separator + unit) <= limits[len(messages) - 1]:
            messages[-1] += separator + unit
        elif len(messages) < len(limits) and visible_length(opening) <= limits[len(messages)]:
            messages.append(opening)
        else:
            break
        placed += 1
    return messages, placed


def split_messages(header, units, footer, more):
    """The announcement in at most MAX_MESSAGES messages of TEXT_LIMIT, split at bullet boundaries, [footer] at the
    end. When it does not fit, the last message ends at a bullet boundary with [more]."""
    units = [
        (separator, unit if visible_length(unit) <= TEXT_LIMIT - 200 else _shorten(unit, TEXT_LIMIT - 200), section)
        for separator, unit, section in units
    ]
    messages, placed = _fill(header, units, [TEXT_LIMIT] * MAX_MESSAGES)
    if placed == len(units):
        if visible_length(messages[-1] + "\n\n" + footer) <= TEXT_LIMIT:
            messages[-1] += "\n\n" + footer
            return messages
        if len(messages) < MAX_MESSAGES:
            return messages + [footer]
    limits = [TEXT_LIMIT] * (MAX_MESSAGES - 1) + [TEXT_LIMIT - visible_length("\n\n" + more)]
    messages, _ = _fill(header, units, limits)
    messages[-1] += "\n\n" + more
    return messages


def human_date(date):
    return f"{date:%B} {date.day}, {date.year}"


def is_prerelease(version):
    """As release.yml decides: anything before 1.0 and any version with a suffix."""
    return version.startswith("0.") or "-" in version


def announcement(version, changelog, release_url, today=None):
    date, body = changelog_section(changelog, version)
    lines = [f"<b>Pingogram {escape(version)}</b>"]
    if is_prerelease(version):
        lines.append("Beta")
    lines.append(human_date(date or today or datetime.date.today()))
    units = changelog_units(body) or [("\n\n", escape(f"No changelog section for {version}."), None)]
    link = f'<a href="{html.escape(release_url, quote=True)}">{escape(release_url)}</a>'
    footer = f"Release on GitHub: {link}"
    more = f"…and more: {link}"
    return split_messages("\n".join(lines), units, footer, more)


# --- Files --------------------------------------------------------------------------------------------------------


def asset_name(version, architecture, flavor):
    """The file's name on the GitHub Release, which Obtainium, SHA256SUMS.txt, the README and the website rely on."""
    return f"pingogram-{version}-{architecture}{'' if flavor == 'foss' else '-' + flavor}.apk"


def telegram_name(version, architecture, flavor):
    return f"pingogram-{version}-{architecture}-{flavor}.apk"


def size_in_megabytes(size):
    """Whole megabytes of 1,048,576 bytes, as the size budgets count them, rounded half up."""
    return (size + MEGABYTE // 2) // MEGABYTE


def read_checksums(text):
    """SHA256SUMS.txt (`sha256sum` output) as {file name: lowercase hex}."""
    sums = {}
    for line in text.splitlines():
        match = re.match(r"^([0-9a-fA-F]{64}) [ *](.+)$", line.strip())
        if match:
            sums[match.group(2).strip()] = match.group(1).lower()
    return sums


def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as file:
        for block in iter(lambda: file.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def caption(version, architecture, description, flavor_label, build, size, sha256, url):
    lines = [
        f"<b>Pingogram {escape(version)}</b> · {architecture} · {flavor_label}",
        f"Architecture: {architecture} ({description})",
        f"Build: {build}",
        f"Size: {size_in_megabytes(size)} MB",
        f"SHA-256: <code>{sha256}</code>",
        f'Source: <a href="{html.escape(url, quote=True)}">{escape(url)}</a>',
    ]
    text = "\n".join(lines)
    if visible_length(text) > CAPTION_LIMIT:
        raise ValueError(f"The caption of {architecture} {flavor_label} is over {CAPTION_LIMIT} characters")
    return text


def release_posts(version, dist, changelog, repository, server="https://github.com", today=None):
    tag = f"v{version}"
    release_url = f"{server}/{repository}/releases/tag/{tag}"
    # Only the announcement's first message notifies; its continuations and the files arrive silently.
    messages = announcement(version, changelog, release_url, today)
    posts = [Post(text, silent=number > 0) for number, text in enumerate(messages)]
    sums_file = dist / "SHA256SUMS.txt"
    sums = read_checksums(sums_file.read_text()) if sums_file.is_file() else {}
    for flavor, flavor_label, build in FLAVORS:
        for architecture, description in ARCHITECTURES:
            name = asset_name(version, architecture, flavor)
            path = dist / name
            post = Post("", file=path, file_name=telegram_name(version, architecture, flavor))
            if not path.is_file():
                post.error = f"{name} is not in {dist}"
            elif not sums:
                post.error = f"no SHA256SUMS.txt in {dist} to check {name} against"
            else:
                sha256 = sha256_of(path)
                if sums.get(name) != sha256:
                    post.error = f"{name}'s SHA-256 does not match SHA256SUMS.txt"
                else:
                    url = f"{server}/{repository}/releases/download/{tag}/{name}"
                    post.text = caption(
                        version, architecture, description, flavor_label, build, path.stat().st_size, sha256, url
                    )
            posts.append(post)
    return posts


def debug_posts(sha, subject, run_url, apk_dir):
    short = sha[:7]
    posts = [Post(f"Pingogram debug {short}\n{subject}\n{run_url}", html=False, silent=False)]
    for architecture, _ in ARCHITECTURES:
        path = apk_dir / f"app-foss-{architecture}-debug.apk"
        post = Post(f"Pingogram debug {short} · {architecture}", html=False, file=path, file_name=path.name)
        if not path.is_file():
            post.error = f"{path.name} is not in {apk_dir}"
        posts.append(post)
    return posts


# --- Sending ------------------------------------------------------------------------------------------------------


def print_dry_run(posts, channel):
    print(f"Dry run: nothing is sent. {len(posts)} posts for the {channel} channel.")
    for number, post in enumerate(posts, 1):
        sound = "silent" if post.silent else "notifies"
        if post.file is None:
            length = visible_length(post.text) if post.html else len(post.text)
            print(f"\n--- {number}. message ({sound}, {length} characters)")
        else:
            print(f"\n--- {number}. document {post.file_name} ({sound}), from {post.file}")
        print(f"NOT POSTED: {post.error}" if post.error else post.text)


def log(message):
    print(message, flush=True)


def warn(message):
    print(f"::warning::{message}", flush=True)


def channel_reference(value):
    """A channel secret as Telethon takes it: a username, or the marked integer ID of "-100…"."""
    value = value.strip()
    if re.fullmatch(r"@[A-Za-z][A-Za-z0-9_]{3,31}", value):
        return value[1:]
    if re.fullmatch(r"-100\d{5,}", value):
        return int(value)
    raise ValueError("the channel must be an @username or a numeric -100… ID")


def credentials(environment):
    """The API ID and hash: the uploader's own when both are set, otherwise the app's; None when neither is."""
    for id_name, hash_name in (
        ("TELEGRAM_UPLOAD_API_ID", "TELEGRAM_UPLOAD_API_HASH"),
        ("TELEGRAM_API_ID", "TELEGRAM_API_HASH"),
    ):
        api_id, api_hash = environment.get(id_name, "").strip(), environment.get(hash_name, "").strip()
        if api_id and api_hash:
            if not api_id.isdigit():
                raise ValueError(f"{id_name} is not a number")
            return int(api_id), api_hash
    return None


async def send_all(posts, token, api_id, api_hash, channel):
    from telethon import TelegramClient, errors
    from telethon.sessions import StringSession
    from telethon.tl.types import DocumentAttributeFilename

    failures = 0
    # In memory only: nothing of the login is written to disk.
    client = TelegramClient(StringSession(), api_id, api_hash)
    client.flood_sleep_threshold = 0  # Flood waits are handled below, each at most once.

    async def call(action, what):
        try:
            return await action()
        except errors.FloodWaitError as wait:
            if wait.seconds > FLOOD_WAIT_MAX_SECONDS:
                raise
            log(f"Telegram asked to wait {wait.seconds} s before {what}.")
            await asyncio.sleep(wait.seconds + 1)
            return await action()

    try:
        await client.start(bot_token=token)
    except (errors.RPCError, OSError, ConnectionError, asyncio.TimeoutError) as error:
        # Telegram's errors name the request, never the token.
        warn(f"The bot could not log in: {type(error).__name__}.")
        return len(posts)
    try:
        try:
            entity = await client.get_input_entity(channel)
        except (ValueError, errors.RPCError):
            warn("The bot cannot reach the channel: check its ID and that the bot is an admin who may post there.")
            return len(posts)
        for number, post in enumerate(posts):
            if number:
                await asyncio.sleep(PAUSE_SECONDS)
            what = post.file_name or "the message"
            if post.error:
                warn(f"Not posted: {post.error}.")
                failures += 1
                continue
            parse_mode = "html" if post.html else None
            try:
                if post.file is None:
                    await call(
                        lambda: client.send_message(
                            entity, post.text, parse_mode=parse_mode, link_preview=False, silent=post.silent
                        ),
                        what,
                    )
                else:
                    await call(
                        lambda: client.send_file(
                            entity,
                            str(post.file),
                            caption=post.text,
                            parse_mode=parse_mode,
                            force_document=True,
                            mime_type="application/vnd.android.package-archive",
                            attributes=[DocumentAttributeFilename(post.file_name)],
                            silent=post.silent,
                        ),
                        what,
                    )
                log(f"Posted {what}.")
            except (errors.RPCError, OSError, ConnectionError, asyncio.TimeoutError) as error:
                # Telegram's error names the request, never the token.
                warn(f"{what} was not posted: {type(error).__name__}: {error}")
                failures += 1
    finally:
        await client.disconnect()
    return failures


def main(argv=None, environment=None):
    environment = os.environ if environment is None else environment
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    release = commands.add_parser("release", help="a release's announcement and its 8 APKs")
    release.add_argument("--version", required=True)
    release.add_argument("--dist", type=Path, required=True)
    release.add_argument("--changelog", type=Path, required=True)
    release.add_argument("--repo", required=True)
    release.add_argument("--server-url", default="https://github.com")
    release.add_argument("--channel", choices=["releases", "debug"], default="releases")
    release.add_argument("--dry-run", action="store_true")
    debug = commands.add_parser("debug", help="a debug build of main, to the debug channel only")
    debug.add_argument("--sha", required=True)
    debug.add_argument("--subject", required=True)
    debug.add_argument("--run-url", required=True)
    debug.add_argument("--apk-dir", type=Path, required=True)
    debug.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)

    if args.command == "release":
        if not re.fullmatch(r"\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?", args.version):
            parser.error("--version takes a version such as 1.0.0, without the v")
        posts = release_posts(
            args.version, args.dist, args.changelog.read_text(encoding="utf-8"), args.repo, args.server_url.rstrip("/")
        )
        channel = args.channel
    else:
        posts = debug_posts(args.sha, args.subject, args.run_url, args.apk_dir)
        channel = "debug"  # Never the releases channel.

    if args.dry_run:
        print_dry_run(posts, channel)
        return 1 if any(post.error for post in posts) else 0

    secret = CHANNEL_SECRETS[channel]
    token = environment.get("TELEGRAM_BOT_TOKEN", "").strip()
    target = environment.get(secret, "").strip()
    if not token or not target:
        log(f"Channel upload skipped: TELEGRAM_BOT_TOKEN or {secret} is not set.")
        return 0
    try:
        api = credentials(environment)
        reference = channel_reference(target)
    except ValueError as error:
        warn(f"Channel upload skipped: {error} ({secret}).")
        return 1
    if api is None:
        log("Channel upload skipped: TELEGRAM_API_ID and TELEGRAM_API_HASH are not set.")
        return 0
    failures = asyncio.run(send_all(posts, token, api[0], api[1], reference))
    if failures:
        warn(f"{failures} of {len(posts)} posts were not posted.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

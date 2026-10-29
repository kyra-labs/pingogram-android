"""Tests of telegram_publish.py: python3 -m unittest discover -s tools/ci -p 'test_*.py' (ci.yml runs them)."""

import asyncio
import datetime
import hashlib
import io
import sys
import tempfile
import types
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

import telegram_publish as tp

CHANGELOG = """# Changelog

## [Unreleased]

## [1.0.0] - 2026-10-29

### Added
- A `t.me/call` link opens the call. See [the docs](https://example.org/a?b=1&c=2).
  It goes on over two lines, with <angle> & ampersands.
  - A nested detail.
- **Bold** and *italic* words, snake_case_names and 2 * 3 stay as they are.

### Fixed
- Search keeps its background.

## [0.6.0] - 2026-10-26

### Added
- Older.

[1.0.0]: https://github.com/kyra-labs/pingogram-android/compare/v0.6.0...v1.0.0
"""


class ConversionTest(unittest.TestCase):
    def test_inline_markdown_becomes_telegram_html_and_the_rest_is_escaped(self):
        self.assertEqual(
            tp.inline("Use `a<b>` and [the docs](https://x.org/?a=1&b=\"2\"), **bold** & *it*"),
            'Use <code>a&lt;b&gt;</code> and <a href="https://x.org/?a=1&amp;b=&quot;2&quot;">the docs</a>, '
            "<b>bold</b> &amp; <i>it</i>",
        )

    def test_underscores_and_lone_asterisks_are_not_markup(self):
        self.assertEqual(tp.inline("snake_case_name and 2 * 3"), "snake_case_name and 2 * 3")

    def test_the_section_becomes_bold_headings_and_bullets_without_markdown(self):
        date, body = tp.changelog_section(CHANGELOG, "1.0.0")
        self.assertEqual(date, datetime.date(2026, 10, 29))
        units = tp.changelog_units(body)
        html = "".join(separator + unit for separator, unit, _ in units)
        self.assertEqual(
            html,
            "\n\n<b>Added</b>\n"
            "• A <code>t.me/call</code> link opens the call. See "
            '<a href="https://example.org/a?b=1&amp;c=2">the docs</a>. '
            "It goes on over two lines, with &lt;angle&gt; &amp; ampersands.\n"
            "    ◦ A nested detail.\n"
            "• <b>Bold</b> and <i>italic</i> words, snake_case_names and 2 * 3 stay as they are."
            "\n\n<b>Fixed</b>\n"
            "• Search keeps its background.",
        )
        for leftover in ("**", "`", "](", "### ", "\n- "):
            self.assertNotIn(leftover, html)
        self.assertEqual([section for _, _, section in units], [None, "<b>Added</b>", None])

    def test_the_section_stops_before_the_next_version_and_the_links(self):
        _, body = tp.changelog_section(CHANGELOG, "0.6.0")
        self.assertEqual([line for line in body if line.strip()], ["### Added", "- Older."])

    def test_a_missing_section_says_so(self):
        messages = tp.announcement("9.9.9", CHANGELOG, "https://x.org/r", today=datetime.date(2026, 1, 2))
        self.assertIn("No changelog section for 9.9.9.", messages[0])
        self.assertIn("January 2, 2026", messages[0])

    def test_the_announcement_heads_with_the_version_and_date_and_ends_with_the_release(self):
        messages = tp.announcement("1.0.0", CHANGELOG, "https://github.com/o/r/releases/tag/v1.0.0")
        self.assertEqual(len(messages), 1)
        self.assertTrue(messages[0].startswith("<b>Pingogram 1.0.0</b>\nOctober 29, 2026\n\n<b>Added</b>"))
        self.assertNotIn("Beta", messages[0])
        self.assertTrue(
            messages[0].endswith(
                'Release on GitHub: <a href="https://github.com/o/r/releases/tag/v1.0.0">'
                "https://github.com/o/r/releases/tag/v1.0.0</a>"
            )
        )

    def test_pre_releases_say_beta(self):
        messages = tp.announcement("0.6.0", CHANGELOG, "https://x.org/r")
        self.assertTrue(messages[0].startswith("<b>Pingogram 0.6.0</b>\nBeta\nOctober 26, 2026"))
        self.assertTrue(tp.is_prerelease("1.1.0-rc.1"))
        self.assertFalse(tp.is_prerelease("1.0.0"))


def bullets(count, length):
    return [("\n", "• " + str(n).rjust(4, "0") + " " + "x" * (length - 7), None) for n in range(count)]


class SplittingTest(unittest.TestCase):
    def test_visible_length_counts_text_not_tags_in_utf16_units(self):
        self.assertEqual(tp.visible_length('<b>a</b> <a href="https://x.org">&amp;</a> 😀'), 6)

    def test_a_long_section_splits_at_bullet_boundaries_into_messages_of_at_most_4096(self):
        units = bullets(30, 300)
        messages = tp.split_messages("<b>Head</b>", units, "end", "…and more")
        self.assertEqual(len(messages), 3)
        self.assertTrue(all(tp.visible_length(message) <= tp.TEXT_LIMIT for message in messages))
        joined = "\n".join(messages)
        for _, unit, _ in units:
            self.assertIn(unit, joined)  # no bullet is cut
        self.assertTrue(messages[-1].endswith("\n\nend"))

    def test_what_does_not_fit_in_three_messages_ends_with_and_more(self):
        units = bullets(60, 300)
        messages = tp.split_messages("<b>Head</b>", units, "end", "…and more: link")
        self.assertEqual(len(messages), 3)
        self.assertTrue(all(tp.visible_length(message) <= tp.TEXT_LIMIT for message in messages))
        self.assertTrue(messages[-1].endswith("\n\n…and more: link"))
        self.assertNotIn("end", messages[-1].replace("…and more", ""))
        last_bullet = messages[-1].split("\n\n…and more")[0].split("\n")[-1]
        self.assertIn(last_bullet, [unit for _, unit, _ in units])  # it ends on a whole bullet

    def test_the_footer_takes_a_message_of_its_own_when_the_last_is_full(self):
        units = bullets(13, 300)  # one full message
        first, _ = tp._fill("H", units, [tp.TEXT_LIMIT])
        self.assertGreater(tp.visible_length(first[0] + "\n\n" + "e" * 200), tp.TEXT_LIMIT)
        messages = tp.split_messages("H", units, "e" * 200, "more")
        self.assertEqual(messages[-1], "e" * 200)

    def test_a_message_that_starts_within_a_section_repeats_its_heading(self):
        units = [("\n\n", "<b>Added</b>\n• first", None)] + [
            ("\n", "• " + "y" * 1000, "<b>Added</b>") for _ in range(6)
        ]
        messages = tp.split_messages("H", units, "end", "more")
        self.assertEqual(len(messages), 2)
        self.assertTrue(messages[1].startswith("<b>Added</b>\n• yyy"))

    def test_a_bullet_longer_than_a_message_is_shortened(self):
        messages = tp.split_messages("H", [("\n", "• " + "word " * 2000, None)], "end", "more")
        self.assertTrue(all(tp.visible_length(message) <= tp.TEXT_LIMIT for message in messages))


class FilesTest(unittest.TestCase):
    def test_file_names_on_github_and_in_telegram(self):
        self.assertEqual(tp.asset_name("1.0.0", "arm64-v8a", "foss"), "pingogram-1.0.0-arm64-v8a.apk")
        self.assertEqual(tp.asset_name("1.0.0", "universal", "gms"), "pingogram-1.0.0-universal-gms.apk")
        self.assertEqual(tp.telegram_name("1.0.0", "arm64-v8a", "foss"), "pingogram-1.0.0-arm64-v8a-foss.apk")
        self.assertEqual(tp.telegram_name("1.0.0", "x86_64", "gms"), "pingogram-1.0.0-x86_64-gms.apk")

    def test_sizes_round_to_whole_megabytes_of_1048576_bytes(self):
        self.assertEqual(tp.size_in_megabytes(50 * 1_048_576), 50)
        self.assertEqual(tp.size_in_megabytes(int(48.49 * 1_048_576)), 48)
        self.assertEqual(tp.size_in_megabytes(int(48.5 * 1_048_576)), 49)
        self.assertEqual(tp.size_in_megabytes(121_000_000), 115)

    def test_checksums_read_both_sha256sum_formats(self):
        a, b = "a" * 64, "B" * 64
        self.assertEqual(tp.read_checksums(f"{a}  one.apk\n{b} *two.apk\nnoise\n"), {"one.apk": a, "two.apk": b.lower()})

    def test_the_longest_caption_stays_within_1024_characters(self):
        repository = "an-organization-with-a-long-name/" + "r" * 60
        url = f"https://github.com/{repository}/releases/download/v10.20.30-rc.40/pingogram-10.20.30-rc.40-armeabi-v7a-gms.apk"
        text = tp.caption(
            "10.20.30-rc.40", "armeabi-v7a", "older 32-bit phones", "GMS", tp.FLAVORS[1][2], 49_000_000, "f" * 64, url
        )
        self.assertLessEqual(tp.visible_length(text), tp.CAPTION_LIMIT)
        self.assertEqual(
            text.splitlines()[:4],
            [
                "<b>Pingogram 10.20.30-rc.40</b> · armeabi-v7a · GMS",
                "Architecture: armeabi-v7a (older 32-bit phones)",
                "Build: GMS, push notifications through Firebase Cloud Messaging",
                "Size: 47 MB",
            ],
        )
        self.assertEqual(text.splitlines()[5], f'Source: <a href="{url}">{url}</a>')


class ReleasePostsTest(unittest.TestCase):
    def setUp(self):
        self.dist = Path(tempfile.mkdtemp())
        sums = []
        for flavor, _, _ in tp.FLAVORS:
            for architecture, _ in tp.ARCHITECTURES:
                name = tp.asset_name("1.0.0", architecture, flavor)
                data = f"{name}".encode()
                (self.dist / name).write_bytes(data)
                sums.append(f"{hashlib.sha256(data).hexdigest()}  {name}")
        (self.dist / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n")

    def posts(self):
        return tp.release_posts("1.0.0", self.dist, CHANGELOG, "o/r")

    def test_the_announcement_notifies_then_eight_silent_documents_in_order(self):
        posts = self.posts()
        self.assertFalse(posts[0].silent)
        files = [post for post in posts if post.file is not None]
        self.assertEqual(
            [post.file_name for post in files],
            [
                f"pingogram-1.0.0-{architecture}-{flavor}.apk"
                for flavor in ("foss", "gms")
                for architecture in ("universal", "arm64-v8a", "armeabi-v7a", "x86_64")
            ],
        )
        self.assertTrue(all(post.silent and post.error is None for post in files))
        self.assertIn(
            "Source: <a href=\"https://github.com/o/r/releases/download/v1.0.0/pingogram-1.0.0-universal-gms.apk\">",
            files[4].text,
        )
        self.assertFalse(any("SHA256SUMS" in str(post.file) for post in files))

    def test_a_checksum_mismatch_or_a_missing_file_skips_that_file_only(self):
        (self.dist / "pingogram-1.0.0-x86_64.apk").write_bytes(b"changed")
        (self.dist / "pingogram-1.0.0-universal-gms.apk").unlink()
        errors = {post.file_name: post.error for post in self.posts() if post.file is not None}
        self.assertIn("does not match SHA256SUMS.txt", errors.pop("pingogram-1.0.0-x86_64-foss.apk"))
        self.assertIn("is not in", errors.pop("pingogram-1.0.0-universal-gms.apk"))
        self.assertTrue(all(error is None for error in errors.values()))

    def test_the_dry_run_prints_everything_and_fails_on_a_skipped_file(self):
        (self.dist / "pingogram-1.0.0-x86_64.apk").write_bytes(b"changed")
        changelog = self.dist / "CHANGELOG.md"
        changelog.write_text(CHANGELOG)
        out = io.StringIO()
        with redirect_stdout(out):
            status = tp.main(
                ["release", "--version", "1.0.0", "--dist", str(self.dist), "--changelog", str(changelog),
                 "--repo", "o/r", "--dry-run"],
                environment={"TELEGRAM_BOT_TOKEN": "123:secret-token"},
            )
        self.assertEqual(status, 1)
        self.assertIn("NOT POSTED: pingogram-1.0.0-x86_64.apk's SHA-256 does not match", out.getvalue())
        self.assertNotIn("secret-token", out.getvalue())


class SecretsTest(unittest.TestCase):
    def run_main(self, environment, *extra):
        out = io.StringIO()
        directory = tempfile.mkdtemp()
        with redirect_stdout(out):
            status = tp.main(
                ["debug", "--sha", "0123456789abcdef", "--subject", "s", "--run-url", "u", "--apk-dir", directory,
                 *extra],
                environment=environment,
            )
        return status, out.getvalue()

    def test_without_the_token_or_the_channel_it_skips_and_succeeds(self):
        self.assertEqual(self.run_main({})[0], 0)
        status, out = self.run_main({"TELEGRAM_BOT_TOKEN": "t", "TELEGRAM_CHANNEL_ID": "@releases"})
        self.assertEqual(status, 0)  # a debug build never falls back to the releases channel
        self.assertIn("TELEGRAM_DEBUG_CHANNEL_ID is not set", out)

    def test_the_uploader_credentials_win_when_both_are_set(self):
        app = {"TELEGRAM_API_ID": "1", "TELEGRAM_API_HASH": "app"}
        self.assertEqual(tp.credentials(app), (1, "app"))
        self.assertEqual(tp.credentials({**app, "TELEGRAM_UPLOAD_API_ID": "2"}), (1, "app"))
        self.assertEqual(
            tp.credentials({**app, "TELEGRAM_UPLOAD_API_ID": "2", "TELEGRAM_UPLOAD_API_HASH": "up"}), (2, "up")
        )
        self.assertIsNone(tp.credentials({}))

    def test_channels_are_usernames_or_marked_ids(self):
        self.assertEqual(tp.channel_reference("@pingogram_builds"), "pingogram_builds")
        self.assertEqual(tp.channel_reference(" -1001234567890 "), -1001234567890)
        for bad in ("1234567890", "pingogram", "@ab", "-100", "https://t.me/x"):
            with self.assertRaises(ValueError):
                tp.channel_reference(bad)

    def test_a_bad_channel_value_is_never_printed(self):
        status, out = self.run_main(
            {"TELEGRAM_BOT_TOKEN": "t", "TELEGRAM_DEBUG_CHANNEL_ID": "not-a-channel-42", "TELEGRAM_API_ID": "1",
             "TELEGRAM_API_HASH": "h"}
        )
        self.assertEqual(status, 1)
        self.assertNotIn("not-a-channel-42", out)

    def test_the_debug_post_is_plain_with_every_abi_universal_first(self):
        posts = tp.debug_posts("0123456789abcdef", "fix: x", "https://run", Path("/nowhere"))
        self.assertEqual(posts[0].text, "Pingogram debug 0123456\nfix: x\nhttps://run")
        self.assertFalse(posts[0].html or posts[0].silent)
        self.assertEqual(
            [post.file_name for post in posts[1:]],
            ["app-foss-universal-debug.apk", "app-foss-arm64-v8a-debug.apk", "app-foss-armeabi-v7a-debug.apk",
             "app-foss-x86_64-debug.apk"],
        )
        self.assertTrue(all(post.silent for post in posts[1:]))


class FakeTelethon:
    """Just enough of Telethon for send_all: a client that records what it sends and fails as told."""

    class RPCError(Exception):
        pass

    class FloodWaitError(RPCError):
        def __init__(self, seconds):
            super().__init__(f"A wait of {seconds} seconds is required")
            self.seconds = seconds

    def __init__(self, failures):
        self.failures = failures  # what each send raises, in order (None: it succeeds)
        self.sent = []
        self.slept = []
        fake = self

        class Client:
            def __init__(self, session, api_id, api_hash):
                fake.login = (type(session).__name__, api_id, api_hash)

            async def start(self, bot_token):
                fake.token = bot_token

            async def get_input_entity(self, channel):
                return ("entity", channel)

            async def disconnect(self):
                fake.disconnected = True

            async def _send(self, kind, **details):
                failure = fake.failures.pop(0) if fake.failures else None
                if failure:
                    raise failure
                fake.sent.append((kind, details))

            async def send_message(self, entity, text, **options):
                await self._send("message", text=text, **options)

            async def send_file(self, entity, file, **options):
                await self._send("file", file=file, **options)

        self.modules = {
            "telethon": types.SimpleNamespace(
                TelegramClient=Client,
                errors=types.SimpleNamespace(RPCError=self.RPCError, FloodWaitError=self.FloodWaitError),
            ),
            "telethon.sessions": types.SimpleNamespace(StringSession=type("StringSession", (), {})),
            "telethon.tl.types": types.SimpleNamespace(DocumentAttributeFilename=lambda name: ("filename", name)),
        }

    def run(self, posts):
        async def sleep(seconds):
            self.slept.append(seconds)

        with mock.patch.dict(sys.modules, self.modules), mock.patch.object(tp.asyncio, "sleep", sleep):
            with redirect_stdout(io.StringIO()) as out:
                failures = asyncio.run(tp.send_all(posts, "123:token", 1, "hash", "channel"))
        return failures, out.getvalue()


class SendingTest(unittest.TestCase):
    def posts(self):
        return [
            tp.Post("<b>News</b>", silent=False),
            tp.Post("a", file=Path("a.apk"), file_name="pingogram-1.0.0-universal-foss.apk"),
            tp.Post("b", file=Path("b.apk"), file_name="b.apk", error="b.apk's SHA-256 does not match"),
            tp.Post("c", file=Path("c.apk"), file_name="c.apk"),
        ]

    def test_it_logs_in_in_memory_and_sends_each_post_as_told(self):
        telethon = FakeTelethon([])
        failures, _ = telethon.run(self.posts())
        self.assertEqual(failures, 1)  # the file whose checksum did not match
        self.assertEqual(telethon.login, ("StringSession", 1, "hash"))
        self.assertTrue(telethon.disconnected)
        (kind, message), (_, file), _ = telethon.sent
        self.assertEqual(kind, "message")
        self.assertEqual((message["parse_mode"], message["link_preview"], message["silent"]), ("html", False, False))
        self.assertEqual(
            (file["file"], file["force_document"], file["silent"], file["attributes"]),
            ("a.apk", True, True, [("filename", "pingogram-1.0.0-universal-foss.apk")]),
        )
        self.assertEqual(file["mime_type"], "application/vnd.android.package-archive")

    def test_a_flood_wait_is_waited_out_once_then_retried(self):
        telethon = FakeTelethon([None, FakeTelethon.FloodWaitError(42)])
        failures, out = telethon.run(self.posts())
        self.assertEqual(failures, 1)
        self.assertIn(43, telethon.slept)
        self.assertIn("Telegram asked to wait 42 s", out)
        self.assertEqual([details.get("file") for _, details in telethon.sent], [None, "a.apk", "c.apk"])

    def test_a_long_flood_wait_or_a_refused_file_skips_only_that_file(self):
        telethon = FakeTelethon([None, FakeTelethon.FloodWaitError(301), FakeTelethon.RPCError("CHAT_WRITE_FORBIDDEN")])
        failures, out = telethon.run(self.posts())
        self.assertEqual(failures, 3)  # a.apk waited too long, b.apk mismatched, c.apk refused
        self.assertNotIn(302, telethon.slept)
        self.assertEqual(len(telethon.sent), 1)
        self.assertIn("c.apk was not posted: RPCError: CHAT_WRITE_FORBIDDEN", out)
        self.assertNotIn("123:token", out)

    def test_plain_posts_are_not_parsed(self):
        telethon = FakeTelethon([])
        telethon.run([tp.Post("Pingogram debug *abc*", html=False, silent=False)])
        self.assertIsNone(telethon.sent[0][1]["parse_mode"])


if __name__ == "__main__":
    unittest.main()

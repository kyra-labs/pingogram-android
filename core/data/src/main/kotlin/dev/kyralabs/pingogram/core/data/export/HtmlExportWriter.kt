package dev.kyralabs.pingogram.core.data.export

import dev.kyralabs.pingogram.core.domain.model.ExportChat
import dev.kyralabs.pingogram.core.domain.model.ExportMedia
import dev.kyralabs.pingogram.core.domain.model.ExportMediaType
import dev.kyralabs.pingogram.core.domain.model.ExportMessage
import dev.kyralabs.pingogram.core.domain.model.ExportSkipReason
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.repository.ExportTexts
import java.util.Locale

/**
 * `messages.html` (`docs/adr/0021-chat-export.md`): one page that needs nothing else, to read in any browser. Its CSS
 * is inline (light and dark from the reader's system), it has no script and loads nothing from the network: its only
 * links are those the messages hold (http, https and tg links, as written) and the files copied into `media/`.
 *
 * Every text from people is escaped, and takes its own direction (`dir="auto"`). A day heading starts each day, the
 * sender's name each run of messages from one sender; a reply links to the message it answers where that is in the
 * export ([isExported]). Media that was copied shows (photos, videos, voice messages) or links to its file; media
 * that was not is named, with why.
 */
internal class HtmlExportWriter(
    private val out: Appendable,
    private val texts: ExportTexts,
    private val isExported: (messageId: Long) -> Boolean,
) : ExportWriter {
    private val labels = texts.labels
    private var day: String? = null
    private var lastSender: Pair<Long, Boolean>? = null
    private var count = 0

    override fun begin(
        chat: ExportChat,
        exportedAt: Long,
        messageCount: Int,
    ) {
        val title = escape(chat.title)
        out
            .append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            .append("<meta name=\"generator\" content=\"Pingogram\">\n")
            .append("<title dir=\"auto\">")
            .append(title)
            .append("</title>\n")
            .append("<style>")
            .append(STYLE)
            .append("</style>\n</head>\n<body>\n<header>\n")
            .append("<h1 dir=\"auto\">")
            .append(title)
            .append("</h1>\n")
            .append("<p>")
            .append(escape(labels.exportedOn.format(texts.day(exportedAt) + ", " + texts.time(exportedAt))))
            .append(" · ")
            .append(escape(labels.messageCount(messageCount)))
            .append("</p>\n</header>\n<main>\n")
    }

    override fun message(message: ExportMessage) {
        count++
        val messageDay = texts.day(message.date)
        if (messageDay != day) {
            day = messageDay
            lastSender = null
            out.append("<h2 class=\"day\">").append(escape(messageDay)).append("</h2>\n")
        }
        val service = message.service
        if (service != null) {
            lastSender = null
            out
                .append("<p class=\"service\" id=\"m")
                .append(message.id.toString())
                .append("\" dir=\"auto\">")
                .append(escape(service))
                .append("</p>\n")
            return
        }
        val direction = if (message.isOutgoing) "out" else "in"
        out.append("<article class=\"message ").append(direction).append("\" id=\"m")
        out.append(message.id.toString()).append("\">\n")
        sender(message)
        message.forwardedFrom?.let { from ->
            out.append("<p class=\"note\" dir=\"auto\">").append(escape(labels.forwardedFrom.format(from)))
            out.append("</p>\n")
        }
        message.replyToMessageId?.let { replied ->
            if (isExported(replied)) {
                out.append("<p class=\"note\"><a href=\"#m").append(replied.toString()).append("\">")
                out.append(escape(labels.inReplyTo)).append("</a></p>\n")
            } else {
                out.append("<p class=\"note\">").append(escape(labels.replyNotExported)).append("</p>\n")
            }
        }
        message.media?.let(::media)
        message.summary?.let { out.append("<p class=\"summary\" dir=\"auto\">").append(escape(it)).append("</p>\n") }
        if (message.text.text.isNotEmpty()) {
            out.append("<div class=\"text\" dir=\"auto\">")
            message.text.toParts().forEach(::part)
            out.append("</div>\n")
        }
        out.append("<p class=\"meta\"><time datetime=\"").append(isoTime(message.date)).append("\">")
        out.append(escape(texts.time(message.date))).append("</time>")
        if (message.editDate > 0) out.append(" · ").append(escape(labels.edited))
        out.append("</p>\n</article>\n")
    }

    /** The sender's name, once per run of their messages on one day. */
    private fun sender(message: ExportMessage) {
        val sender = message.sender
        val key = sender?.let { it.id to it.isChat }
        if (key == lastSender) return
        lastSender = key
        if (sender == null) return
        out.append("<p class=\"from\" dir=\"auto\">").append(escape(sender.name)).append("</p>\n")
    }

    private fun media(media: ExportMedia) {
        val name = labels.mediaName(media.type)
        val path = media.path
        if (path == null) {
            val reason =
                when (media.skipped) {
                    ExportSkipReason.TooLarge -> labels.mediaTooLarge
                    ExportSkipReason.SelfDestructing -> labels.mediaSelfDestructing
                    ExportSkipReason.Unavailable -> labels.mediaUnavailable
                    ExportSkipReason.NotChosen, null -> labels.mediaNotExported
                }
            out.append("<p class=\"missing\">").append(escape(reason.format(name))).append("</p>\n")
            return
        }
        val source = escape(encodePath(path))
        when (media.type) {
            ExportMediaType.Photo -> {
                out
                    .append("<a class=\"media\" href=\"")
                    .append(source)
                    .append("\"><img src=\"")
                    .append(source)
                out.append("\" alt=\"").append(escape(name)).append("\" loading=\"lazy\"></a>\n")
            }

            ExportMediaType.Video, ExportMediaType.VideoNote -> {
                out.append("<video class=\"media\" controls preload=\"metadata\" src=\"").append(source)
                out.append("\"></video>\n")
            }

            ExportMediaType.Animation -> {
                out.append("<video class=\"media\" controls loop muted preload=\"metadata\" src=\"").append(source)
                out.append("\"></video>\n")
            }

            ExportMediaType.VoiceNote, ExportMediaType.Audio -> {
                out.append("<audio controls preload=\"none\" src=\"").append(source).append("\"></audio>\n")
                fileLink(media, source, name)
            }

            ExportMediaType.Document, ExportMediaType.Sticker -> {
                fileLink(media, source, name)
            }
        }
    }

    private fun fileLink(
        media: ExportMedia,
        source: String,
        name: String,
    ) {
        out.append("<p class=\"file\"><a href=\"").append(source).append("\" dir=\"auto\">")
        out.append(escape(media.fileName.ifEmpty { name })).append("</a></p>\n")
    }

    private fun part(part: TextPart) {
        when (part) {
            is TextPart.Plain -> {
                out.append(escape(part.text))
            }

            is TextPart.Marked -> {
                val (open, close) = tags(part.entity, part.text)
                out.append(open)
                part.parts.forEach(::part)
                out.append(close)
            }
        }
    }

    /** The tags around an entity's text; none for those that only mark what the text says. */
    private fun tags(
        entity: TextEntityType,
        text: String,
    ): Pair<String, String> =
        when (entity) {
            TextEntityType.Bold -> "<strong>" to "</strong>"
            TextEntityType.Italic -> "<em>" to "</em>"
            TextEntityType.Underline -> "<u>" to "</u>"
            TextEntityType.Strikethrough -> "<s>" to "</s>"
            TextEntityType.Spoiler -> "<span class=\"spoiler\" tabindex=\"0\">" to "</span>"
            TextEntityType.Code -> "<code>" to "</code>"
            TextEntityType.Pre -> "<pre>" to "</pre>"
            is TextEntityType.PreCode -> "<pre><code>" to "</code></pre>"
            TextEntityType.BlockQuote, TextEntityType.ExpandableBlockQuote -> "<blockquote>" to "</blockquote>"
            TextEntityType.Url -> link(if (SCHEME.containsMatchIn(text)) text else "http://$text")
            is TextEntityType.TextUrl -> link(entity.url)
            else -> "" to ""
        }

    /** A link to [url] where it is an http, https or tg link; plain text for anything else. */
    private fun link(url: String): Pair<String, String> =
        if (LINKABLE.containsMatchIn(url)) {
            "<a href=\"${escape(url)}\" rel=\"noreferrer\">" to "</a>"
        } else {
            "" to ""
        }

    override fun end() {
        if (count == 0) out.append("<p class=\"service\">").append(escape(labels.noMessages)).append("</p>\n")
        out.append("</main>\n</body>\n</html>\n")
    }

    private companion object {
        /** A link that names its scheme. */
        val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

        /** The schemes a message's link may have to be one in the page. */
        val LINKABLE = Regex("^(https?|tg)://", RegexOption.IGNORE_CASE)

        // The page's look: Pingogram's own, light or dark with the reader's system, no web fonts.
        const val STYLE =
            ":root{color-scheme:light dark;--bg:#f9f9ff;--text:#191c20;--muted:#44474e;--in:#e7e8ee;" +
                "--out:#d9e2ff;--line:#c4c6d0;--link:#2156c4}" +
                "@media (prefers-color-scheme:dark){:root{--bg:#111318;--text:#e2e2e9;--muted:#c4c6d0;" +
                "--in:#282a2f;--out:#2b4678;--line:#44474e;--link:#b1c5ff}}" +
                "body{margin:0;background:var(--bg);color:var(--text);font:16px/1.5 system-ui,sans-serif}" +
                "header,main{max-width:720px;margin:0 auto;padding:16px}" +
                "header{border-bottom:1px solid var(--line)}h1{font-size:28px;margin:0 0 4px}" +
                "header p{margin:0;color:var(--muted)}" +
                ".day{font-size:14px;font-weight:500;text-align:center;color:var(--muted);margin:24px 0 8px}" +
                ".message{max-width:85%;width:fit-content;margin:4px 0;padding:8px 12px;border-radius:20px;" +
                "background:var(--in);overflow-wrap:anywhere}" +
                ".message.out{margin-left:auto;background:var(--out)}" +
                ".message p{margin:0}.from{font-weight:600;font-size:14px}" +
                ".note,.meta,.missing,.summary{font-size:14px;color:var(--muted)}.meta{text-align:end}" +
                ".text{white-space:pre-wrap}.media{display:block;max-width:100%;border-radius:12px;margin:4px 0}" +
                ".media img{display:block;max-width:100%;border-radius:12px}" +
                "a{color:var(--link)}code,pre{font-family:monospace;font-size:14px}" +
                "pre{white-space:pre-wrap;margin:4px 0}" +
                "blockquote{margin:4px 0;padding-left:12px;border-left:3px solid var(--line)}" +
                ".spoiler{background:var(--muted);color:transparent;border-radius:4px}" +
                ".spoiler:hover,.spoiler:focus{background:none;color:inherit}" +
                ".service{text-align:center;font-size:14px;color:var(--muted);margin:8px 0}"
    }
}

/** [text] safe in HTML text and in quoted attributes. */
internal fun escape(text: String): String {
    if (text.none { it in ESCAPED }) return text
    val result = StringBuilder(text.length + ESCAPE_ROOM)
    for (char in text) {
        when (char) {
            '&' -> result.append("&amp;")
            '<' -> result.append("&lt;")
            '>' -> result.append("&gt;")
            '"' -> result.append("&quot;")
            '\'' -> result.append("&#39;")
            else -> result.append(char)
        }
    }
    return result.toString()
}

private const val ESCAPED = "&<>\"'"
private const val ESCAPE_ROOM = 16

/** A relative path ("media/12_Report 2.pdf") as a URL: each segment percent-encoded in UTF-8, the slashes kept. */
internal fun encodePath(path: String): String =
    path.split('/').joinToString("/") { segment ->
        buildString {
            for (byte in segment.encodeToByteArray()) {
                val value = byte.toInt() and BYTE_MASK
                val char = value.toChar()
                if (value < ASCII_END && (char.isLetterOrDigit() || char in UNRESERVED)) {
                    append(char)
                } else {
                    append('%').append(value.toString(HEX_RADIX).uppercase(Locale.ROOT).padStart(2, '0'))
                }
            }
        }
    }

private const val UNRESERVED = "-._~"
private const val BYTE_MASK = 0xFF
private const val ASCII_END = 0x80
private const val HEX_RADIX = 16

package dev.kyralabs.pingogram.core.domain.link

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import kotlinx.collections.immutable.toImmutableList

/**
 * Removes tracking parameters from links (`REQUIREMENTS.md` FR-X8): those that only tell a site or an ad network where
 * a click came from, never what the page shows. The list is small on purpose, so no link stops working:
 * - every `utm_` parameter (`utm_source`, `utm_medium`, `utm_campaign`, `utm_term`, `utm_content`, …);
 * - [TRACKING_PARAMETERS]: Meta's `fbclid`, `igshid` and `igsh`, Google's `gclid`, `dclid`, `gbraid` and `wbraid`,
 *   Microsoft's `msclkid`, Yandex's `yclid`, Mailchimp's `mc_cid` and `mc_eid`, HubSpot's `_hsenc` and `_hsmi`;
 * - `si` on YouTube and Spotify links only ([SHARE_ID_HOSTS]), the share ID their apps add, as elsewhere `si` can
 *   mean anything.
 *
 * Names are matched without regard to case. Everything else stays as it was written: the other parameters in their
 * order, the fragment, the encoding. Only `http`, `https` and `www.` links are touched.
 */
object LinkCleaner {
    /** [url] without its tracking parameters; [url] itself if it has none. */
    fun cleanUrl(url: String): String {
        val link = WebLink.of(url) ?: return url
        val kept = link.parameters.filterNot { it.isNotEmpty() && isTracking(it.substringBefore('='), link.host) }
        return if (kept.size == link.parameters.size) url else link.withParameters(kept)
    }

    /**
     * [text] with every link in it cleaned ([cleanUrl]): the links written out (TDLib's `Url` entities and those it
     * would find, `http`, `https` or `www.`) and the targets of `TextUrl` entities. Where a link written out gets
     * shorter, the entities after it move back and those around it shrink, so the formatting stays on the same words.
     * [text] itself when nothing changes.
     */
    fun clean(text: FormattedText): FormattedText {
        val edits = linkSpans(text).mapNotNull { span -> span.cleaned(text.text) }
        val entities = text.entities.map { it.withCleanTarget() }
        if (edits.isEmpty() && entities == text.entities) return text
        val cleanedText =
            buildString {
                var position = 0
                for (edit in edits) {
                    append(text.text, position, edit.start)
                    append(edit.replacement)
                    position = edit.end
                }
                append(text.text, position, text.text.length)
            }
        val moved =
            entities.map { entity ->
                val start = edits.map(entity.offset)
                val end = edits.map(entity.offset + entity.length)
                entity.copy(offset = start, length = end - start)
            }
        return FormattedText(cleanedText, moved.toImmutableList())
    }

    private fun isTracking(
        name: String,
        host: String,
    ): Boolean {
        val lowercase = name.lowercase()
        return lowercase.startsWith(UTM_PREFIX) ||
            lowercase in TRACKING_PARAMETERS ||
            (lowercase == SHARE_ID && SHARE_ID_HOSTS.any { host == it || host.endsWith(".$it") })
    }

    /** The links written out in [text]: its `Url` entities, then the links TDLib would find that none overlaps. */
    private fun linkSpans(text: FormattedText): List<IntRange> {
        val marked =
            text.entities
                .filter { it.type == TextEntityType.Url }
                .map { it.offset until it.offset + it.length }
        val found =
            WEB_LINK
                .findAll(text.text)
                .map { match -> match.range.first..trimEnd(text.text, match.range) }
                .filter { range -> marked.none { it.first <= range.last && range.first <= it.last } }
        return (marked + found).sortedBy { it.first }
    }

    /** The last index of [range] in [text] without the punctuation that usually follows a link in a sentence. */
    private fun trimEnd(
        text: String,
        range: IntRange,
    ): Int {
        var last = range.last
        while (last > range.first && text[last] in TRAILING_PUNCTUATION) last--
        return last
    }

    private fun IntRange.cleaned(text: String): Edit? {
        val link = text.substring(first, last + 1)
        val clean = cleanUrl(link)
        return if (clean == link) null else Edit(first, last + 1, clean)
    }

    private fun TextEntity.withCleanTarget(): TextEntity {
        val type = type as? TextEntityType.TextUrl ?: return this
        val clean = cleanUrl(type.url)
        return if (clean == type.url) this else copy(type = TextEntityType.TextUrl(clean))
    }

    /** Where [position] of the text before these edits is in the text after them. */
    private fun List<Edit>.map(position: Int): Int {
        var shift = 0
        for (edit in this) {
            if (position <= edit.start) break
            if (position < edit.end) return edit.start + shift + minOf(position - edit.start, edit.replacement.length)
            shift += edit.replacement.length - (edit.end - edit.start)
        }
        return position + shift
    }

    /**
     * An `http`, `https` or `www.` [url] with a query: its [host] (lowercase, without `www.`, `m.` or `music.`) and
     * its query's [parameters] as written, between [queryStart] (its `?`) and [fragmentStart] (its `#`, or the end).
     */
    private class WebLink(
        private val url: String,
        private val queryStart: Int,
        private val fragmentStart: Int,
    ) {
        val parameters: List<String> = url.substring(queryStart + 1, fragmentStart).split('&')

        val host: String =
            url
                .substringAfter("://", url)
                .takeWhile { it != '/' && it != '?' && it != '#' && it != ':' }
                .lowercase()
                .let { host -> HOST_PREFIXES.fold(host) { name, prefix -> name.removePrefix(prefix) } }

        /** The link with only [kept] in its query, and no `?` when none is left. */
        fun withParameters(kept: List<String>): String {
            val query = kept.filter { it.isNotEmpty() }.joinToString("&")
            return buildString {
                append(url, 0, queryStart)
                if (query.isNotEmpty()) append('?').append(query)
                append(url, fragmentStart, url.length)
            }
        }

        companion object {
            /** [url] as a [WebLink]; null for another scheme or a link without a query. */
            fun of(url: String): WebLink? {
                val web = WEB_SCHEMES.any { url.startsWith(it, ignoreCase = true) }
                val fragmentStart = url.indexOf('#').takeIf { it >= 0 } ?: url.length
                val queryStart = url.indexOf('?').takeIf { it in 0 until fragmentStart }
                return if (web && queryStart != null) WebLink(url, queryStart, fragmentStart) else null
            }
        }
    }

    /** [replacement] in place of the text from [start] up to [end]. */
    private class Edit(
        val start: Int,
        val end: Int,
        val replacement: String,
    )

    private const val UTM_PREFIX = "utm_"
    private const val SHARE_ID = "si"
    private val TRACKING_PARAMETERS =
        setOf(
            "fbclid",
            "igshid",
            "igsh",
            "gclid",
            "dclid",
            "gbraid",
            "wbraid",
            "msclkid",
            "yclid",
            "mc_cid",
            "mc_eid",
            "_hsenc",
            "_hsmi",
        )
    private val SHARE_ID_HOSTS = setOf("youtube.com", "youtu.be", "spotify.com")
    private val HOST_PREFIXES = listOf("www.", "m.", "music.")
    private val WEB_SCHEMES = listOf("http://", "https://", "www.")
    private val WEB_LINK = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"]+""")
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}'\""
}

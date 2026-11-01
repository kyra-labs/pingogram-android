package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/** `**bold**`, `__italic__` and `` `code` ``: the marks of the demo's translatable texts (`res/values/strings.xml`). */
private val Marks = Regex("""\*\*(.+?)\*\*|__(.+?)__|`([^`]+)`""")

/** A link in a text, as TDLib finds them in received messages. */
private val Links = Regex("""https?://[^\s]+[^\s.,!?)]""")

/**
 * [markup] as TDLib's formatted text: its marks become bold, italic and code entities (the marks themselves dropped),
 * and its links URL entities. Offsets count UTF-16 code units, as td_api.tl's do and Kotlin strings are.
 */
@Suppress("MagicNumber") // The groups of [Marks], in its order.
internal fun formattedText(markup: String): TdApi.FormattedText {
    val text = StringBuilder()
    val entities = mutableListOf<TdApi.TextEntity>()
    var position = 0
    for (match in Marks.findAll(markup)) {
        text.append(markup, position, match.range.first)
        val (inner, type) =
            when {
                match.groups[1] != null -> match.groupValues[1] to TdApi.TextEntityTypeBold()
                match.groups[2] != null -> match.groupValues[2] to TdApi.TextEntityTypeItalic()
                else -> match.groupValues[3] to TdApi.TextEntityTypeCode()
            }
        entities += TdApi.TextEntity(text.length, inner.length, type)
        text.append(inner)
        position = match.range.last + 1
    }
    text.append(markup, position, markup.length)
    val plain = text.toString()
    Links.findAll(plain).forEach {
        entities +=
            TdApi.TextEntity(it.range.first, it.value.length, TdApi.TextEntityTypeUrl())
    }
    return TdApi.FormattedText(plain, entities.sortedBy { it.offset }.toTypedArray())
}

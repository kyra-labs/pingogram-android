package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * A rich message, as channels post them (td_api.tl `richMessage`): [blocks] of headings, paragraphs, collapsible
 * sections, photos, lists and tables, laid out right to left if [isRtl]. When [isFull] is false Telegram sent only
 * its start, and `getFullRichMessage` gives the rest (`MessageRepository.loadFullRichMessage`).
 *
 * Every text is a [FormattedText], its formatting and links as entities, as a text message's. A block Pingogram does
 * not show is [RichBlock.Unsupported], so the rest of the message still shows. `toString()` leaves the content out.
 */
data class RichMessage(
    val blocks: ImmutableList<RichBlock>,
    val isRtl: Boolean,
    val isFull: Boolean,
) {
    override fun toString() = "RichMessage(blocks=${blocks.size}, isRtl=$isRtl, isFull=$isFull)"

    /** The first text of the message (its title, or else its first heading, paragraph or quote); null if none. */
    fun firstText(): FormattedText? = blocks.firstNotNullOfOrNull { it.firstText() }

    /**
     * The text of every block, one per line, in order: what "Copy text" copies and what is translated or exported.
     * Media show only their captions, and a collapsed section's text is there too.
     */
    fun fullText(): FormattedText = FormattedTextJoiner().apply { blocks.forEach(::add) }.build()

    /** The photos, videos and GIFs of the message, in order, also those inside sections, lists, quotes and collages. */
    fun media(): ImmutableList<RichBlock.Media> =
        buildList { blocks.forEach { it.collectMedia(this) } }.toImmutableList()

    companion object {
        val Empty = RichMessage(persistentListOf(), isRtl = false, isFull = true)
    }
}

/** A part of a [RichMessage] (td_api.tl `PageBlock`). */
sealed interface RichBlock {
    /** A block made of one text, shown in a style of its own. */
    sealed interface TextBlock : RichBlock {
        val text: FormattedText
    }

    /**
     * A photo, a video or a GIF of the message; [index] is its place among the message's media
     * ([RichMessage.media]).
     */
    sealed interface Media : RichBlock {
        val index: Int
        val caption: RichCaption
        val hasSpoiler: Boolean
    }

    data class Title(
        override val text: FormattedText,
    ) : TextBlock

    data class Subtitle(
        override val text: FormattedText,
    ) : TextBlock

    data class Header(
        override val text: FormattedText,
    ) : TextBlock

    data class Subheader(
        override val text: FormattedText,
    ) : TextBlock

    /** A heading of a section; [size] 1 is the largest (td_api.tl `pageBlockSectionHeading`). */
    data class SectionHeading(
        override val text: FormattedText,
        val size: Int,
    ) : TextBlock

    /** A short line above a title. */
    data class Kicker(
        override val text: FormattedText,
    ) : TextBlock

    data class Paragraph(
        override val text: FormattedText,
    ) : TextBlock

    /** Monospace text, kept as written; [language] as the sender gave it, possibly empty. */
    data class Preformatted(
        override val text: FormattedText,
        val language: String,
    ) : TextBlock

    data class Footer(
        override val text: FormattedText,
    ) : TextBlock

    /** A formula, shown as written in monospace (Pingogram does not typeset it). */
    data class MathematicalExpression(
        val expression: String,
    ) : RichBlock

    data object Divider : RichBlock

    /** A place links of the message can point to; shows nothing. */
    data class Anchor(
        val name: String,
    ) : RichBlock

    data class ListBlock(
        val items: ImmutableList<ListItem>,
    ) : RichBlock

    /**
     * An item of a [ListBlock]: its [label] ("1.", "•", as Telegram gives it) and [blocks]; a checklist item if
     * [hasCheckbox], checked if [isChecked].
     */
    data class ListItem(
        val label: String,
        val blocks: ImmutableList<RichBlock>,
        val hasCheckbox: Boolean,
        val isChecked: Boolean,
    )

    data class BlockQuote(
        val blocks: ImmutableList<RichBlock>,
        val credit: FormattedText,
    ) : RichBlock

    /** A quote collapsed to a few lines until opened. */
    data class ExpandableBlockQuote(
        override val text: FormattedText,
        val credit: FormattedText,
    ) : TextBlock

    /** A quote set apart, larger. */
    data class PullQuote(
        override val text: FormattedText,
        val credit: FormattedText,
    ) : TextBlock

    /**
     * A collapsible section: [header] over [blocks], open at first if [isOpen]. [id] tells the message's sections
     * apart (their order, depth first), so which ones the reader opened can be kept.
     */
    data class Details(
        val id: Int,
        val header: FormattedText,
        val blocks: ImmutableList<RichBlock>,
        val isOpen: Boolean,
    ) : RichBlock

    /** [url]: where tapping the photo leads instead of the media viewer; empty if nowhere. */
    data class Photo(
        override val index: Int,
        val photo: dev.kyralabs.pingogram.core.domain.model.Photo,
        override val caption: RichCaption,
        val url: String,
        override val hasSpoiler: Boolean,
    ) : Media

    data class Video(
        override val index: Int,
        val video: dev.kyralabs.pingogram.core.domain.model.Video,
        override val caption: RichCaption,
        override val hasSpoiler: Boolean,
    ) : Media

    data class Animation(
        override val index: Int,
        val animation: dev.kyralabs.pingogram.core.domain.model.Animation,
        override val caption: RichCaption,
        override val hasSpoiler: Boolean,
    ) : Media

    /** Photos and videos side by side. */
    data class Collage(
        val blocks: ImmutableList<RichBlock>,
        val caption: RichCaption,
    ) : RichBlock

    /** Photos and videos one after another. */
    data class Slideshow(
        val blocks: ImmutableList<RichBlock>,
        val caption: RichCaption,
    ) : RichBlock

    /** Rows of [TableCell]s; [caption] may be empty. */
    data class Table(
        val caption: FormattedText,
        val rows: ImmutableList<ImmutableList<TableCell>>,
        val isBordered: Boolean,
        val isStriped: Boolean,
    ) : RichBlock

    /** Buttons in a row, as a bot's inline keyboard has them. */
    data class ButtonRow(
        val buttons: ImmutableList<InlineKeyboardButton>,
    ) : RichBlock

    /** A block Pingogram cannot show yet (a map, an embedded page, a file, …): a short note takes its place. */
    data object Unsupported : RichBlock
}

/** The caption under a media block or a collage: its [text] and who to [credit], either possibly empty. */
data class RichCaption(
    val text: FormattedText,
    val credit: FormattedText,
) {
    val isEmpty: Boolean get() = text.text.isBlank() && credit.text.isBlank()

    companion object {
        val Empty = RichCaption(FormattedText.Empty, FormattedText.Empty)
    }
}

/** A cell of a [RichBlock.Table], spanning [colspan] columns and [rowspan] rows (at least 1 each). */
data class TableCell(
    val text: FormattedText,
    val isHeader: Boolean,
    val colspan: Int,
    val rowspan: Int,
    val align: TableCellAlignment,
)

enum class TableCellAlignment {
    Start,
    Center,
    End,
}

/** The first text of this block, depth first; null if it has none. */
private fun RichBlock.firstText(): FormattedText? =
    when (this) {
        is RichBlock.TextBlock -> {
            text.takeIf { it.text.isNotBlank() }
        }

        is RichBlock.Details -> {
            header.takeIf { it.text.isNotBlank() } ?: blocks.firstNotNullOfOrNull { it.firstText() }
        }

        is RichBlock.BlockQuote -> {
            blocks.firstNotNullOfOrNull { it.firstText() }
        }

        is RichBlock.ListBlock -> {
            items.firstNotNullOfOrNull { item ->
                item.blocks.firstNotNullOfOrNull { it.firstText() }
            }
        }

        else -> {
            null
        }
    }

private fun RichBlock.collectMedia(into: MutableList<RichBlock.Media>) {
    when (this) {
        is RichBlock.Media -> into += this
        is RichBlock.Details -> blocks.forEach { it.collectMedia(into) }
        is RichBlock.BlockQuote -> blocks.forEach { it.collectMedia(into) }
        is RichBlock.ListBlock -> items.forEach { item -> item.blocks.forEach { it.collectMedia(into) } }
        is RichBlock.Collage -> blocks.forEach { it.collectMedia(into) }
        is RichBlock.Slideshow -> blocks.forEach { it.collectMedia(into) }
        else -> Unit
    }
}

/** Joins the texts of blocks into one [FormattedText], one per line, their entities moved along. */
private class FormattedTextJoiner {
    private val text = StringBuilder()
    private val entities = mutableListOf<TextEntity>()

    fun add(block: RichBlock) {
        when (block) {
            is RichBlock.TextBlock -> {
                line(block.text)
                if (block is RichBlock.ExpandableBlockQuote) line(block.credit)
                if (block is RichBlock.PullQuote) line(block.credit)
            }

            is RichBlock.MathematicalExpression -> {
                line(FormattedText(block.expression, persistentListOf()))
            }

            is RichBlock.Details -> {
                line(block.header)
                block.blocks.forEach(::add)
            }

            is RichBlock.BlockQuote -> {
                block.blocks.forEach(::add)
                line(block.credit)
            }

            is RichBlock.ListBlock -> {
                block.items.forEach { item ->
                    val start = text.length
                    item.blocks.forEach(::add)
                    // The label goes before the item's first line, after the line break that starts it.
                    if (item.label.isNotBlank() && text.length > start) {
                        insert(if (start == 0) 0 else start + 1, "${item.label} ")
                    }
                }
            }

            is RichBlock.Media, is RichBlock.Collage, is RichBlock.Slideshow, is RichBlock.Table -> {
                addLaidOut(block)
            }

            is RichBlock.ButtonRow,
            is RichBlock.Anchor,
            RichBlock.Divider,
            RichBlock.Unsupported,
            -> {
                Unit
            }
        }
    }

    /** Media with their captions, collages and slideshows with their media's, and tables cell by cell. */
    private fun addLaidOut(block: RichBlock) {
        when (block) {
            is RichBlock.Collage -> block.blocks.forEach(::add).also { caption(block.caption) }
            is RichBlock.Slideshow -> block.blocks.forEach(::add).also { caption(block.caption) }
            is RichBlock.Media -> caption(block.caption)
            is RichBlock.Table -> table(block)
            else -> Unit
        }
    }

    /** The table's caption, then each row on a line of its own, its cells separated by tabs. */
    private fun table(table: RichBlock.Table) {
        line(table.caption)
        table.rows.forEach { row ->
            row.map { it.text }.filter { it.text.isNotBlank() }.forEachIndexed { index, cell ->
                if (index == 0) line(cell) else append(cell, separator = "\t")
            }
        }
    }

    private fun caption(caption: RichCaption) {
        line(caption.text)
        line(caption.credit)
    }

    /** [part] on a line of its own; nothing if it is blank. */
    private fun line(part: FormattedText) = append(part, separator = "\n")

    private fun append(
        part: FormattedText,
        separator: String,
    ) {
        if (part.text.isBlank()) return
        if (text.isNotEmpty()) text.append(separator)
        val offset = text.length
        text.append(part.text)
        part.entities.mapTo(entities) { it.copy(offset = it.offset + offset) }
    }

    /** Inserts [prefix] at [at], moving the entities after it. */
    private fun insert(
        at: Int,
        prefix: String,
    ) {
        text.insert(at, prefix)
        entities.replaceAll { if (it.offset >= at) it.copy(offset = it.offset + prefix.length) else it }
    }

    fun build() = FormattedText(text.toString(), entities.toImmutableList())
}

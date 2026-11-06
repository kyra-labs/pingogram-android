package dev.kyralabs.pingogram.feature.chat.ui.rich

import android.content.res.Resources
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.domain.model.RichCaption
import dev.kyralabs.pingogram.core.domain.model.RichMessage
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.RichMessageEvent
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What screen readers hear of a rich message in its bubble's description (`docs/DESIGN_SYSTEM.md` §9): every block's
 * text in order (a long message whole, as screen readers do not see the cut), each section as "Section Features,
 * Collapsed" with its blocks only while open, checklist items as done or not, "Table, 3 rows" before a table's cells,
 * the kind of each photo, video or GIF before its caption, and "This part can't be shown yet" for a block Pingogram
 * does not show. [spoken] turns a text into what is read (hidden spoilers read "spoiler").
 */
internal fun richDescription(
    resources: Resources,
    message: RichMessage,
    ui: RichMessageUi?,
    spoken: (FormattedText) -> String,
): List<String> =
    buildList {
        RichSpeaker(resources, ui, spoken, this).addAll(message.blocks)
    }

/**
 * The custom actions screen readers get on a rich message: "Expand Features" or "Collapse Features" for each section
 * they can reach (those not inside a closed one), and "Show full message" where Telegram sent only the start.
 */
internal fun richActions(
    resources: Resources,
    messageId: Long,
    message: RichMessage,
    ui: RichMessageUi?,
    spoken: (FormattedText) -> String,
): List<Pair<String, ChatEvent>> =
    buildList {
        fun visit(blocks: List<RichBlock>) {
            blocks.forEach { block ->
                when (block) {
                    is RichBlock.Details -> {
                        val open = ui.isOpen(block)
                        val label = if (open) R.string.rich_section_collapse else R.string.rich_section_expand
                        add(
                            resources.getString(label, spoken(block.header)) to
                                RichMessageEvent.ToggleSection(messageId, block.id),
                        )
                        if (open) visit(block.blocks)
                    }

                    is RichBlock.BlockQuote -> {
                        visit(block.blocks)
                    }

                    is RichBlock.ListBlock -> {
                        block.items.forEach { visit(it.blocks) }
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
        visit(message.blocks)
        if (!message.isFull && ui?.isLoadingFull != true) {
            add(resources.getString(R.string.rich_show_full) to RichMessageEvent.LoadFull(messageId))
        }
    }

/** Walks a message's blocks into the parts of its description. */
private class RichSpeaker(
    private val resources: Resources,
    private val ui: RichMessageUi?,
    private val spoken: (FormattedText) -> String,
    private val parts: MutableList<String>,
) {
    fun addAll(blocks: List<RichBlock>) = blocks.forEach(::add)

    private fun text(text: FormattedText) {
        if (text.text.isNotBlank()) parts += spoken(text)
    }

    private fun add(block: RichBlock) {
        when (block) {
            is RichBlock.TextBlock -> {
                text(block.text)
                if (block is RichBlock.PullQuote) text(block.credit)
                if (block is RichBlock.ExpandableBlockQuote) text(block.credit)
            }

            is RichBlock.MathematicalExpression -> {
                parts += block.expression
            }

            is RichBlock.Details -> {
                details(block)
            }

            is RichBlock.BlockQuote -> {
                addAll(block.blocks).also { text(block.credit) }
            }

            is RichBlock.ListBlock -> {
                block.items.forEach(::item)
            }

            is RichBlock.Media -> {
                media(block)
            }

            is RichBlock.Collage -> {
                addAll(block.blocks).also { caption(block.caption) }
            }

            is RichBlock.Slideshow -> {
                addAll(block.blocks).also { caption(block.caption) }
            }

            is RichBlock.Table -> {
                table(block)
            }

            is RichBlock.ButtonRow -> {
                block.buttons.forEach { parts += it.text }
            }

            RichBlock.Unsupported -> {
                parts += resources.getString(R.string.rich_unsupported_block)
            }

            RichBlock.Divider, is RichBlock.Anchor -> {
                Unit
            }
        }
    }

    private fun details(block: RichBlock.Details) {
        val open = ui.isOpen(block)
        val state = resources.getString(if (open) R.string.rich_section_expanded else R.string.rich_section_collapsed)
        parts += resources.getString(R.string.rich_section_description, spoken(block.header), state)
        if (open) addAll(block.blocks)
    }

    private fun item(item: RichBlock.ListItem) {
        val start = parts.size
        addAll(item.blocks)
        if (item.hasCheckbox && parts.size > start) {
            val label = if (item.isChecked) R.string.rich_item_checked else R.string.rich_item_unchecked
            parts[start] = resources.getString(label, parts[start])
        }
    }

    private fun media(block: RichBlock.Media) {
        val kind =
            when (block) {
                is RichBlock.Photo -> UiR.string.preview_photo
                is RichBlock.Video -> UiR.string.preview_video
                is RichBlock.Animation -> UiR.string.preview_gif
            }
        parts += resources.getString(kind)
        caption(block.caption)
    }

    private fun caption(caption: RichCaption) {
        text(caption.text)
        text(caption.credit)
    }

    private fun table(block: RichBlock.Table) {
        text(block.caption)
        parts += resources.getQuantityString(R.plurals.rich_table_rows, block.rows.size, block.rows.size)
        block.rows.forEach { row -> row.forEach { text(it.text) } }
    }
}

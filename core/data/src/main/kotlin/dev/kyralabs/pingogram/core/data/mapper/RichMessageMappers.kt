package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButton
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButtonType
import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.domain.model.RichCaption
import dev.kyralabs.pingogram.core.domain.model.RichMessage
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

// Rich messages (td_api.tl richMessage, PageBlock, RichText): the blocks Pingogram shows, their texts as FormattedText
// with entities, their media as the photos, videos and GIFs of other messages.

/**
 * This rich message's blocks, in order. Sections are numbered depth first and media in the order they appear
 * ([RichBlock.Details.id], [RichBlock.Media.index]); blocks Pingogram does not show are [RichBlock.Unsupported].
 */
internal fun TdApi.RichMessage.toRichMessage(): RichMessage {
    val numbers = BlockNumbers()
    return RichMessage(blocks.orEmpty().mapBlocks(numbers), isRtl, isFull)
}

/** Counters for the sections and media of one message, as its blocks are mapped in order. */
private class BlockNumbers {
    var details = 0
    var media = 0
}

private fun Array<out TdApi.PageBlock?>.mapBlocks(numbers: BlockNumbers): ImmutableList<RichBlock> =
    mapNotNull { it?.toRichBlock(numbers) }.toImmutableList()

private fun TdApi.PageBlock.toRichBlock(numbers: BlockNumbers): RichBlock =
    textBlock() ?: mediaBlock(numbers) ?: structureBlock(numbers) ?: RichBlock.Unsupported

/** A block of one text, a divider or an anchor; null for any other block. */
private fun TdApi.PageBlock.textBlock(): RichBlock? =
    when (this) {
        is TdApi.PageBlockTitle -> RichBlock.Title(title.toFormattedText())
        is TdApi.PageBlockSubtitle -> RichBlock.Subtitle(subtitle.toFormattedText())
        is TdApi.PageBlockHeader -> RichBlock.Header(header.toFormattedText())
        is TdApi.PageBlockSubheader -> RichBlock.Subheader(subheader.toFormattedText())
        is TdApi.PageBlockSectionHeading -> RichBlock.SectionHeading(text.toFormattedText(), size)
        is TdApi.PageBlockKicker -> RichBlock.Kicker(kicker.toFormattedText())
        is TdApi.PageBlockParagraph -> RichBlock.Paragraph(text.toFormattedText())
        is TdApi.PageBlockPreformatted -> RichBlock.Preformatted(text.toFormattedText(), language.orEmpty())
        is TdApi.PageBlockFooter -> RichBlock.Footer(footer.toFormattedText())
        is TdApi.PageBlockMathematicalExpression -> RichBlock.MathematicalExpression(expression.orEmpty())
        is TdApi.PageBlockPullQuote -> RichBlock.PullQuote(text.toFormattedText(), credit.toFormattedText())
        is TdApi.PageBlockDivider -> RichBlock.Divider
        is TdApi.PageBlockAnchor -> RichBlock.Anchor(name.orEmpty())
        else -> null
    }

/** A photo, a video or a GIF, numbered among the message's media; null for other blocks and one without its media. */
private fun TdApi.PageBlock.mediaBlock(numbers: BlockNumbers): RichBlock? =
    when (this) {
        is TdApi.PageBlockPhoto -> {
            photo?.let {
                RichBlock.Photo(numbers.media++, it.toPhoto(), caption.toRichCaption(), url.orEmpty(), hasSpoiler)
            }
        }

        is TdApi.PageBlockVideo -> {
            video?.let { RichBlock.Video(numbers.media++, it.toVideo(), caption.toRichCaption(), hasSpoiler) }
        }

        is TdApi.PageBlockAnimation -> {
            animation?.let {
                RichBlock.Animation(numbers.media++, it.toAnimation(), caption.toRichCaption(), hasSpoiler)
            }
        }

        else -> {
            null
        }
    }

/**
 * A block that holds others or is laid out of parts: lists, quotes, sections, collages, slideshows, tables and rows of
 * buttons; null for any other block, and for those Pingogram does not show (author and date, maps, embedded pages and
 * posts, related articles, chat links, audio, voice notes, files, an AI's thinking, blocks a newer TDLib adds).
 */
private fun TdApi.PageBlock.structureBlock(numbers: BlockNumbers): RichBlock? =
    when (this) {
        is TdApi.PageBlockList -> {
            val listItems =
                items.orEmpty().mapNotNull { item ->
                    item?.run {
                        RichBlock.ListItem(label.orEmpty(), blocks.orEmpty().mapBlocks(numbers), hasCheckbox, isChecked)
                    }
                }
            RichBlock.ListBlock(listItems.toImmutableList())
        }

        is TdApi.PageBlockBlockQuote -> {
            RichBlock.BlockQuote(blocks.orEmpty().mapBlocks(numbers), credit.toFormattedText())
        }

        is TdApi.PageBlockExpandableBlockQuote -> {
            RichBlock.ExpandableBlockQuote(text.toFormattedText(), credit.toFormattedText())
        }

        is TdApi.PageBlockDetails -> {
            details(numbers)
        }

        is TdApi.PageBlockCollage -> {
            RichBlock.Collage(blocks.orEmpty().mapBlocks(numbers), caption.toRichCaption())
        }

        is TdApi.PageBlockSlideshow -> {
            RichBlock.Slideshow(blocks.orEmpty().mapBlocks(numbers), caption.toRichCaption())
        }

        is TdApi.PageBlockTable -> {
            table()
        }

        is TdApi.PageBlockButtonRow -> {
            buttonRow()
        }

        // A cover is a photo or video shown first: the block it holds.
        is TdApi.PageBlockCover -> {
            cover?.toRichBlock(numbers)
        }

        else -> {
            null
        }
    }

/** Numbered before the blocks it holds, so a section comes before the sections inside it. */
private fun TdApi.PageBlockDetails.details(numbers: BlockNumbers): RichBlock.Details {
    val id = numbers.details++
    return RichBlock.Details(id, header.toFormattedText(), blocks.orEmpty().mapBlocks(numbers), isOpen)
}

private fun TdApi.PageBlockCaption?.toRichCaption(): RichCaption =
    if (this == null) RichCaption.Empty else RichCaption(text.toFormattedText(), credit.toFormattedText())

/** The row's buttons as a bot's inline keyboard has them; null (unsupported) if it has none. */
private fun TdApi.PageBlockButtonRow.buttonRow(): RichBlock.ButtonRow? =
    buttons
        .orEmpty()
        .mapNotNull { button ->
            button?.let {
                InlineKeyboardButton(
                    it.text.toFormattedText().text,
                    it.style.toBotButtonStyle(),
                    it.type.toButtonType(),
                )
            }
        }.takeIf { it.isNotEmpty() }
        ?.let { RichBlock.ButtonRow(it.toImmutableList()) }

package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.BotButtonStyle
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButton
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButtonType
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.domain.model.RichCaption
import dev.kyralabs.pingogram.core.domain.model.TableCellAlignment
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RichMessageMappersTest {
    private fun plain(text: String) = TdApi.RichTextPlain(text)

    private fun text(
        text: String,
        vararg entities: TextEntity,
    ) = FormattedText(text, entities.toList().toImmutableList())

    private fun blocks(vararg blocks: TdApi.PageBlock) =
        TdApi.RichMessage(arrayOf(*blocks), false, true).toRichMessage().blocks

    private fun block(block: TdApi.PageBlock) = blocks(block).single()

    @Nested
    inner class Message {
        @Test
        fun `a rich message becomes rich message content with its direction and whether it is full`() {
            val content =
                TdApi
                    .MessageRichMessage(TdApi.RichMessage(arrayOf(TdApi.PageBlockParagraph(plain("Hi"))), true, false))
                    .toMessageContent(sender = null, isChannelPost = true)

            val message = (content as MessageContent.RichMessage).message
            assertEquals(listOf(RichBlock.Paragraph(text("Hi"))), message.blocks)
            assertTrue(message.isRtl)
            assertFalse(message.isFull)
        }

        @Test
        fun `blocks Pingogram does not show become unsupported, keeping their place`() {
            val map = TdApi.PageBlockMap(TdApi.Location(1.0, 2.0, 0.0), 10, 100, 100, null)

            val mapped =
                blocks(
                    TdApi.PageBlockTitle(plain("A")),
                    map,
                    TdApi.PageBlockUnsupported(),
                    TdApi.PageBlockFooter(plain("Z")),
                )

            assertEquals(
                listOf(
                    RichBlock.Title(text("A")),
                    RichBlock.Unsupported,
                    RichBlock.Unsupported,
                    RichBlock.Footer(text("Z")),
                ),
                mapped,
            )
        }
    }

    @Nested
    inner class TextBlocks {
        @Test
        fun `each text block keeps its kind`() {
            val mapped =
                blocks(
                    TdApi.PageBlockTitle(plain("Title")),
                    TdApi.PageBlockSubtitle(plain("Subtitle")),
                    TdApi.PageBlockHeader(plain("Header")),
                    TdApi.PageBlockSubheader(plain("Subheader")),
                    TdApi.PageBlockSectionHeading(plain("Section"), 2),
                    TdApi.PageBlockKicker(plain("Kicker")),
                    TdApi.PageBlockParagraph(plain("Paragraph")),
                    TdApi.PageBlockPreformatted(plain("val x = 1"), "kotlin"),
                    TdApi.PageBlockFooter(plain("Footer")),
                    TdApi.PageBlockMathematicalExpression("e = mc^2"),
                    TdApi.PageBlockDivider(),
                    TdApi.PageBlockAnchor("top"),
                )

            assertEquals(
                listOf(
                    RichBlock.Title(text("Title")),
                    RichBlock.Subtitle(text("Subtitle")),
                    RichBlock.Header(text("Header")),
                    RichBlock.Subheader(text("Subheader")),
                    RichBlock.SectionHeading(text("Section"), 2),
                    RichBlock.Kicker(text("Kicker")),
                    RichBlock.Paragraph(text("Paragraph")),
                    RichBlock.Preformatted(text("val x = 1"), "kotlin"),
                    RichBlock.Footer(text("Footer")),
                    RichBlock.MathematicalExpression("e = mc^2"),
                    RichBlock.Divider,
                    RichBlock.Anchor("top"),
                ),
                mapped,
            )
        }

        @Test
        fun `quotes keep their credit`() {
            val mapped =
                blocks(
                    TdApi.PageBlockBlockQuote(arrayOf(TdApi.PageBlockParagraph(plain("Quoted"))), plain("Ada")),
                    TdApi.PageBlockExpandableBlockQuote(plain("Long"), plain("Grace")),
                    TdApi.PageBlockPullQuote(plain("Pulled"), null),
                )

            assertEquals(
                listOf(
                    RichBlock.BlockQuote(persistentListOf(RichBlock.Paragraph(text("Quoted"))), text("Ada")),
                    RichBlock.ExpandableBlockQuote(text("Long"), text("Grace")),
                    RichBlock.PullQuote(text("Pulled"), FormattedText.Empty),
                ),
                mapped,
            )
        }
    }

    @Nested
    inner class Structure {
        @Test
        fun `a list keeps its items' labels, blocks and checkboxes`() {
            val list =
                TdApi.PageBlockList(
                    arrayOf(
                        TdApi.PageBlockListItem(
                            "1.",
                            arrayOf(TdApi.PageBlockParagraph(plain("One"))),
                            false,
                            false,
                            1,
                            "1",
                        ),
                        TdApi.PageBlockListItem(
                            "",
                            arrayOf(TdApi.PageBlockParagraph(plain("Done"))),
                            true,
                            true,
                            0,
                            "",
                        ),
                    ),
                )

            assertEquals(
                RichBlock.ListBlock(
                    persistentListOf(
                        RichBlock.ListItem("1.", persistentListOf(RichBlock.Paragraph(text("One"))), false, false),
                        RichBlock.ListItem("", persistentListOf(RichBlock.Paragraph(text("Done"))), true, true),
                    ),
                ),
                block(list),
            )
        }

        @Test
        fun `sections are numbered depth first, a section before those inside it`() {
            val inner = TdApi.PageBlockDetails(plain("Inner"), arrayOf(TdApi.PageBlockParagraph(plain("Deep"))), false)
            val outer = TdApi.PageBlockDetails(plain("Links"), arrayOf(inner), true)
            val second = TdApi.PageBlockDetails(plain("Features"), emptyArray(), false)

            val mapped = blocks(outer, second)

            val outerDetails = mapped[0] as RichBlock.Details
            assertEquals(0, outerDetails.id)
            assertEquals(text("Links"), outerDetails.header)
            assertTrue(outerDetails.isOpen)
            assertEquals(1, (outerDetails.blocks.single() as RichBlock.Details).id)
            assertEquals(RichBlock.Details(2, text("Features"), persistentListOf(), isOpen = false), mapped[1])
        }

        @Test
        fun `a table keeps its cells, their spans and alignment, and its look`() {
            fun cell(
                value: String,
                isHeader: Boolean,
                colspan: Int,
                align: TdApi.PageBlockHorizontalAlignment,
            ) = TdApi.PageBlockTableCell(
                plain(value),
                isHeader,
                colspan,
                0,
                align,
                TdApi.PageBlockVerticalAlignmentTop(),
            )
            val header =
                arrayOf(
                    cell("Plan", isHeader = true, colspan = 1, align = TdApi.PageBlockHorizontalAlignmentLeft()),
                    cell("Cost", isHeader = true, colspan = 2, align = TdApi.PageBlockHorizontalAlignmentRight()),
                )
            val row =
                arrayOf(cell("Free", isHeader = false, colspan = 1, align = TdApi.PageBlockHorizontalAlignmentCenter()))
            val table = TdApi.PageBlockTable(plain("Prices"), arrayOf(header, emptyArray(), row), true, false, false)

            val mapped = block(table) as RichBlock.Table

            assertEquals(text("Prices"), mapped.caption)
            assertEquals(
                listOf(listOf("Plan", "Cost"), listOf("Free")),
                mapped.rows.map { row ->
                    row.map { it.text.text }
                },
            )
            val cells = mapped.rows.flatten()
            assertEquals(listOf(true, true, false), cells.map { it.isHeader })
            assertEquals(listOf(1, 2, 1), cells.map { it.colspan })
            assertEquals(listOf(1, 1, 1), cells.map { it.rowspan })
            assertEquals(
                listOf(TableCellAlignment.Start, TableCellAlignment.End, TableCellAlignment.Center),
                cells.map { it.align },
            )
            assertTrue(mapped.isBordered)
            assertFalse(mapped.isStriped)
        }

        @Test
        fun `a row of buttons maps them as an inline keyboard's`() {
            val row =
                TdApi.PageBlockButtonRow(
                    arrayOf(
                        TdApi.InlineButton(
                            plain("Site"),
                            null,
                            TdApi.InlineKeyboardButtonTypeUrl("https://example.org"),
                        ),
                    ),
                    TdApi.PageBlockHorizontalAlignmentCenter(),
                )

            assertEquals(
                RichBlock.ButtonRow(
                    persistentListOf(
                        InlineKeyboardButton(
                            "Site",
                            BotButtonStyle.Default,
                            InlineKeyboardButtonType.Url("https://example.org"),
                        ),
                    ),
                ),
                block(row),
            )
        }

        @Test
        fun `a cover is the block it holds`() {
            assertEquals(
                RichBlock.Paragraph(text("Cover")),
                block(TdApi.PageBlockCover(TdApi.PageBlockParagraph(plain("Cover")))),
            )
        }
    }

    @Nested
    inner class Media {
        @Test
        fun `photos, videos and GIFs map their media as other messages do, numbered in order`() {
            val caption = TdApi.PageBlockCaption(plain("Home screen"), plain("Ada"))
            val mapped =
                blocks(
                    TdApi.PageBlockPhoto(TdApiFixtures.photo(), caption, "https://example.org", true),
                    TdApi.PageBlockDetails(
                        plain("Screenshots"),
                        arrayOf(TdApi.PageBlockVideo(TdApiFixtures.video(), null, false, false, false)),
                        false,
                    ),
                    TdApi.PageBlockAnimation(TdApiFixtures.animation(), null, true, false),
                )

            assertEquals(
                RichBlock.Photo(
                    0,
                    TdApiFixtures.photo().toPhoto(),
                    RichCaption(text("Home screen"), text("Ada")),
                    "https://example.org",
                    true,
                ),
                mapped[0],
            )
            assertEquals(
                RichBlock.Video(1, TdApiFixtures.video().toVideo(), RichCaption.Empty, false),
                (mapped[1] as RichBlock.Details).blocks.single(),
            )
            assertEquals(
                RichBlock.Animation(2, TdApiFixtures.animation().toAnimation(), RichCaption.Empty, false),
                mapped[2],
            )
        }

        @Test
        fun `collages and slideshows hold their media`() {
            val photo = TdApi.PageBlockPhoto(TdApiFixtures.photo(), null, "", false)

            val mapped =
                blocks(
                    TdApi.PageBlockCollage(arrayOf(photo, photo), null),
                    TdApi.PageBlockSlideshow(arrayOf(photo), TdApi.PageBlockCaption(plain("Trip"), null)),
                )

            val collage = mapped[0] as RichBlock.Collage
            assertEquals(listOf(0, 1), collage.blocks.map { (it as RichBlock.Photo).index })
            val slideshow = mapped[1] as RichBlock.Slideshow
            assertEquals(2, (slideshow.blocks.single() as RichBlock.Photo).index)
            assertEquals(RichCaption(text("Trip"), FormattedText.Empty), slideshow.caption)
        }

        @Test
        fun `a photo without its photo is unsupported`() {
            assertEquals(RichBlock.Unsupported, block(TdApi.PageBlockPhoto(null, null, "", false)))
        }
    }

    @Nested
    inner class RichTexts {
        private fun mapped(rich: TdApi.RichText) = rich.toFormattedText()

        @Test
        fun `formatting becomes entities over the parts it covers`() {
            val rich =
                TdApi.RichTexts(
                    arrayOf(
                        plain("A "),
                        TdApi.RichTextBold(TdApi.RichTextItalic(plain("bold"))),
                        plain(" "),
                        TdApi.RichTextUnderline(plain("u")),
                        TdApi.RichTextStrikethrough(plain("s")),
                        TdApi.RichTextFixed(plain("c")),
                        TdApi.RichTextSpoiler(plain("x")),
                        TdApi.RichTextMarked(plain("m")),
                        TdApi.RichTextSubscript(plain("2")),
                        TdApi.RichTextSuperscript(plain("3")),
                    ),
                )

            assertEquals(
                text(
                    "A bold usc" + "xm23",
                    TextEntity(2, 4, TextEntityType.Bold),
                    TextEntity(2, 4, TextEntityType.Italic),
                    TextEntity(7, 1, TextEntityType.Underline),
                    TextEntity(8, 1, TextEntityType.Strikethrough),
                    TextEntity(9, 1, TextEntityType.Code),
                    TextEntity(10, 1, TextEntityType.Spoiler),
                    TextEntity(11, 1, TextEntityType.Marked),
                    TextEntity(12, 1, TextEntityType.Subscript),
                    TextEntity(13, 1, TextEntityType.Superscript),
                ),
                mapped(rich),
            )
        }

        @Test
        fun `links, anchor links and references open their URL, and are plain without one`() {
            val rich =
                TdApi.RichTexts(
                    arrayOf(
                        TdApi.RichTextUrl(plain("site"), "https://example.org", false),
                        TdApi.RichTextAnchorLink(plain("up"), "top", ""),
                        TdApi.RichTextReferenceLink(plain("[1]"), "ref", "https://example.org/1"),
                    ),
                )

            assertEquals(
                text(
                    "siteup[1]",
                    TextEntity(0, 4, TextEntityType.TextUrl("https://example.org")),
                    TextEntity(6, 3, TextEntityType.TextUrl("https://example.org/1")),
                ),
                mapped(rich),
            )
        }

        @Test
        fun `mentions, hashtags, cashtags, bot commands, emails and phone numbers keep their kind`() {
            val rich =
                TdApi.RichTexts(
                    arrayOf(
                        TdApi.RichTextMention(plain("@ada"), "ada"),
                        TdApi.RichTextMention(plain("Ada Lovelace"), "ada"),
                        TdApi.RichTextMentionName(plain("Grace"), 42),
                        TdApi.RichTextHashtag(plain("#news"), "news"),
                        TdApi.RichTextCashtag(plain("\$USD"), "USD"),
                        TdApi.RichTextBotCommand(plain("/start"), "/start"),
                        TdApi.RichTextEmailAddress(plain("a@b.c"), "a@b.c"),
                        TdApi.RichTextPhoneNumber(plain("call"), "+441234"),
                    ),
                )

            val entities = mapped(rich).entities.map { it.type }

            assertEquals(
                listOf(
                    TextEntityType.Mention,
                    TextEntityType.TextUrl("https://t.me/ada"),
                    TextEntityType.MentionName(42),
                    TextEntityType.Hashtag,
                    TextEntityType.Cashtag,
                    TextEntityType.BotCommand,
                    TextEntityType.EmailAddress,
                    TextEntityType.TextUrl("tel:+441234"),
                ),
                entities,
            )
        }

        @Test
        fun `custom emoji keep their emoji, dates their time, and formulas show as code`() {
            val rich =
                TdApi.RichTexts(
                    arrayOf(
                        TdApi.RichTextCustomEmoji(7, "🙂"),
                        TdApi.RichTextCustomEmoji(8, ""),
                        TdApi.RichTextDateTime(plain("today"), 1_700_000_000, TdApi.DateTimeFormattingTypeRelative()),
                        TdApi.RichTextMathematicalExpression("x^2"),
                    ),
                )

            assertEquals(
                text(
                    "🙂todayx^2",
                    TextEntity(0, 2, TextEntityType.CustomEmoji(7)),
                    TextEntity(
                        2,
                        5,
                        TextEntityType.DateTime(
                            1_700_000_000,
                            dev.kyralabs.pingogram.core.domain.model.DateTimeFormat.Relative,
                        ),
                    ),
                    TextEntity(7, 3, TextEntityType.Code),
                ),
                mapped(rich),
            )
        }

        @Test
        fun `a changed text shows as it is now, and anchors and icons add nothing`() {
            val rich =
                TdApi.RichTexts(
                    arrayOf(
                        TdApi.RichTextDiff(plain("new"), plain("old")),
                        TdApi.RichTextAnchor("here"),
                        TdApi.RichTextIcon(null, 16, 16),
                        TdApi.RichTextReference("note", plain("!")),
                    ),
                )

            assertEquals(text("new!"), mapped(rich))
        }

        @Test
        fun `a link button inside a text links its words`() {
            val rich =
                TdApi.RichTextButton(
                    TdApi.InlineButton(plain("Open"), null, TdApi.InlineKeyboardButtonTypeUrl("https://example.org")),
                )

            assertEquals(text("Open", TextEntity(0, 4, TextEntityType.TextUrl("https://example.org"))), mapped(rich))
        }

        @Test
        fun `no text is empty`() {
            assertEquals(FormattedText.Empty, (null as TdApi.RichText?).toFormattedText())
        }
    }
}

package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RichMessageModelTest {
    private fun text(
        text: String,
        vararg entities: TextEntity,
    ) = FormattedText(text, entities.toList().toImmutableList())

    private fun message(vararg blocks: RichBlock) =
        RichMessage(blocks.toList().toImmutableList(), isRtl = false, isFull = true)

    private val photo = Photo(persistentListOf(), null)

    @Test
    fun `the first text is the first block with text, depth first`() {
        val inSection =
            message(
                RichBlock.Divider,
                RichBlock.Details(0, text(""), persistentListOf(RichBlock.Paragraph(text("Inside"))), isOpen = false),
                RichBlock.Title(text("Later")),
            )

        assertEquals(text("Inside"), inSection.firstText())
        assertEquals(
            text("Title"),
            message(RichBlock.Title(text("Title")), RichBlock.Paragraph(text("Body"))).firstText(),
        )
        assertNull(message(RichBlock.Divider, RichBlock.Unsupported).firstText())
    }

    @Test
    fun `the full text has every block's text on its own line, entities moved along`() {
        val bold = text("Bold", TextEntity(0, 4, TextEntityType.Bold))
        val rich =
            message(
                RichBlock.Title(text("Supernova")),
                RichBlock.Details(
                    0,
                    text("Features"),
                    persistentListOf(
                        RichBlock.ListBlock(
                            persistentListOf(
                                RichBlock.ListItem("•", persistentListOf(RichBlock.Paragraph(bold)), false, false),
                                RichBlock.ListItem(
                                    "•",
                                    persistentListOf(RichBlock.Paragraph(text("Fast"))),
                                    false,
                                    false,
                                ),
                            ),
                        ),
                    ),
                    isOpen = false,
                ),
                RichBlock.Photo(0, photo, RichCaption(text("Home"), FormattedText.Empty), "", hasSpoiler = false),
                RichBlock.Divider,
            )

        val full = rich.fullText()

        assertEquals("Supernova\nFeatures\n• Bold\n• Fast\nHome", full.text)
        assertEquals(listOf(TextEntity(21, 4, TextEntityType.Bold)), full.entities)
    }

    @Test
    fun `a table's cells are separated by tabs, a row per line`() {
        val cell = { value: String ->
            TableCell(text(value), isHeader = false, colspan = 1, rowspan = 1, align = TableCellAlignment.Start)
        }
        val table =
            RichBlock.Table(
                FormattedText.Empty,
                persistentListOf(persistentListOf(cell("a"), cell("b")), persistentListOf(cell("c"))),
                isBordered = true,
                isStriped = false,
            )

        assertEquals("a\tb\nc", message(table).fullText().text)
    }

    @Test
    fun `the media are every photo, video and GIF in order, nested ones too`() {
        val first = RichBlock.Photo(0, photo, RichCaption.Empty, "", hasSpoiler = false)
        val second = RichBlock.Photo(1, photo, RichCaption.Empty, "", hasSpoiler = false)
        val rich =
            message(
                first,
                RichBlock.Details(
                    0,
                    text("Screenshots"),
                    persistentListOf(RichBlock.Collage(persistentListOf(second), RichCaption.Empty)),
                    false,
                ),
            )

        assertEquals(listOf(first, second), rich.media())
    }
}

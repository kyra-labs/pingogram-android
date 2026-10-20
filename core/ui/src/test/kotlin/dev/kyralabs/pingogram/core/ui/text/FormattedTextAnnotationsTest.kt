package dev.kyralabs.pingogram.core.ui.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [toAnnotatedString]: every entity type, UTF-16 offsets, nesting, and entities that do not fit the text. */
class FormattedTextAnnotationsTest {
    private val styles =
        FormattedTextStyles(
            linkColor = Color.Blue,
            mentionColor = Color.Magenta,
            codeBackground = Color.LightGray,
            spoilerColor = Color.DarkGray,
            quoteColor = Color.Gray,
            boldWeight = FontWeight.SemiBold,
        )

    // Styles

    @Test
    fun `text without entities stays plain`() {
        val annotated = FormattedText("Hello", emptyList<TextEntity>().toImmutableList()).toAnnotatedString(styles)

        assertEquals(AnnotatedString("Hello"), annotated)
    }

    @Test
    fun `bold uses the emphasized weight of the style`() {
        val annotated = annotate("a bold word", "bold" to TextEntityType.Bold)

        assertEquals(listOf(Styled("bold", SpanStyle(fontWeight = FontWeight.SemiBold))), annotated.styled())
    }

    @Test
    fun `italic is a true italic`() {
        val annotated = annotate("an italic word", "italic" to TextEntityType.Italic)

        assertEquals(listOf(Styled("italic", SpanStyle(fontStyle = FontStyle.Italic))), annotated.styled())
    }

    @Test
    fun `underline and strikethrough decorate`() {
        val annotated =
            annotate(
                "under and struck",
                "under" to TextEntityType.Underline,
                "struck" to TextEntityType.Strikethrough,
            )

        assertEquals(
            listOf(
                Styled("under", SpanStyle(textDecoration = TextDecoration.Underline)),
                Styled("struck", SpanStyle(textDecoration = TextDecoration.LineThrough)),
            ),
            annotated.styled(),
        )
    }

    @Test
    fun `underline nested in strikethrough combines both decorations`() {
        val text = "one two three"
        val annotated =
            annotate(
                text,
                entity(text, "one two three", TextEntityType.Strikethrough),
                entity(text, "two", TextEntityType.Underline),
            )

        val both = TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
        assertEquals(
            listOf(
                Styled("one ", SpanStyle(textDecoration = TextDecoration.LineThrough)),
                Styled("two", SpanStyle(textDecoration = both)),
                Styled(" three", SpanStyle(textDecoration = TextDecoration.LineThrough)),
            ),
            annotated.styled(),
        )
    }

    @Test
    fun `code, pre and pre-code are monospace on the code background`() {
        val annotated =
            annotate(
                "code pre kotlin",
                "code" to TextEntityType.Code,
                "pre" to TextEntityType.Pre,
                "kotlin" to TextEntityType.PreCode("kotlin"),
            )

        val monospace = SpanStyle(fontFamily = FontFamily.Monospace, background = Color.LightGray)
        assertEquals(
            listOf(Styled("code", monospace), Styled("pre", monospace), Styled("kotlin", monospace)),
            annotated.styled(),
        )
    }

    @Test
    fun `text that draws its blocks marks code and pre instead of giving them a background`() {
        val text = "code pre kotlin"
        val annotated =
            FormattedText(
                text,
                listOf(
                    entity(text, "code", TextEntityType.Code),
                    entity(text, "pre", TextEntityType.Pre),
                    entity(text, "kotlin", TextEntityType.PreCode("kotlin")),
                ).toImmutableList(),
            ).toAnnotatedString(styles, drawsBlocks = true)

        val monospace = SpanStyle(fontFamily = FontFamily.Monospace)
        assertEquals(
            listOf(Styled("code", monospace), Styled("pre", monospace), Styled("kotlin", monospace)),
            annotated.styled(),
        )
        assertEquals(
            listOf("code" to TextBlock.CODE, "pre" to TextBlock.PRE, "kotlin" to TextBlock.PRE),
            annotated.blocks(),
        )
        assertTrue(annotated.hasTextBlocks())
    }

    @Test
    fun `block quotes are italic in the quote color`() {
        val annotated =
            annotate(
                "quote expandable",
                "quote" to TextEntityType.BlockQuote,
                "expandable" to TextEntityType.ExpandableBlockQuote,
            )

        val quote = SpanStyle(fontStyle = FontStyle.Italic, color = Color.Gray)
        assertEquals(listOf(Styled("quote", quote), Styled("expandable", quote)), annotated.styled())
    }

    @Test
    fun `mentions, hashtags, cashtags, bot commands and media timestamps are colored`() {
        val annotated =
            annotate(
                "@ada Ada #tag \$USD /start 1:02",
                "@ada" to TextEntityType.Mention,
                "Ada" to TextEntityType.MentionName(userId = 7),
                "#tag" to TextEntityType.Hashtag,
                "\$USD" to TextEntityType.Cashtag,
                "/start" to TextEntityType.BotCommand,
                "1:02" to TextEntityType.MediaTimestamp(seconds = 62),
            )

        val mention = SpanStyle(color = Color.Magenta)
        assertEquals(
            listOf("@ada", "Ada", "#tag", "\$USD", "/start", "1:02").map { Styled(it, mention) },
            annotated.styled(),
        )
        assertTrue(annotated.links().isEmpty())
    }

    @Test
    fun `custom emoji, dates, bank cards and unknown entities show the text as it is`() {
        val annotated =
            annotate(
                "😀 at 5 4111 x",
                "😀" to TextEntityType.CustomEmoji(customEmojiId = 1),
                "5" to TextEntityType.DateTime(unixTime = 0, format = null),
                "4111" to TextEntityType.BankCardNumber,
                "x" to TextEntityType.Unknown,
            )

        assertEquals("😀 at 5 4111 x", annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
        assertTrue(annotated.links().isEmpty())
    }

    // Links

    @Test
    fun `a URL gets https when it has no scheme, and keeps the one it has`() {
        val annotated =
            annotate(
                "example.org and http://example.com/a",
                "example.org" to TextEntityType.Url,
                "http://example.com/a" to TextEntityType.Url,
            )

        assertEquals(
            listOf("example.org" to "https://example.org", "http://example.com/a" to "http://example.com/a"),
            annotated.links().map { (part, link) -> part to (link as LinkAnnotation.Url).url },
        )
    }

    @Test
    fun `text URLs, email addresses and phone numbers open what they stand for`() {
        val annotated =
            annotate(
                "docs ada@example.org +1 555-0100",
                "docs" to TextEntityType.TextUrl("tg://resolve?domain=docs"),
                "ada@example.org" to TextEntityType.EmailAddress,
                "+1 555-0100" to TextEntityType.PhoneNumber,
            )

        assertEquals(
            listOf("tg://resolve?domain=docs", "mailto:ada@example.org", "tel:+15550100"),
            annotated.links().map { (_, link) -> (link as LinkAnnotation.Url).url },
        )
    }

    @Test
    fun `links are underlined in the link color and use the given listener`() {
        val listener = LinkInteractionListener {}
        val text = "see example.org"

        val link =
            FormattedText(text, listOf(entity(text, "example.org", TextEntityType.Url)).toImmutableList())
                .toAnnotatedString(styles, onLinkClick = listener)
                .links()
                .single()
                .second as LinkAnnotation.Url

        assertEquals(SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline), link.styles?.style)
        assertSame(listener, link.linkInteractionListener)
    }

    // Spoilers

    @Test
    fun `a hidden spoiler is a block of the spoiler color that reveals on tap`() {
        val onSpoilerClick = LinkInteractionListener {}

        val annotated = annotate("the end: secret", "secret" to TextEntityType.Spoiler, onSpoilerClick = onSpoilerClick)

        assertEquals(
            listOf(Styled("secret", SpanStyle(color = Color.DarkGray, background = Color.DarkGray))),
            annotated.styled(),
        )
        val (part, link) = annotated.links().single()
        assertEquals("secret", part)
        assertEquals(SPOILER_TAG, (link as LinkAnnotation.Clickable).tag)
        assertSame(onSpoilerClick, link.linkInteractionListener)
        // The link keeps the text hidden, instead of the default link look.
        assertEquals(SpanStyle(color = Color.DarkGray, background = Color.DarkGray), link.styles?.style)
    }

    @Test
    fun `a hidden spoiler covers the styles inside it, and its links stay inert`() {
        val text = "secret link"
        val annotated =
            annotate(
                text,
                entity(text, "secret link", TextEntityType.Spoiler),
                entity(text, "link", TextEntityType.TextUrl("https://example.org")),
                entity(text, "secret", TextEntityType.Bold),
            )

        assertEquals(
            SpanStyle(color = Color.DarkGray, background = Color.DarkGray),
            annotated.spanStyles.last().item,
        )
        assertTrue(annotated.links().none { it.second is LinkAnnotation.Url })
    }

    @Test
    fun `a revealed spoiler shows its text and links`() {
        val text = "secret link"
        val annotated =
            FormattedText(
                text,
                listOf(
                    entity(text, "secret link", TextEntityType.Spoiler),
                    entity(text, "link", TextEntityType.TextUrl("https://example.org")),
                ).toImmutableList(),
            ).toAnnotatedString(styles, spoilersRevealed = true)

        assertTrue(annotated.spanStyles.isEmpty())
        assertEquals(listOf("link"), annotated.links().map { it.first })
    }

    @Test
    fun `text that draws its blocks hides a spoiler as transparent text over a marked block`() {
        val text = "a secret"
        val annotated =
            FormattedText(text, listOf(entity(text, "secret", TextEntityType.Spoiler)).toImmutableList())
                .toAnnotatedString(styles, drawsBlocks = true)

        assertEquals(listOf(Styled("secret", SpanStyle(color = Color.Transparent))), annotated.styled())
        assertEquals(listOf("secret" to TextBlock.SPOILER), annotated.blocks())
    }

    @Test
    fun `a revealed spoiler draws no block`() {
        val text = "a secret"
        val annotated =
            FormattedText(text, listOf(entity(text, "secret", TextEntityType.Spoiler)).toImmutableList())
                .toAnnotatedString(styles, spoilersRevealed = true, drawsBlocks = true)

        assertEquals(emptyList<Pair<String, String>>(), annotated.blocks())
        assertFalse(annotated.hasTextBlocks())
    }

    // Offsets

    @Test
    fun `offsets count UTF-16 code units, so emoji before an entity take two`() {
        val text = "👋🏽 hi, bold"
        // 👋🏽 is a surrogate pair plus a modifier pair: four code units.
        val annotated = annotate(text, TextEntity(offset = 9, length = 4, type = TextEntityType.Bold))

        assertEquals(listOf(Styled("bold", SpanStyle(fontWeight = FontWeight.SemiBold))), annotated.styled())
    }

    @Test
    fun `an entity over an emoji covers its whole surrogate pair`() {
        val text = "a 😀 b"
        val annotated = annotate(text, TextEntity(offset = 2, length = 2, type = TextEntityType.Italic))

        assertEquals(listOf(Styled("😀", SpanStyle(fontStyle = FontStyle.Italic))), annotated.styled())
    }

    @Test
    fun `nested entities keep both, bold inside a link`() {
        val text = "read the docs"
        val annotated =
            annotate(
                text,
                entity(text, "the docs", TextEntityType.TextUrl("https://example.org")),
                entity(text, "docs", TextEntityType.Bold),
            )

        assertEquals(listOf(Styled("docs", SpanStyle(fontWeight = FontWeight.SemiBold))), annotated.styled())
        assertEquals(listOf("the docs"), annotated.links().map { it.first })
    }

    @Test
    fun `partly overlapping entities apply where they are`() {
        val text = "abcdef"
        val annotated =
            annotate(
                text,
                TextEntity(offset = 0, length = 4, type = TextEntityType.Bold),
                TextEntity(offset = 2, length = 4, type = TextEntityType.Italic),
            )

        assertEquals(
            listOf(
                Styled("abcd", SpanStyle(fontWeight = FontWeight.SemiBold)),
                Styled("cdef", SpanStyle(fontStyle = FontStyle.Italic)),
            ),
            annotated.styled(),
        )
    }

    @Test
    fun `entities past the end of the text are cut there, and empty ones dropped`() {
        val annotated =
            annotate(
                "short",
                TextEntity(offset = 2, length = 100, type = TextEntityType.Bold),
                TextEntity(offset = 10, length = 3, type = TextEntityType.Italic),
                TextEntity(offset = -2, length = 3, type = TextEntityType.Code),
                TextEntity(offset = 1, length = 0, type = TextEntityType.Underline),
            )

        assertEquals(
            listOf(
                Styled("ort", SpanStyle(fontWeight = FontWeight.SemiBold)),
                Styled("s", SpanStyle(fontFamily = FontFamily.Monospace, background = Color.LightGray)),
            ),
            annotated.styled(),
        )
    }

    private fun annotate(
        text: String,
        vararg parts: Pair<String, TextEntityType>,
        onSpoilerClick: LinkInteractionListener? = null,
    ): AnnotatedString =
        FormattedText(text, parts.map { (part, type) -> entity(text, part, type) }.toImmutableList())
            .toAnnotatedString(styles, onSpoilerClick = onSpoilerClick)

    private fun annotate(
        text: String,
        vararg entities: TextEntity,
    ): AnnotatedString = FormattedText(text, entities.toList().toImmutableList()).toAnnotatedString(styles)

    /** The entity over the first occurrence of [part] in [text]. */
    private fun entity(
        text: String,
        part: String,
        type: TextEntityType,
    ) = TextEntity(text.indexOf(part).also { check(it >= 0) { "No $part in $text" } }, part.length, type)

    /** A span: the part of the text it covers, and its style. */
    private data class Styled(
        val part: String,
        val style: SpanStyle,
    )

    private fun AnnotatedString.styled(): List<Styled> =
        spanStyles.map {
            Styled(text.substring(it.start, it.end), it.item)
        }

    private fun AnnotatedString.blocks(): List<Pair<String, String>> =
        getStringAnnotations(TEXT_BLOCK_TAG, 0, length).map { text.substring(it.start, it.end) to it.item }

    private fun AnnotatedString.links(): List<Pair<String, LinkAnnotation>> =
        getLinkAnnotations(0, length).map { text.substring(it.start, it.end) to it.item }
}

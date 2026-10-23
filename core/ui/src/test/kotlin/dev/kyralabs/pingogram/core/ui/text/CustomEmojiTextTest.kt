package dev.kyralabs.pingogram.core.ui.text

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Where [customEmoji] draws the pictures of a laid-out text's custom emoji ([customEmojiPlacements]): over the glyph
 * of the fallback emoji, on its line, left to right and right to left, and nowhere for hidden spoilers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CustomEmojiTextTest {
    // Declares the activity to Robolectric, as ui-test-manifest would.
    @get:Rule(order = 0)
    val activityRegistration =
        object : ExternalResource() {
            override fun before() {
                val application = RuntimeEnvironment.getApplication()
                shadowOf(application.packageManager)
                    .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
            }
        }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun formatted(
        text: String,
        vararg emoji: Pair<String, Long>,
        spoiler: IntRange? = null,
    ): FormattedText {
        var from = 0
        val entities =
            emoji.map { (fallback, id) ->
                val offset = text.indexOf(fallback, from)
                from = offset + fallback.length
                TextEntity(offset, fallback.length, TextEntityType.CustomEmoji(id))
            } + listOfNotNull(spoiler?.let { TextEntity(it.first, it.count(), TextEntityType.Spoiler) })
        return FormattedText(text, entities.toImmutableList())
    }

    /** The layout of [text] at [fontSize], [width] wide. */
    private fun layout(
        text: AnnotatedString,
        fontSize: Int = BODY_SIZE,
        width: Int = WIDTH,
        direction: TextDirection = TextDirection.Content,
    ): TextLayoutResult = layouts(listOf(text to fontSize), width, direction).single()

    /** The layouts of [texts], each at its font size, [width] wide, laid out in one composition. */
    private fun layouts(
        texts: List<Pair<AnnotatedString, Int>>,
        width: Int = WIDTH,
        direction: TextDirection = TextDirection.Content,
    ): List<TextLayoutResult> {
        val results = arrayOfNulls<TextLayoutResult>(texts.size)
        composeRule.setContent {
            Column {
                texts.forEachIndexed { index, (text, fontSize) ->
                    BasicText(
                        text = text,
                        modifier = Modifier.width(width.dp),
                        style =
                            TextStyle(
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * LINE_HEIGHT).sp,
                                textDirection = direction,
                            ),
                        onTextLayout = { results[index] = it },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return results.map { checkNotNull(it) }
    }

    private fun annotated(text: FormattedText) = text.toAnnotatedString(STYLES, drawsBlocks = true)

    @Test
    fun `a custom emoji's picture covers its fallback emoji, on its line, in a square`() {
        val result = layout(annotated(formatted("Party 🎉 time", "🎉" to 7)))

        val placement = result.customEmojiPlacements().single()

        val glyph = result.getBoundingBox("Party ".length)
        assertEquals(7L, placement.customEmojiId)
        assertEquals(glyph.left, placement.glyph.left, 0.5f)
        assertEquals(result.getLineTop(0), placement.glyph.top, 0.5f)
        assertEquals(result.getLineBottom(0), placement.glyph.bottom, 0.5f)
        assertEquals(placement.picture.width, placement.picture.height, 0.01f)
        assertEquals(placement.glyph.center.x, placement.picture.center.x, 0.5f)
        assertEquals(placement.glyph.center.y, placement.picture.center.y, 0.5f)
        assertTrue(placement.picture.width <= placement.glyph.height + 0.5f)
    }

    @Test
    fun `each of several custom emoji gets its own place, on the line it is on`() {
        val text = "A 🎉 and then a much longer run of words before the next one 🚀 at last"
        val result = layout(annotated(formatted(text, "🎉" to 1, "🚀" to 2)), width = NARROW)

        val placements = result.customEmojiPlacements()

        assertEquals(listOf(1L, 2L), placements.map { it.customEmojiId })
        val rocketLine = result.getLineForOffset(text.indexOf("🚀"))
        assertTrue(rocketLine > 0)
        assertEquals(result.getLineTop(rocketLine), placements[1].glyph.top, 0.5f)
    }

    @Test
    fun `in right-to-left text the picture is where the emoji is drawn`() {
        val text = "שלום 🎉 עולם"
        val result = layout(annotated(formatted(text, "🎉" to 3)), direction = TextDirection.Rtl)

        val placement = result.customEmojiPlacements().single()

        val glyph = result.getBoundingBox(text.indexOf("🎉"))
        assertEquals(glyph.left, placement.glyph.left, 0.5f)
        assertEquals(glyph.right, placement.glyph.right, 0.5f)
        // Right to left: "שלום" is at the right end, the emoji left of it.
        assertTrue(placement.glyph.right < result.getBoundingBox(0).left + 0.5f)
    }

    @Test
    fun `an emoji-only message's pictures are as large as its emoji`() {
        val (body, display) =
            layouts(
                listOf(
                    formatted("🎉", "🎉" to 1).customEmojiText() to BODY_SIZE,
                    formatted("🎉🎉", "🎉" to 1, "🎉" to 2).customEmojiText() to DISPLAY_SMALL_SIZE,
                ),
            )
        val small = body.customEmojiPlacements().single()
        val large = display.customEmojiPlacements()

        assertEquals(2, large.size)
        assertTrue(large[0].picture.width > small.picture.width * 2)
        assertTrue(large[0].glyph.right <= large[1].glyph.left + 0.5f)
    }

    @Test
    fun `a custom emoji in a hidden spoiler gets no picture, until the spoiler shows`() {
        val text = formatted("Psst 🎉 here", "🎉" to 1, spoiler = 5..6)

        val revealed = text.toAnnotatedString(STYLES, spoilersRevealed = true, drawsBlocks = true)
        val (hidden, shown) = layouts(listOf(annotated(text) to BODY_SIZE, revealed to BODY_SIZE))

        assertEquals(emptyList<CustomEmojiPlacement>(), hidden.customEmojiPlacements())
        assertEquals(1, shown.customEmojiPlacements().size)
    }

    @Test
    fun `a text field's entities place its custom emoji, its layout holding no annotations`() {
        val text = "Hi 🎉"
        val result = layout(AnnotatedString(text))
        val entities = listOf(TextEntity(3, 2, TextEntityType.CustomEmoji(9)), TextEntity(0, 2, TextEntityType.Bold))

        assertEquals(emptyList<CustomEmojiPlacement>(), result.customEmojiPlacements())
        assertEquals(listOf(9L), result.customEmojiPlacements(entities).map { it.customEmojiId })
        // An entity the text no longer reaches (an edit not seen yet) is left out.
        assertEquals(
            emptyList<CustomEmojiPlacement>(),
            result.customEmojiPlacements(listOf(TextEntity(4, 2, TextEntityType.CustomEmoji(9)))),
        )
    }

    @Test
    fun `text without custom emoji has nothing to place`() {
        assertEquals(
            emptyList<CustomEmojiPlacement>(),
            layout(annotated(FormattedText("Plain", persistentListOf()))).customEmojiPlacements(),
        )
    }

    private companion object {
        val STYLES =
            FormattedTextStyles(
                linkColor = Color.Blue,
                mentionColor = Color.Blue,
                codeBackground = Color.LightGray,
                spoilerColor = Color.Gray,
                quoteColor = Color.DarkGray,
                boldWeight = FontWeight.Bold,
            )
        const val BODY_SIZE = 16
        const val DISPLAY_SMALL_SIZE = 36
        const val LINE_HEIGHT = 1.4f
        const val WIDTH = 360
        const val NARROW = 200
    }
}

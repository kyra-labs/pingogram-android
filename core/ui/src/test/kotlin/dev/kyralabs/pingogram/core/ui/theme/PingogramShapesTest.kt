package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PingogramShapesTest {
    @Test
    fun `follows the expressive shape scale`() {
        val expected =
            listOf(4.dp, 8.dp, 12.dp, 16.dp, 20.dp, 28.dp, 32.dp, 48.dp).map { RoundedCornerShape(it) }

        val actual =
            with(PingogramShapes) {
                listOf(
                    extraSmall,
                    small,
                    medium,
                    large,
                    largeIncreased,
                    extraLarge,
                    extraLargeIncreased,
                    extraExtraLarge,
                )
            }

        assertEquals(expected, actual)
    }

    @Test
    fun `a single bubble has extra large corners all round, without a tail`() {
        val large = RoundedCornerShape(28.dp)

        assertEquals(large, PingogramShapes.messageBubble(isOutgoing = true, joinsPrevious = false, joinsNext = false))
        assertEquals(large, PingogramShapes.messageBubble(isOutgoing = false, joinsPrevious = false, joinsNext = false))
    }

    @Test
    fun `outgoing bubbles of a group meet with extra small corners at the end`() {
        val shapes = groupOf(isOutgoing = true)

        assertEquals(
            listOf(
                // First: large at the top end, small at the bottom end.
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 4.dp, bottomStart = 28.dp),
                // Middle: small at both ends.
                RoundedCornerShape(topStart = 28.dp, topEnd = 4.dp, bottomEnd = 4.dp, bottomStart = 28.dp),
                // Last: small at the top end, large at the bottom end.
                RoundedCornerShape(topStart = 28.dp, topEnd = 4.dp, bottomEnd = 28.dp, bottomStart = 28.dp),
            ),
            shapes,
        )
    }

    @Test
    fun `incoming bubbles of a group meet with extra small corners at the start`() {
        val shapes = groupOf(isOutgoing = false)

        assertEquals(
            listOf(
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 4.dp),
                RoundedCornerShape(topStart = 4.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 4.dp),
                RoundedCornerShape(topStart = 4.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 28.dp),
            ),
            shapes,
        )
    }

    @Test
    fun `the shapes built once are the rule's, the same instance each time`() {
        val built = MessageBubbleShapes(PingogramShapes)

        for (isOutgoing in listOf(false, true)) {
            for (joinsPrevious in listOf(false, true)) {
                for (joinsNext in listOf(false, true)) {
                    val shape = built.of(isOutgoing, joinsPrevious, joinsNext)
                    assertEquals(PingogramShapes.messageBubble(isOutgoing, joinsPrevious, joinsNext), shape)
                    assertSame(shape, built.of(isOutgoing, joinsPrevious, joinsNext))
                }
            }
        }
    }

    @Test
    fun `a one-line bubble is a full pill, its corners capped at half its height`() {
        val density = Density(1f)
        val size = Size(width = 200f, height = 40f)

        val outline =
            PingogramShapes
                .messageBubble(
                    false,
                    false,
                    false,
                ).createOutline(size, LayoutDirection.Ltr, density)

        val corners = (outline as Outline.Rounded).roundRect
        assertEquals(20f, corners.topLeftCornerRadius.x)
        assertEquals(20f, corners.bottomRightCornerRadius.x)
    }

    /** The shapes of the first, a middle and the last bubble of a group on the [isOutgoing] side. */
    private fun groupOf(isOutgoing: Boolean) =
        listOf(false to true, true to true, true to false).map { (joinsPrevious, joinsNext) ->
            PingogramShapes.messageBubble(isOutgoing, joinsPrevious, joinsNext)
        }
}

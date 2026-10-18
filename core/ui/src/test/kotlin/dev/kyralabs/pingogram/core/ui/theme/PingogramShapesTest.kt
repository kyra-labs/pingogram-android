package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
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
    fun `picks avatar placeholder polygons by id, negative chat ids included`() {
        assertNotSame(avatarPlaceholderPolygon(0), avatarPlaceholderPolygon(1))
        assertSame(avatarPlaceholderPolygon(5), avatarPlaceholderPolygon(-1))
        assertSame(avatarPlaceholderPolygon(-1_001_234_567_890), avatarPlaceholderPolygon(-1_001_234_567_890 + 6))
    }

    @Test
    fun `a single outgoing bubble is large with a small tail at the bottom end`() {
        val shape = PingogramShapes.messageBubble(isOutgoing = true, joinsPrevious = false, joinsNext = false)

        assertEquals(RoundedCornerShape(16.dp, 16.dp, 8.dp, 16.dp), shape)
    }

    @Test
    fun `a single incoming bubble has its tail at the bottom start`() {
        val shape = PingogramShapes.messageBubble(isOutgoing = false, joinsPrevious = false, joinsNext = false)

        assertEquals(RoundedCornerShape(16.dp, 16.dp, 16.dp, 8.dp), shape)
    }

    @Test
    fun `bubbles in a group are medium where they meet, the tail on the last one`() {
        val shapes =
            listOf(false to true, true to true, true to false).map { (joinsPrevious, joinsNext) ->
                PingogramShapes.messageBubble(isOutgoing = true, joinsPrevious, joinsNext)
            }

        assertEquals(
            listOf(
                RoundedCornerShape(16.dp, 16.dp, 12.dp, 16.dp),
                RoundedCornerShape(16.dp, 12.dp, 12.dp, 16.dp),
                RoundedCornerShape(16.dp, 12.dp, 8.dp, 16.dp),
            ),
            shapes,
        )
    }
}

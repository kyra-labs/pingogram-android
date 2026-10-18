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
}

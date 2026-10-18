package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.kyralabs.pingogram.core.domain.model.Contrast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ColorSchemeLerpTest {
    private val light = brandColorScheme(dark = false, contrast = Contrast.Standard)
    private val dark = brandColorScheme(dark = true, contrast = Contrast.Standard)

    private fun ColorScheme.roles(): List<Color> =
        listOf(
            primary,
            onPrimary,
            primaryContainer,
            onPrimaryContainer,
            inversePrimary,
            secondary,
            onSecondary,
            secondaryContainer,
            onSecondaryContainer,
            tertiary,
            onTertiary,
            tertiaryContainer,
            onTertiaryContainer,
            background,
            onBackground,
            surface,
            onSurface,
            surfaceVariant,
            onSurfaceVariant,
            surfaceTint,
            inverseSurface,
            inverseOnSurface,
            error,
            onError,
            errorContainer,
            onErrorContainer,
            outline,
            outlineVariant,
            scrim,
            surfaceBright,
            surfaceDim,
            surfaceContainer,
            surfaceContainerHigh,
            surfaceContainerHighest,
            surfaceContainerLow,
            surfaceContainerLowest,
            primaryFixed,
            primaryFixedDim,
            onPrimaryFixed,
            onPrimaryFixedVariant,
            secondaryFixed,
            secondaryFixedDim,
            onSecondaryFixed,
            onSecondaryFixedVariant,
            tertiaryFixed,
            tertiaryFixedDim,
            onTertiaryFixed,
            onTertiaryFixedVariant,
        )

    @Test
    fun `starts at the first scheme`() {
        assertEquals(light.roles(), lerp(light, dark, 0f).roles())
    }

    @Test
    fun `ends at the second scheme`() {
        assertEquals(dark.roles(), lerp(light, dark, 1f).roles())
    }

    @Test
    fun `moves every role between the two`() {
        val half = lerp(light, dark, 0.5f).roles()

        light.roles().zip(dark.roles()).zip(half).forEachIndexed { index, (ends, middle) ->
            assertEquals(lerp(ends.first, ends.second, 0.5f), middle, "role $index")
        }
        // The light and dark schemes differ in their surfaces at least, so the crossfade shows.
        assertNotEquals(light.surface, lerp(light, dark, 0.5f).surface)
    }

    @Test
    fun `a role either scheme leaves unspecified takes the second scheme's`() {
        val withoutFixed = lightColorScheme().copy(primaryFixed = Color.Unspecified)
        val target = darkColorScheme()

        assertEquals(target.primaryFixed, lerp(withoutFixed, target, 0.5f).primaryFixed)
        assertEquals(Color.Unspecified, lerp(target, withoutFixed, 0.5f).primaryFixed)
    }
}

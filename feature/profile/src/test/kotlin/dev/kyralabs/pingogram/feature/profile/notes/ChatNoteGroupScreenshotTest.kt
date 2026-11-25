package dev.kyralabs.pingogram.feature.profile.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.profile.ui.profilePageColor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of "On this phone" (`docs/DESIGN_SYSTEM.md` §6 "Notes and names on this phone"): the rows with a name and a
 * note and without, light and dark, and at 200% font.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatNoteGroupScreenshotTest {
    private val filled = ChatNoteGroupStates().values.first()
    private val empty = ChatNoteGroupStates().values.last()

    @Test
    fun light() = capture(ScreenshotVariant.Light, filled)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark, filled)

    @Test
    fun emptyLight() = capture(ScreenshotVariant.Light, empty)

    @Test
    @Config(fontScale = 2f)
    fun fontScale200() = capture(ScreenshotVariant.Light, filled)

    private fun capture(
        variant: ScreenshotVariant,
        state: ChatNoteUiState,
    ) = captureRoboImage {
        ScreenshotTheme(variant) {
            Box(Modifier.background(profilePageColor()).padding(MaterialTheme.spacing.large)) {
                ChatNoteGroup(state, onEvent = {})
            }
        }
    }
}

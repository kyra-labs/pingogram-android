package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSpeed
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.chat.R
import java.text.NumberFormat

/**
 * The speed of the voice or round video message in the player (`REQUIREMENTS.md` FR-X4, `docs/DESIGN_SYSTEM.md` §6
 * "Playback speed"): "1.5×" in `labelLarge` with tabular figures on a tonal [SuggestionChip] in [containerColor] and
 * [contentColor], growing in and out with [visible] on the fast spatial spring. It keeps the chip's own height in the
 * layout, its 48 dp target reaching past it (the platform's minimum touch target), so the bubble grows only by the
 * chip. A tap ([onChange]) goes to the next speed. Screen readers hear "Playback speed, 1.5 times", "Change speed".
 */
@Composable
internal fun SpeedChip(
    visible: Boolean,
    speed: PlaybackSpeed,
    onChange: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandIn(motion.fastSpatialSpec(), Alignment.Center) + fadeIn(motion.fastEffectsSpec()),
        exit = shrinkOut(motion.fastSpatialSpec(), Alignment.Center) + fadeOut(motion.fastEffectsSpec()),
    ) {
        val description =
            if (speed == PlaybackSpeed.Normal) {
                stringResource(R.string.content_speed_normal)
            } else {
                stringResource(R.string.content_speed, speedNumber(speed))
            }
        val action = stringResource(R.string.content_change_speed)
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            SuggestionChip(
                onClick = onChange,
                label = {
                    Text(
                        text = stringResource(R.string.content_speed_value, speedNumber(speed)),
                        modifier = Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    )
                },
                modifier =
                    Modifier.semantics {
                        contentDescription = description
                        onClick(label = action) {
                            onChange()
                            true
                        }
                    },
                colors =
                    SuggestionChipDefaults.suggestionChipColors(
                        containerColor = containerColor,
                        labelColor = contentColor,
                    ),
                border = null,
            )
        }
    }
}

/** "1.5", in the digits and decimal separator of the current language. */
@Composable
private fun speedNumber(speed: PlaybackSpeed): String {
    val format = NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0])
    format.maximumFractionDigits = 1
    return format.format(speed.factor)
}

@PingogramPreviews
@Composable
private fun SpeedChipPreview() {
    PingogramPreview {
        SpeedChip(
            visible = true,
            speed = PlaybackSpeed.Faster,
            onChange = {},
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

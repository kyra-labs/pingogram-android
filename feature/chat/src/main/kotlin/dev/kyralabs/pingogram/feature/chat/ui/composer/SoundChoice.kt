package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The send menus' sound entry (`REQUIREMENTS.md` FR-X11): "Send with sound" ([silent] false) while messages go without
 * sound by default ([LocalSendsSilently]), "Send without sound" ([silent] true) otherwise.
 */
@Immutable
internal class SoundChoice(
    val silent: Boolean,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
)

/** The sound entry the send menus offer, as "Send silently by default" asks. */
@Composable
@ReadOnlyComposable
internal fun soundChoice(): SoundChoice =
    if (LocalSendsSilently.current) {
        SoundChoice(silent = false, R.string.composer_send_with_sound, UiR.drawable.ic_notifications)
    } else {
        SoundChoice(silent = true, R.string.composer_send_silently, UiR.drawable.ic_notifications_off)
    }

package dev.kyralabs.pingogram.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.core.content.IntentCompat
import dev.kyralabs.pingogram.MainActivity
import dev.kyralabs.pingogram.PageToOpen
import dev.kyralabs.pingogram.core.domain.focus.FocusClock
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.ManualFocus
import java.time.Instant
import java.time.ZoneId

/**
 * The Focus tile's intents (board 31): its long press (Android's [TileService.ACTION_QS_TILE_PREFERENCES], naming the
 * tile) opens the Chats tab's switcher; its tap while there is no profile to turn on opens Settings → Focus.
 */
internal object FocusTile {
    private const val ACTION_OPEN_FOCUS = "dev.kyralabs.pingogram.action.OPEN_FOCUS"

    /** The page [intent] asks for, if it comes from the Focus tile. */
    fun pageOf(intent: Intent?): PageToOpen? =
        when (intent?.action) {
            TileService.ACTION_QS_TILE_PREFERENCES -> {
                val tile =
                    IntentCompat.getParcelableExtra(
                        intent,
                        Intent.EXTRA_COMPONENT_NAME,
                        ComponentName::class.java,
                    )
                PageToOpen.FocusSwitcher.takeIf { tile?.className == FocusTileService::class.java.name }
            }

            ACTION_OPEN_FOCUS -> {
                PageToOpen.Focus
            }

            else -> {
                null
            }
        }

    /** Opens Settings → Focus in the app. */
    fun openFocus(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_FOCUS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * What the Focus tile shows: whether a profile is [on], its [name] and when it ends ([endsAt], epoch seconds; null
 * while it lasts until turned off) while on, and the [icon] of the profile on, or of the one a tap turns on (null:
 * there is none).
 */
internal data class FocusTileState(
    val on: Boolean,
    val name: String?,
    val icon: FocusIcon?,
    val endsAt: Long?,
) {
    override fun toString() = "FocusTileState(on=$on, icon=$icon, endsAt=$endsAt)"

    companion object {
        fun of(
            profiles: List<FocusProfile>,
            manual: ManualFocus?,
            lastUsedId: Long?,
            now: Instant,
            zone: ZoneId,
        ): FocusTileState {
            val active = FocusClock.active(profiles, manual, now, zone)
            return if (active != null) {
                FocusTileState(on = true, active.profile.name, active.profile.icon, active.endsAt)
            } else {
                val next = profiles.firstOrNull { it.id == lastUsedId } ?: profiles.firstOrNull()
                FocusTileState(on = false, name = null, icon = next?.icon, endsAt = null)
            }
        }
    }
}

/** Opens [intent]'s activity from the tile and closes the Quick Settings panel. */
internal fun TileService.openActivity(intent: Intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
    } else {
        // Android 14 takes a PendingIntent and refuses the Intent; before it, only the Intent exists.
        @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
        startActivityAndCollapse(intent)
    }
}

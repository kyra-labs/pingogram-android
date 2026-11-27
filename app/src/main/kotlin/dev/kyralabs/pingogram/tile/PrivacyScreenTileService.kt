package dev.kyralabs.pingogram.tile

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.domain.repository.PrivacyScreenRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The privacy screen's tile in Quick Settings (`REQUIREMENTS.md` FR-X25, owner decisions 2026-11-25): the same switch
 * as Privacy and security → "Privacy screen", on or off, "On" or "Off" below its name. A tap turns it the other way
 * ([PrivacyScreenToggle]); it stays so until turned again. Turning it on works from the lock screen; turning it off
 * asks to unlock the phone first, so whoever holds a locked phone cannot. Android draws the tile; it follows the
 * setting while Quick Settings shows it, and stops with it.
 */
@AndroidEntryPoint
class PrivacyScreenTileService : TileService() {
    @Inject
    lateinit var repository: PrivacyScreenRepository

    private val scope = MainScope()
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch { repository.enabled.distinctUntilChanged().collect(::show) }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val toggle = PrivacyScreenToggle(repository)
        // The change shows through the setting's flow; one that cannot be saved leaves the tile as it was.
        scope.launch {
            if (toggle.needsUnlock(isLocked = isLocked)) {
                unlockAndRun { scope.launch { toggle.toggle() } }
            } else {
                toggle.toggle()
            }
        }
    }

    private fun show(on: Boolean) {
        val tile = qsTile ?: return
        val status = getString(if (on) R.string.tile_privacy_screen_on else R.string.tile_privacy_screen_off)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_privacy_screen)
        tile.icon = Icon.createWithResource(this, UiR.drawable.ic_visibility_off)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tile.stateDescription = status
        tile.updateTile()
    }
}

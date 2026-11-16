package dev.kyralabs.pingogram.tile

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import dagger.hilt.android.AndroidEntryPoint
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.focus.FocusToggle
import dev.kyralabs.pingogram.core.domain.focus.FocusToggleResult
import dev.kyralabs.pingogram.core.domain.repository.FocusRepository
import dev.kyralabs.pingogram.core.ui.component.symbol
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Focus tile of Quick Settings (`docs/DESIGN_SYSTEM.md` §13.13, board 31, owner decision 2026-11-15): on while a
 * profile is, with its name and when it ends as the subtitle ("Work hours · until 18:00"), "Off" otherwise, and the
 * profile's symbol. A tap turns the last used profile on or off ([FocusToggle]), after unlocking the phone where it is
 * locked; with no profile to turn on, it opens Settings → Focus. Its long press opens the Chats tab's switcher
 * ([FocusTile.pageOf]). Android draws the tile; it follows the profiles while Quick Settings shows it, each minute,
 * and stops with it.
 */
@AndroidEntryPoint
class FocusTileService : TileService() {
    @Inject
    lateinit var repository: FocusRepository

    @Inject
    lateinit var toggle: FocusToggle

    @Inject
    lateinit var clock: WallClock

    private val scope = MainScope()
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening =
            scope.launch {
                combine(repository.profiles, repository.manual, repository.lastUsedId, clock.minutes) {
                    profiles,
                    manual,
                    lastUsedId,
                    now,
                    ->
                    FocusTileState.of(profiles, manual, lastUsedId, now, clock.zone())
                }.distinctUntilChanged().collect(::show)
            }
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
        if (isLocked) unlockAndRun(::toggleNow) else toggleNow()
    }

    private fun toggleNow() {
        scope.launch {
            // Done shows through the profiles' flows; a failure leaves the tile as it was.
            if (toggle.toggle() ==
                FocusToggleResult.NoProfiles
            ) {
                openActivity(FocusTile.openFocus(this@FocusTileService))
            }
        }
    }

    private fun show(state: FocusTileState) {
        val tile = qsTile ?: return
        val status =
            when {
                !state.on -> getString(R.string.tile_focus_off)
                state.endsAt != null -> getString(R.string.tile_focus_until, state.name, time(state.endsAt))
                else -> state.name
            }
        tile.state = if (state.on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_focus)
        tile.icon = Icon.createWithResource(this, state.icon?.symbol() ?: UiR.drawable.ic_do_not_disturb_on)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tile.stateDescription = status
        tile.updateTile()
    }

    private fun time(epochSeconds: Long): String =
        DateFormat.getTimeFormat(this).format(Date(epochSeconds * MILLIS_PER_SECOND))

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

package dev.kyralabs.pingogram.tile

import android.content.ComponentName
import android.content.Intent
import android.service.quicksettings.TileService
import androidx.test.core.app.ApplicationProvider
import dev.kyralabs.pingogram.PageToOpen
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import dev.kyralabs.pingogram.core.domain.model.FocusUntil
import dev.kyralabs.pingogram.core.domain.model.ManualFocus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The Focus tile (board 31): what it shows, and which page its intents open. */
@RunWith(RobolectricTestRunner::class)
class FocusTileTest {
    private val work =
        FocusProfile(
            id = 1,
            name = "Work hours",
            icon = FocusIcon.Work,
            allowedFolderIds = persistentListOf(),
            allowedChatIds = persistentListOf(),
            schedule = FocusSchedule(DayOfWeek.entries.take(5).toImmutableSet(), 9 * 60, 18 * 60),
            allowsCallsFromContacts = true,
        )
    private val weekend = work.copy(id = 2, name = "Weekend", icon = FocusIcon.Weekend, schedule = null)

    /** Monday 2026-11-16 at [hour] UTC. */
    private fun monday(hour: Int) = LocalDateTime.of(2026, 11, 16, hour, 0).toInstant(ZoneOffset.UTC)

    @Test
    fun `on shows the profile and when it ends`() {
        val state = FocusTileState.of(listOf(work, weekend), null, null, monday(10), ZoneOffset.UTC)

        assertEquals(FocusTileState(true, "Work hours", FocusIcon.Work, monday(18).epochSecond), state)
    }

    @Test
    fun `off shows the symbol of the profile a tap turns on`() {
        val off = ManualFocus(null, monday(10).epochSecond, FocusUntil.ScheduleChange)

        val state = FocusTileState.of(listOf(work, weekend), off, 2L, monday(10), ZoneOffset.UTC)

        assertEquals(FocusTileState(false, null, FocusIcon.Weekend, null), state)
        assertNull(FocusTileState.of(emptyList(), null, null, monday(10), ZoneOffset.UTC).icon)
    }

    @Test
    fun `the tile's long press opens the switcher, and its tap without profiles the Focus page`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        fun preferences(service: Class<*>) =
            Intent(TileService.ACTION_QS_TILE_PREFERENCES)
                .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context, service))

        assertEquals(PageToOpen.FocusSwitcher, FocusTile.pageOf(preferences(FocusTileService::class.java)))
        assertNull(FocusTile.pageOf(preferences(NewMessageTileService::class.java)))
        assertEquals(PageToOpen.Focus, FocusTile.pageOf(FocusTile.openFocus(context)))
        assertNull(FocusTile.pageOf(Intent(Intent.ACTION_MAIN)))
    }
}

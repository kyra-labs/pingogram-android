package dev.kyralabs.pingogram.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/*
 * Saved Messages' topic tabs and tags (`docs/DESIGN_SYSTEM.md` §6 "Saved Messages tabs", "Saved Messages tags"), which
 * Saved Messages remembers between visits: the journeys show "All" first, so what they scroll and tap does not depend
 * on the tab or tag it was left on.
 */

/**
 * Shows "All" of the open Saved Messages, whichever topic tab or tag it was left on: taps the "All" tab and then the
 * tags' "All" chip where they show and are not chosen, and waits for each to be chosen. Both rows grow in above the
 * messages once Saved Messages' topics and tags load, so each is waited for a moment ([ROW_TIMEOUT_MS]); an account
 * with fewer than two topics has no tabs, and one without tags no chips. Not part of [openSavedMessages], which
 * OpenChatBenchmark measures.
 */
internal fun MacrobenchmarkScope.showAllSavedMessages() {
    for (all in listOf(SAVED_TAB_ALL, SAVED_TAGS_ALL)) {
        val shown = device.wait(Until.findObject(By.res(all)), ROW_TIMEOUT_MS)
        if (shown != null && !shown.isChosen()) choose(all)
    }
}

/**
 * The bottom of Saved Messages' topic tabs or tags, the lower of the two rows, found by their "All"; null where neither
 * shows.
 */
internal fun MacrobenchmarkScope.savedRowsBottom(): Int? =
    listOf(SAVED_TAB_ALL, SAVED_TAGS_ALL)
        .mapNotNull { device.findObject(By.res(it))?.visibleBounds?.bottom }
        .maxOrNull()

/** Taps the tab or chip tagged [tag] once it has stopped moving, then waits for it to be chosen. */
private fun MacrobenchmarkScope.choose(tag: String) {
    settledObject { device.findObject(By.res(tag)) }?.click()
    var looks = 0
    while (device.findObject(By.res(tag))?.isChosen() == false && looks < SETTLE_TRIES) {
        SystemClock.sleep(SETTLE_STEP_MS)
        looks++
    }
}

/** Whether a tab or a chip is the one chosen: a toggle reads as checked, a tab as selected. */
private fun UiObject2.isChosen(): Boolean = isChecked || isSelected

// The tags in feature:chat (SavedTabs.kt, SavedTagChips.kt).
private const val SAVED_TAB_ALL = "saved_tab_all"
private const val SAVED_TAGS_ALL = "saved_tags_all"

/** How long [showAllSavedMessages] waits for Saved Messages' tabs or tags before deciding the account has none. */
private const val ROW_TIMEOUT_MS = 3_000L

package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/*
 * Catch-up and the Bookmarks page, for the Baseline Profile and CatchUpBenchmark / BookmarksScrollBenchmark
 * (`docs/PERFORMANCE.md` §9). Neither reads a chat: Catch-up shows unread chats without marking them (only "Mark as
 * read" and "Open chat" do), and the journeys step through its cards with "Skip"; the Bookmarks page opens from Saved
 * Messages, the one chat the journeys open.
 */

/**
 * Opens Catch-up from the chip in the chat list's header and returns its first card, or null where the chip does not
 * show: it needs at least five unread chats that are not muted (`docs/DESIGN_SYSTEM.md` §6 "Catch-up").
 */
internal fun MacrobenchmarkScope.openCatchUp(): UiObject2? {
    waitForChatList()
    val chip = device.wait(Until.findObject(By.res(CATCH_UP_CHIP)), CHIP_TIMEOUT_MS) ?: return null
    chip.click()
    return device.wait(Until.findObject(By.res(CATCH_UP_CARD)), TIMEOUT_MS)
        ?: error("Catch-up showed no card within $TIMEOUT_MS ms.")
}

/** Skips up to [CARDS] of Catch-up's cards, one after another, each followed by a wait until the app is idle. */
internal fun MacrobenchmarkScope.skipCatchUpCards() {
    repeat(CARDS) {
        val skip = device.wait(Until.findObject(By.res(CATCH_UP_SKIP)), TIMEOUT_MS) ?: return
        skip.click()
        device.waitForIdle()
    }
}

/**
 * Opens the Bookmarks page through Saved Messages' card and returns its list, or null where Saved Messages shows no
 * card: it shows once a message is bookmarked, and on "All" only, so Saved Messages is shown on "All" first
 * ([showAllSavedMessages]), whichever tab it was left on. The card sits in the top bar below the topic tabs and the
 * tags, which grow in above it as they load and push it down: it is tapped once it has stayed still for
 * [CARD_STILL_LOOKS] looks ([settledObject]), at a point below those rows ([tapBelowSavedRows]), and once more, on
 * "All" again, if the page does not open.
 */
internal fun MacrobenchmarkScope.openBookmarks(): UiObject2? {
    openSavedMessages()
    showAllSavedMessages()
    if (!device.wait(Until.hasObject(By.res(SAVED_BOOKMARKS)), CHIP_TIMEOUT_MS)) return null
    var page: UiObject2? = null
    var taps = 0
    while (page == null && taps < CARD_TAPS) {
        if (taps > 0) showAllSavedMessages()
        settledObject(CARD_STILL_LOOKS) { device.findObject(By.res(SAVED_BOOKMARKS)) }?.let { tapBelowSavedRows(it) }
        page = device.wait(Until.findObject(By.res(BOOKMARKS_LIST)), TIMEOUT_MS)
        taps++
    }
    return page ?: screenMissing("The Bookmarks page did not open within $TIMEOUT_MS ms.")
}

/**
 * Taps [card] in the middle of its visible part below Saved Messages' tabs and tags ([savedRowsBottom]), so never on a
 * tab or a chip, which would show another tab or a tag's messages and take the card away. Nothing where the rows
 * cover all of it.
 */
private fun MacrobenchmarkScope.tapBelowSavedRows(card: UiObject2) {
    val bounds = card.visibleBounds
    val top = maxOf(bounds.top, savedRowsBottom() ?: bounds.top)
    if (top < bounds.bottom) device.click(bounds.centerX(), (top + bounds.bottom) / 2)
}

/** The open Bookmarks page's cards, found afresh: a list found in an earlier block may be stale. */
internal fun MacrobenchmarkScope.bookmarksList(): UiObject2 =
    device.wait(Until.findObject(By.res(BOOKMARKS_LIST)), TIMEOUT_MS)
        ?: error("No Bookmarks page open after $TIMEOUT_MS ms.")

/** Flings the open Bookmarks page's cards down and back up. */
internal fun MacrobenchmarkScope.scrollBookmarks() {
    fling(Direction.DOWN) { bookmarksList() }
    fling(Direction.UP) { bookmarksList() }
}

// The tags in feature:chats (CatchUpChip.kt, CatchUpCard.kt) and feature:chat (SavedBookmarksCard.kt,
// BookmarksScreen.kt).
private const val CATCH_UP_CHIP = "catch_up_chip"
private const val CATCH_UP_CARD = "catch_up_card"
private const val CATCH_UP_SKIP = "catch_up_skip"
private const val SAVED_BOOKMARKS = "saved_bookmarks"
private const val BOOKMARKS_LIST = "bookmarks_list"

/** How long the journeys wait for a screen: TDLib may still be loading after a cold start. */
private const val TIMEOUT_MS = 10_000L

/** How long they wait for the chip or the card to show before deciding it does not: the chats come from TDLib first. */
private const val CHIP_TIMEOUT_MS = 3_000L

private const val CARDS = 3

/** How many times [openBookmarks] taps Saved Messages' card before it gives up. */
private const val CARD_TAPS = 2

/**
 * How many looks in a row Saved Messages' card stays still before [openBookmarks] taps it: about a second
 * ([SETTLE_STEP_MS] apart), as the rows above it may still be growing in.
 */
private const val CARD_STILL_LOOKS = 6

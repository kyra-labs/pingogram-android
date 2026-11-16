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
 * card: it shows once a message is bookmarked.
 */
internal fun MacrobenchmarkScope.openBookmarks(): UiObject2? {
    openSavedMessages()
    val card = device.wait(Until.findObject(By.res(SAVED_BOOKMARKS)), CHIP_TIMEOUT_MS) ?: return null
    card.click()
    return device.wait(Until.findObject(By.res(BOOKMARKS_LIST)), TIMEOUT_MS)
        ?: error("The Bookmarks page did not open within $TIMEOUT_MS ms.")
}

/** The open Bookmarks page's cards, found afresh: a list found in an earlier block may be stale. */
internal fun MacrobenchmarkScope.bookmarksList(): UiObject2 =
    device.wait(Until.findObject(By.res(BOOKMARKS_LIST)), TIMEOUT_MS)
        ?: error("No Bookmarks page open after $TIMEOUT_MS ms.")

/** Flings the Bookmarks page's cards [list] down and back up. */
internal fun MacrobenchmarkScope.scrollBookmarks(list: UiObject2) {
    fling(list, Direction.DOWN)
    fling(list, Direction.UP)
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

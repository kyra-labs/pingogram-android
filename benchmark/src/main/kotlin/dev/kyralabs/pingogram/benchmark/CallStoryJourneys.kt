package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/*
 * Recent calls and the story rings, for the Baseline Profile and CallsAndStoriesBenchmark (`docs/PERFORMANCE.md` §2,
 * §9). Each is skipped, returning false or null, where its screen does not show, so an account without calls or
 * stories, or a window with the navigation rail, still yields a profile. Neither places a call nor posts a story:
 * Recent calls is only scrolled (a tap on a row calls back), and the only stories opened are the user's own, which
 * tell no one they were seen, closed before their first one ends, as the viewer goes on to other chats' stories after
 * the last of them.
 */

/**
 * Opens Recent calls from the Contacts tab's top bar and returns its list, or null where the Contacts tab, its button
 * or a list of calls does not show (an account without calls shows its empty state instead).
 */
internal fun MacrobenchmarkScope.openRecentCalls(): UiObject2? {
    waitForChatList()
    val tab = device.wait(Until.findObject(By.res(NAV_CONTACTS)), TIMEOUT_MS) ?: return null
    tab.click()
    val list =
        device.wait(Until.findObject(By.res(RECENT_CALLS_BUTTON)), TIMEOUT_MS)?.let { button ->
            button.click()
            // Without calls, the page shows its empty state: left again.
            device.wait(Until.findObject(By.res(RECENT_CALLS_LIST)), TIMEOUT_MS) ?: null.also { device.pressBack() }
        }
    if (list == null) showChats()
    return list
}

/** Flings the open Recent calls list down and back up, touching no row. */
internal fun MacrobenchmarkScope.scrollRecentCalls() {
    fling(Direction.DOWN) { recentCallsList() }
    fling(Direction.UP) { recentCallsList() }
}

/** Leaves Recent calls and the Contacts tab for the chat list. */
internal fun MacrobenchmarkScope.closeRecentCalls() {
    device.pressBack()
    device.wait(Until.hasObject(By.res(NAV_CONTACTS)), TIMEOUT_MS)
    showChats()
}

/** The open Recent calls list, found afresh: a list found in an earlier block may be stale. */
private fun MacrobenchmarkScope.recentCallsList(): UiObject2 =
    device.wait(Until.findObject(By.res(RECENT_CALLS_LIST)), TIMEOUT_MS)
        ?: error("No Recent calls list open after $TIMEOUT_MS ms.")

private fun MacrobenchmarkScope.showChats() {
    device.wait(Until.findObject(By.res(NAV_CHATS)), TIMEOUT_MS)?.click()
    waitForChatList()
}

/** The story rings above the chat list, or null where the account sees no stories (the row then does not show). */
internal fun MacrobenchmarkScope.storyRings(): UiObject2? {
    waitForChatList()
    return device.wait(Until.findObject(By.res(STORY_RINGS)), RINGS_WAIT_MS)
}

/** Flings the story rings towards their end and back, opening none. */
internal fun MacrobenchmarkScope.scrollStoryRings() {
    fling(Direction.RIGHT) { storyRingsRow() }
    fling(Direction.LEFT) { storyRingsRow() }
}

/** The story rings, found afresh: a row found in an earlier block may be stale. */
private fun MacrobenchmarkScope.storyRingsRow(): UiObject2 =
    device.wait(Until.findObject(By.res(STORY_RINGS)), TIMEOUT_MS) ?: error("No story rings after $TIMEOUT_MS ms.")

/**
 * Opens the user's own stories from their ring, lets the viewer draw and closes it; false where the user has no
 * story, or the viewer does not show. Closed as soon as it is idle, well before a story ends, so it never goes on to
 * another chat's stories.
 */
internal fun MacrobenchmarkScope.openOwnStories(): Boolean {
    val ring = storyRings()?.let { device.findObject(By.res(OWN_STORIES_RING)) } ?: return false
    ring.click()
    val shown = device.wait(Until.hasObject(By.res(STORY_VIEWER)), VIEWER_WAIT_MS)
    if (shown) {
        device.waitForIdle(VIEWER_IDLE_MS)
        device.pressBack()
        waitForChatList()
    }
    return shown
}

// The tags in :app (PingogramNavigationBar.kt), feature:contacts (ContactsScreen.kt), feature:calls
// (RecentCallsScreen.kt), feature:chats (StoryRings.kt) and feature:media (StoryViewerScreen.kt).
private const val NAV_CHATS = "nav_chats"
private const val NAV_CONTACTS = "nav_contacts"
private const val RECENT_CALLS_BUTTON = "contacts_recent_calls"
private const val RECENT_CALLS_LIST = "recent_calls_list"
private const val STORY_RINGS = "story_rings"
private const val OWN_STORIES_RING = "story_ring_own"
private const val STORY_VIEWER = "story_viewer"

private const val TIMEOUT_MS = 10_000L

/** How long the story rings are waited for, short: they show only while the account sees stories. */
private const val RINGS_WAIT_MS = 3_000L

/** How long the viewer is waited for, and then for its first frames: far shorter than a story. */
private const val VIEWER_WAIT_MS = 2_000L
private const val VIEWER_IDLE_MS = 1_000L

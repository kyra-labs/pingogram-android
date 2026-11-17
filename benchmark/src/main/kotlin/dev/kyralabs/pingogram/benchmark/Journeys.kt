package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/*
 * What the benchmarks and the Baseline Profile generator do in the app. Past the start, they need an account: sign in
 * on the device first, in the build under test (`docs/PERFORMANCE.md` §9). They find the screens by test tags, which
 * `MainActivity` shows as resource ids.
 */

/** Waits for the chat list, which only a signed-in app shows. */
internal fun MacrobenchmarkScope.waitForChatList(): UiObject2 =
    device.wait(Until.findObject(By.res(CHAT_LIST)), TIMEOUT_MS)
        ?: error(
            "No chat list after $TIMEOUT_MS ms. Sign in to Pingogram on the device first (docs/PERFORMANCE.md §9).",
        )

/** Flings the chat list down [FLINGS] times, loading more chats on the way, and back up. */
internal fun MacrobenchmarkScope.scrollChatList() {
    fling(Direction.DOWN) { waitForChatList() }
    fling(Direction.UP) { waitForChatList() }
}

/**
 * Opens Saved Messages from the chat list and returns its messages. The journeys open no other chat: opening one marks
 * its messages as read.
 */
internal fun MacrobenchmarkScope.openSavedMessages(): UiObject2 {
    val list = waitForChatList()
    list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_FRACTION)
    val row =
        device.findObject(By.res(SAVED_MESSAGES))
            ?: list.scrollUntil(Direction.DOWN, Until.findObject(By.res(SAVED_MESSAGES)))
            ?: error("Saved Messages is not in the chat list. Send yourself a few messages there first.")
    row.click()
    return device.wait(Until.findObject(By.res(MESSAGE_LIST)), TIMEOUT_MS)
        ?: error("Saved Messages did not open within $TIMEOUT_MS ms.")
}

/**
 * Goes back to the chat list from wherever the app is (a conversation the last iteration opened), up to
 * [BACK_PRESSES] times.
 */
internal fun MacrobenchmarkScope.returnToChatList() {
    repeat(BACK_PRESSES) {
        if (device.hasObject(By.res(CHAT_LIST)) && !device.hasObject(By.res(MESSAGE_LIST))) return
        device.pressBack()
        device.waitForIdle()
    }
    waitForChatList()
}

/** The open conversation's messages, found afresh: a list found in an earlier block may be stale. */
internal fun MacrobenchmarkScope.messageList(): UiObject2 =
    device.wait(Until.findObject(By.res(MESSAGE_LIST)), TIMEOUT_MS)
        ?: error("No conversation open after $TIMEOUT_MS ms.")

/** Flings the open conversation up to older messages, loading them on the way, and back down. */
internal fun MacrobenchmarkScope.scrollConversation() {
    fling(Direction.UP) { messageList() }
    fling(Direction.DOWN) { messageList() }
}

/**
 * [FLINGS] flings in [direction] of the list [find] returns, each followed by a wait until the app is idle. The list is
 * found again before each fling: scrolling can replace its node, and a fling on the old one throws
 * `StaleObjectException`.
 */
internal fun MacrobenchmarkScope.fling(
    direction: Direction,
    find: () -> UiObject2,
) {
    repeat(FLINGS) {
        val list = find()
        // Clear of the system's back gesture along the sides.
        list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_FRACTION)
        list.fling(direction)
        device.waitForIdle()
    }
}

// The tags in feature:chats (ChatListScreen.kt) and feature:chat (ChatScreen.kt).
private const val CHAT_LIST = "chat_list"
private const val SAVED_MESSAGES = "saved_messages"
private const val MESSAGE_LIST = "message_list"

/** How long the journeys wait for a screen: TDLib may still be loading after a cold start. */
private const val TIMEOUT_MS = 10_000L

private const val FLINGS = 3
private const val BACK_PRESSES = 3
private const val GESTURE_MARGIN_FRACTION = 5

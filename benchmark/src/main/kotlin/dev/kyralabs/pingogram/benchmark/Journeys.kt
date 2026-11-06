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
    val list = waitForChatList()
    fling(list, Direction.DOWN)
    fling(list, Direction.UP)
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

/** Types [text] into the open conversation's composer, ready to send. */
internal fun MacrobenchmarkScope.typeMessage(text: String) {
    val field =
        device.wait(Until.findObject(By.res(COMPOSER_FIELD)), TIMEOUT_MS)
            ?: error("No composer after $TIMEOUT_MS ms.")
    field.text = text
    device.wait(Until.hasObject(By.res(SEND_BUTTON)), TIMEOUT_MS)
}

/** Taps send and waits for the bubble of [text] to show. */
internal fun MacrobenchmarkScope.sendTyped(text: String): UiObject2 {
    device.findObject(By.res(SEND_BUTTON))?.click() ?: error("No send button: type a message first.")
    return device.wait(Until.findObject(By.descContains(text)), TIMEOUT_MS)
        ?: error("The bubble of the message sent did not show within $TIMEOUT_MS ms.")
}

/**
 * Deletes the message of [bubble] through its actions: a long press, "Delete", then the dialog's "Delete". The
 * journeys leave nothing behind in Saved Messages.
 */
internal fun MacrobenchmarkScope.deleteMessage(bubble: UiObject2) {
    bubble.longClick()
    device.wait(Until.findObject(By.res(DELETE_ACTION)), TIMEOUT_MS)?.click()
        ?: error("No \"Delete\" in the message's actions.")
    device.wait(Until.findObject(By.res(DELETE_CONFIRM)), TIMEOUT_MS)?.click()
        ?: error("No confirmation to delete the message.")
    device.waitForIdle()
}

/** Flings the conversation [list] up to older messages, loading them on the way, and back down. */
internal fun MacrobenchmarkScope.scrollConversation(list: UiObject2) {
    fling(list, Direction.UP)
    fling(list, Direction.DOWN)
}

/** [FLINGS] flings of [list] in [direction], each followed by a wait until the app is idle. */
private fun MacrobenchmarkScope.fling(
    list: UiObject2,
    direction: Direction,
) {
    // Clear of the system's back gesture along the sides.
    list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_FRACTION)
    repeat(FLINGS) {
        list.fling(direction)
        device.waitForIdle()
    }
}

// The tags in feature:chats (ChatListScreen.kt) and feature:chat (ChatScreen.kt, ComposerField.kt, ComposerButtons.kt,
// MessageActionsMenu.kt, DeleteMessagesAlert.kt).
private const val CHAT_LIST = "chat_list"
private const val SAVED_MESSAGES = "saved_messages"
private const val MESSAGE_LIST = "message_list"
private const val COMPOSER_FIELD = "composer_field"
private const val SEND_BUTTON = "composer_send"
private const val DELETE_ACTION = "message_action_Delete"
private const val DELETE_CONFIRM = "delete_confirm"

/** How long the journeys wait for a screen: TDLib may still be loading after a cold start. */
private const val TIMEOUT_MS = 10_000L

private const val FLINGS = 3
private const val BACK_PRESSES = 3
private const val GESTURE_MARGIN_FRACTION = 5

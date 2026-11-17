package dev.kyralabs.pingogram.benchmark

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityWindowInfo
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/*
 * Sending and deleting a message in the open conversation, for SendMessageBenchmark (`docs/PERFORMANCE.md` §9).
 */

/** Types [text] into the open conversation's composer, ready to send. */
internal fun MacrobenchmarkScope.typeMessage(text: String) {
    val field =
        device.wait(Until.findObject(By.res(COMPOSER_FIELD)), TIMEOUT_MS)
            ?: error("No composer after $TIMEOUT_MS ms.")
    field.text = text
    device.wait(Until.hasObject(By.res(SEND_BUTTON)), TIMEOUT_MS)
}

/** Taps send and waits for the bubble of [text] to show. */
internal fun MacrobenchmarkScope.sendTyped(text: String) {
    device.findObject(By.res(SEND_BUTTON))?.click() ?: error("No send button: type a message first.")
    if (!device.wait(Until.hasObject(By.descContains(text)), TIMEOUT_MS)) {
        error("The bubble of the message sent did not show within $TIMEOUT_MS ms.")
    }
}

/**
 * Deletes the message whose bubble shows [text] through its actions: a long press, "Delete", then the dialog's
 * "Delete". The journeys leave nothing behind in Saved Messages.
 *
 * The keyboard goes down first: the composer keeps the focus after a send, and with the keyboard up the window is too
 * short for the floating menu to show "Delete" (it moves the message into view and scrolls the rest). Where "Delete"
 * is still out of sight, the menu is scrolled to it. The bubble is found after that, once it has stopped moving
 * ([settledBubble]).
 */
internal fun MacrobenchmarkScope.deleteMessage(text: String) {
    hideKeyboard()
    settledBubble(text).longClick()
    deleteAction().click()
    device.wait(Until.findObject(By.res(DELETE_CONFIRM)), TIMEOUT_MS)?.click()
        ?: error("No confirmation to delete the message.")
    device.waitForIdle()
}

/**
 * The bubble of [text] in the open conversation's messages, found again until it shows at the same place twice
 * [SETTLE_STEP_MS] apart. After a send the list scrolls the message into view and the keyboard's insets animate it
 * down: a long press on a row that moves under the finger reads as a scroll, which cancels it (`LongPress.kt`), and
 * the row's node is replaced on the way, so an object found earlier is stale. Only the message list is searched: the
 * chat list's Saved Messages row shows the same text as its last message.
 */
private fun MacrobenchmarkScope.settledBubble(text: String): UiObject2 {
    var lastBounds: Rect? = null
    repeat(SETTLE_TRIES) {
        val bubble =
            messageList().wait(Until.findObject(By.descContains(text)), TIMEOUT_MS)
                ?: error("The bubble of the message to delete is gone.")
        val bounds = bubble.visibleBounds
        if (bounds == lastBounds) return bubble
        lastBounds = bounds
        SystemClock.sleep(SETTLE_STEP_MS)
    }
    error("The bubble of the message to delete kept moving for ${SETTLE_TRIES * SETTLE_STEP_MS} ms.")
}

/** "Delete" in the open message actions, scrolled into view where the menu has no room for it. */
private fun MacrobenchmarkScope.deleteAction(): UiObject2 {
    device.wait(Until.findObject(By.res(DELETE_ACTION)), SHORT_TIMEOUT_MS)?.let { return it }
    val menu =
        device.wait(Until.findObject(By.res(MESSAGE_ACTIONS)), TIMEOUT_MS)
            ?: error("The message's actions did not open within $TIMEOUT_MS ms.")
    return menu.scrollUntil(Direction.DOWN, Until.findObject(By.res(DELETE_ACTION)))
        ?: error("No \"Delete\" in the message's actions.")
}

/**
 * Presses back once if the keyboard's window shows, which only hides it. Back with no keyboard would leave the
 * conversation, so the keyboard counts as shown only once two looks [SETTLE_STEP_MS] apart agree: one closing as the
 * message goes out may still have its window.
 */
private fun MacrobenchmarkScope.hideKeyboard() {
    if (keyboardShown()) {
        device.pressBack()
        device.waitForIdle()
    }
}

/** Whether the keyboard's window shows, once two looks [SETTLE_STEP_MS] apart agree. */
private fun MacrobenchmarkScope.keyboardShown(): Boolean {
    var shown = device.hasWindow(KEYBOARD)
    repeat(SETTLE_TRIES) {
        SystemClock.sleep(SETTLE_STEP_MS)
        val now = device.hasWindow(KEYBOARD)
        if (now == shown) return now
        shown = now
    }
    return shown
}

// The tags in feature:chat (ComposerField.kt, ComposerButtons.kt, MessageActionsMenu.kt,
// MessageActionsBottomSheet.kt, DeleteMessagesAlert.kt).
private const val COMPOSER_FIELD = "composer_field"
private const val SEND_BUTTON = "composer_send"
private const val DELETE_ACTION = "message_action_Delete"
private const val DELETE_CONFIRM = "delete_confirm"
private const val MESSAGE_ACTIONS = "message_actions"

/** How long the journeys wait for a screen, as Journeys.kt. */
private const val TIMEOUT_MS = 10_000L

/** How long to wait for what usually shows at once, before trying another way. */
private const val SHORT_TIMEOUT_MS = 2_000L

/** How often, and how many times, [settledBubble] and [hideKeyboard] look before they act. */
private const val SETTLE_STEP_MS = 150L
private const val SETTLE_TRIES = 20

/** The keyboard's window, which UiAutomator lists while it shows. */
private val KEYBOARD = By.Window.type(AccessibilityWindowInfo.TYPE_INPUT_METHOD)

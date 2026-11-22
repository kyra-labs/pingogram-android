package dev.kyralabs.pingogram.benchmark

import android.graphics.Rect
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import java.util.regex.Pattern

/*
 * What the benchmarks and the Baseline Profile generator do in the app. Past the start, they need an account: sign in
 * on the device first, in the build under test (`docs/PERFORMANCE.md` §9). They find the screens by test tags, which
 * `MainActivity` shows as resource ids.
 */

/** Waits for the chat list, which only a signed-in app shows. */
internal fun MacrobenchmarkScope.waitForChatList(): UiObject2 =
    device.wait(Until.findObject(By.res(CHAT_LIST)), TIMEOUT_MS)
        ?: screenMissing(
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
 * Goes back to the chat list from wherever the app is (a conversation, the Bookmarks page or Catch-up the last
 * iteration opened), up to [BACK_PRESSES] times. It first waits for one of those screens or the chat list, and presses
 * back only while one of those screens shows and the chat list does not: right after a start the app may still be
 * loading (slower without a profile), and back on the chat list's screen would close it. The chat list shows while a
 * conversation slides away after a back (the panes animate) and beside it on a wide window, so after each press it
 * waits a moment for the chat list instead of pressing again on a conversation that is leaving.
 */
internal fun MacrobenchmarkScope.returnToChatList() {
    var presses = 0
    while (presses < BACK_PRESSES) {
        device.wait(Until.hasObject(By.res(KNOWN_SCREEN)), TIMEOUT_MS)
        if (device.hasObject(By.res(CHAT_LIST)) || !device.hasObject(By.res(SCREEN_ABOVE_CHAT_LIST))) break
        device.pressBack()
        device.wait(Until.hasObject(By.res(CHAT_LIST)), BACK_WAIT_MS)
        presses++
    }
    waitForChatList()
}

/** The open conversation's messages, found afresh: a list found in an earlier block may be stale. */
internal fun MacrobenchmarkScope.messageList(): UiObject2 =
    device.wait(Until.findObject(By.res(MESSAGE_LIST)), TIMEOUT_MS)
        ?: screenMissing("No conversation open after $TIMEOUT_MS ms.")

/**
 * What [find] returns once it shows at the same place for [stillLooks] looks in a row after the first, [SETTLE_STEP_MS]
 * apart, found again each time, or null once [find] finds nothing. A tap or a long press on something still moving
 * under the finger reads as a scroll, and one on a node the app has replaced meanwhile throws `StaleObjectException`.
 */
internal fun settledObject(
    stillLooks: Int = 1,
    find: () -> UiObject2?,
): UiObject2? {
    var found = find()
    var bounds: Rect? = found?.visibleBounds
    var still = 0
    var looks = 1
    while (found != null && looks < SETTLE_TRIES) {
        SystemClock.sleep(SETTLE_STEP_MS)
        val again = find()
        val againBounds = again?.visibleBounds
        still = if (againBounds == bounds) still + 1 else 0
        if (still >= stillLooks) return again
        found = again
        bounds = againBounds
        looks++
    }
    return found
}

/**
 * Fails with [message], first saving what the screen shows next to the run's traces, which Gradle copies into
 * `connected_android_test_additional_output`: a journey that cannot find a screen says what it found instead.
 */
internal fun MacrobenchmarkScope.screenMissing(message: String): Nothing {
    val directory = InstrumentationRegistry.getArguments().getString(OUTPUT_DIRECTORY_ARGUMENT)
    val screenshot = directory?.let { File(it, "journey-failure-${System.currentTimeMillis()}.png") }
    if (screenshot != null && device.takeScreenshot(screenshot)) error("$message Screenshot: ${screenshot.name}.")
    error(message)
}

/**
 * Flings the open conversation up to older messages, loading them on the way, and back down, within the part of the
 * list the messages show in ([setMessageGestureMargins]).
 */
internal fun MacrobenchmarkScope.scrollConversation() {
    fling(Direction.UP, margins = { setMessageGestureMargins(it) }) { messageList() }
    fling(Direction.DOWN, margins = { setMessageGestureMargins(it) }) { messageList() }
}

/**
 * [FLINGS] flings in [direction] of the list [find] returns, each followed by a wait until the app is idle, inside the
 * [margins] set on it: by default clear of the system's back gesture along the sides. The list is found again before
 * each fling: scrolling can replace its node, and a fling on the old one throws `StaleObjectException`.
 */
internal fun MacrobenchmarkScope.fling(
    direction: Direction,
    margins: (UiObject2) -> Unit = { it.setGestureMargin(device.displayWidth / GESTURE_MARGIN_FRACTION) },
    find: () -> UiObject2,
) {
    repeat(FLINGS) {
        val list = find()
        margins(list)
        list.fling(direction)
        device.waitForIdle()
    }
}

/**
 * Margins for a gesture on the open conversation's [list] that keep it where the messages show. The list starts below
 * the top bar (the sheet it scrolls in does, `ContentSheet`), but reaches the window's bottom: the composer floats over
 * its end, and a gesture starting on it would focus or scroll the field instead of the messages. So the bottom margin
 * clears the composer's field by the side margin, clear of the system's back gesture (or a [COVERED_FRACTION] of the
 * list where it shows none), the sides and the top keep that margin, and at least a [MIN_GESTURE_FRACTION] of the list
 * is left for the gesture.
 */
private fun MacrobenchmarkScope.setMessageGestureMargins(list: UiObject2) {
    val side = device.displayWidth / GESTURE_MARGIN_FRACTION
    val bounds = list.visibleBounds
    val composerTop = device.findObject(By.res(COMPOSER_FIELD))?.visibleBounds?.top
    val covered = composerTop?.let { bounds.bottom - it } ?: (bounds.height() / COVERED_FRACTION)
    val bottom = (covered + side).coerceAtMost(bounds.height() - side - bounds.height() / MIN_GESTURE_FRACTION)
    list.setGestureMargins(side, side, side, bottom.coerceAtLeast(side))
}

// The tags in feature:chats (ChatListScreen.kt) and feature:chat (ChatScreen.kt, ComposerField.kt).
private const val CHAT_LIST = "chat_list"
private const val SAVED_MESSAGES = "saved_messages"
private const val MESSAGE_LIST = "message_list"
private const val COMPOSER_FIELD = "composer_field"

/** How long the journeys wait for a screen: TDLib may still be loading after a cold start. */
private const val TIMEOUT_MS = 10_000L

/** How long [returnToChatList] waits for the chat list after a back before it looks again. */
private const val BACK_WAIT_MS = 2_000L

private const val FLINGS = 3
private const val BACK_PRESSES = 3
private const val GESTURE_MARGIN_FRACTION = 5

/** How much of a conversation's list the composer is taken to cover where its field does not show: a quarter. */
private const val COVERED_FRACTION = 4

/** How much of a list a gesture keeps at least, whatever its margins: a third. */
private const val MIN_GESTURE_FRACTION = 3

/** How often, and how many times, [settledObject] and the keyboard check (MessageJourneys.kt) look before they act. */
internal const val SETTLE_STEP_MS = 150L
internal const val SETTLE_TRIES = 20

/**
 * The screens [returnToChatList] knows: the chat list, and those it goes back from (with the tags of feature:chat's
 * BookmarksScreen.kt and feature:chats' CatchUpCard.kt, as ToolJourneys.kt).
 */
private val KNOWN_SCREEN: Pattern = Pattern.compile("chat_list|message_list|bookmarks_list|catch_up_card")
private val SCREEN_ABOVE_CHAT_LIST: Pattern = Pattern.compile("message_list|bookmarks_list|catch_up_card")

/** The instrumentation argument naming where a test's extra files go, which Gradle collects after the run. */
private const val OUTPUT_DIRECTORY_ARGUMENT = "additionalTestOutputDir"

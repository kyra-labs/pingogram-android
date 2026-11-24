package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/*
 * Search, the Settings tab and a profile, for the Baseline Profile (`docs/PERFORMANCE.md` §2). Each one is skipped,
 * returning false, where the screen does not show, so an account or a build without it still yields a profile. None
 * reads a chat.
 */

/** Opens search from the chat list's header, types [query], lets the results come and closes it again. */
internal fun MacrobenchmarkScope.searchChats(query: String): Boolean {
    val button = device.wait(Until.findObject(By.res(CHAT_LIST_SEARCH)), TIMEOUT_MS) ?: return false
    button.click()
    val field = device.wait(Until.findObject(By.clazz(EDIT_TEXT)), TIMEOUT_MS)
    field?.text = query
    device.wait(Until.hasObject(By.scrollable(true)), RESULTS_WAIT_MS)
    device.pressBack()
    device.pressBack()
    waitForChatList()
    return field != null
}

/** Opens the Settings tab from the header's account button, scrolls its page down and up, and goes back to Chats. */
internal fun MacrobenchmarkScope.browseSettings(): Boolean {
    val account = device.wait(Until.findObject(By.res(CHAT_LIST_ACCOUNT)), TIMEOUT_MS) ?: return false
    account.click()
    val home = device.wait(Until.findObject(By.res(SETTINGS_HOME)), TIMEOUT_MS)
    home?.findObject(By.scrollable(true))?.let { page ->
        page.setGestureMargin(device.displayWidth / GESTURE_MARGIN_FRACTION)
        page.fling(Direction.DOWN)
        page.fling(Direction.UP)
    }
    device.pressBack()
    waitForChatList()
    return home != null
}

/**
 * Opens the profile from the open conversation's title, scrolls it and goes back to the conversation; false where the
 * title opens nothing (Saved Messages may not) or the profile does not show.
 */
internal fun MacrobenchmarkScope.browseProfileFromConversation(): Boolean {
    val title = device.wait(Until.findObject(By.res(CHAT_TITLE)), TIMEOUT_MS) ?: return false
    title.click()
    val grid = device.wait(Until.findObject(By.res(PROFILE_GRID)), PROFILE_WAIT_MS) ?: return false
    grid.setGestureMargin(device.displayWidth / GESTURE_MARGIN_FRACTION)
    grid.fling(Direction.DOWN)
    grid.fling(Direction.UP)
    device.pressBack()
    device.wait(Until.hasObject(By.res(MESSAGE_LIST)), TIMEOUT_MS)
    return true
}

// The tags in feature:chats (ChatListHeaderButtons.kt), feature:settings (SettingsScreen.kt), feature:chat
// (ChatTitle.kt, ChatScreen.kt) and feature:profile (ProfileScreen.kt).
private const val CHAT_LIST_SEARCH = "chat_list_search"
private const val CHAT_LIST_ACCOUNT = "chat_list_account"
private const val SETTINGS_HOME = "settings_home"
private const val CHAT_TITLE = "chat_title"
private const val PROFILE_GRID = "profile_grid"
private const val MESSAGE_LIST = "message_list"

/** A text field, as Compose reports one to accessibility. */
private const val EDIT_TEXT = "android.widget.EditText"

private const val TIMEOUT_MS = 10_000L

/** How long a search waits for its results, which come from TDLib's local search. */
private const val RESULTS_WAIT_MS = 3_000L

/** How long a profile is waited for, short: Saved Messages' title may open none. */
private const val PROFILE_WAIT_MS = 3_000L
private const val GESTURE_MARGIN_FRACTION = 5

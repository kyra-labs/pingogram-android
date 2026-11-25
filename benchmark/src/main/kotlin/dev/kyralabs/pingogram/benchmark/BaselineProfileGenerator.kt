package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects the Baseline Profile and Startup Profile that `:app` ships (`docs/PERFORMANCE.md` §2). Run it through
 * `tools/benchmark/generate-profiles.sh` (`.ps1` on Windows), which needs a device on API 33+ (or a rooted one on
 * API 28+) with the app signed in (§9), and commit the result in `app/src/main/generated/baselineProfiles/`. The
 * script deletes the committed profiles first: collected from an APK that embeds one, every profile inherits it.
 *
 * The startup profile covers the start up to the chat list; the Baseline Profile adds scrolling the chat list,
 * opening and scrolling a conversation, and opening it again from the chat list with its history cached, as
 * `OpenChatBenchmark` does; Saved Messages' newest photo in the media viewer (where it has one), a message sent and
 * deleted there, and its profile (where its title opens one); search and the Settings tab; then Catch-up's cards and
 * the Bookmarks page, where the account has them (at least five unread chats that are not muted, and a bookmarked
 * message), reading no chat on the way; then Recent calls and the story rings, and the user's own stories in the
 * viewer, where the account has them, placing no call and opening no one else's story.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() {
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            waitForChatList()
        }
    }

    @Test
    fun chatListAndConversation() {
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            scrollChatList()
            openSavedMessages()
            showAllSavedMessages()
            scrollConversation()
            returnToChatList()
            openSavedMessages()
            returnToChatList()
        }
    }

    @Test
    fun mediaComposerAndProfile() {
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            openSavedMessages()
            showAllSavedMessages()
            if (photoOnScreen()) {
                openNewestPhoto()
                closeMediaViewer()
            }
            // Sent to Saved Messages and deleted again, as SendMessageBenchmark does.
            val text = "Baseline Profile ${System.nanoTime()}"
            typeMessage(text)
            sendTyped(text)
            deleteMessage(text)
            browseProfileFromConversation()
            returnToChatList()
        }
    }

    @Test
    fun searchAndSettings() {
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            waitForChatList()
            searchChats(SEARCH_QUERY)
            browseSettings()
        }
    }

    @Test
    fun catchUpAndBookmarks() {
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            if (openCatchUp() != null) {
                skipCatchUpCards()
                returnToChatList()
            }
            if (openBookmarks() != null) scrollBookmarks()
            returnToChatList()
        }
    }

    @Test
    fun callsAndStories() {
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            if (openRecentCalls() != null) {
                scrollRecentCalls()
                closeRecentCalls()
            }
            if (storyRings() != null) {
                scrollStoryRings()
                openOwnStories()
            }
        }
    }

    private companion object {
        /** A search that finds something in most accounts: a common letter. */
        const val SEARCH_QUERY = "a"
    }
}

package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects the Baseline Profile and Startup Profile that `:app` ships (`docs/PERFORMANCE.md` §2). Run it through
 * `./gradlew :app:generateBaselineProfile`, which needs a device on API 33+ (or a rooted one on API 28+) with the app
 * signed in (§9), and commit the result in `app/src/main/generated/baselineProfiles/`.
 *
 * The startup profile covers the start up to the chat list; the Baseline Profile adds scrolling the chat list,
 * opening and scrolling a conversation, and opening it again from the chat list with its history cached, as
 * `OpenChatBenchmark` does.
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
            scrollConversation(openSavedMessages())
            returnToChatList()
            openSavedMessages()
            returnToChatList()
        }
    }
}

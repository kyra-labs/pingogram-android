package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.archive.ArchiveSettingsScreen
import dev.kyralabs.pingogram.feature.chats.ui.catchup.CatchUpPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.catchup.CatchUpScreen
import dev.kyralabs.pingogram.feature.chats.ui.catchup.CatchUpStage
import dev.kyralabs.pingogram.feature.chats.ui.catchup.CatchUpUiState
import dev.kyralabs.pingogram.feature.chats.ui.create.CreateChatScreenshotTest
import dev.kyralabs.pingogram.feature.chats.ui.drafts.DraftsScreen
import dev.kyralabs.pingogram.feature.chats.ui.mentions.MentionsScreen
import dev.kyralabs.pingogram.feature.chats.ui.preview.ArchiveSettingsPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.preview.DraftsPreviewStates
import dev.kyralabs.pingogram.feature.chats.ui.preview.MentionsPreviewStates
import dev.kyralabs.pingogram.feature.chats.ui.preview.SearchPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsRow
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryUploadContent
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryUploadSheetScreenshotTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every screen of the Chats tab's module in every state its goldens show: each control has a name and a 48 dp touch
 * target ([assertAccessible], `docs/audit/accessibility.md`), on a compact phone, where targets are tightest, at the
 * default font scale and at 200%.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatsAccessibilityChecksTest(
    private val screenName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun defaultFontScale() = check()

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = check()

    private fun check() {
        val screen = SCREENS.getValue(screenName)
        composeRule.setContent { PingogramTheme(dynamicColor = false) { screen() } }
        composeRule.waitForIdle()
        // Past the loading indicators' delay, so a screen still loading shows what it shows then.
        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds)
        composeRule.waitForIdle()
        composeRule.assertAccessible()
    }

    companion object {
        private fun search(state: SearchUiState): @Composable () -> Unit =
            {
                SearchScreen(
                    state = state,
                    textFieldState = remember { TextFieldState(SearchPreviewData.queryOf(state)) },
                    onEvent = {},
                    onBack = {},
                )
            }

        private val SCREENS: Map<String, @Composable () -> Unit> =
            buildMap {
                MentionsPreviewStates().values.forEachIndexed { index, state ->
                    put("mentions.$index") { MentionsScreen(state = state, onEvent = {}, onBack = {}) }
                }
                DraftsPreviewStates().values.forEachIndexed { index, state ->
                    put("drafts.$index") { DraftsScreen(state = state, onEvent = {}, onBack = {}) }
                }
                ChatListScreenScreenshotTest.STATES.forEach { (name, state) ->
                    put("chatList.$name") { ChatListScreen(state = state, onEvent = {}) }
                }
                put("chatList.storyRings") {
                    ChatListScreen(
                        state = ChatListPreviewData.contentWithArchive,
                        onEvent = {},
                        stories = { modifier ->
                            StoryRingsRow(
                                rings = StoryRingsPreviewData.rings,
                                onOpen = {},
                                onNearEnd = {},
                                modifier = modifier,
                            )
                        },
                    )
                }
                put("chatList.storyPosting") {
                    ChatListScreen(
                        state = ChatListPreviewData.contentWithArchive,
                        onEvent = {},
                        stories = { modifier ->
                            StoryRingsRow(
                                rings = StoryRingsPreviewData.posting,
                                onOpen = {},
                                onNearEnd = {},
                                modifier = modifier,
                            )
                        },
                    )
                }
                StoryUploadSheetScreenshotTest.STATES.forEach { (name, state) ->
                    put("storyUpload.$name") { StoryUploadContent(state.upload, state.cancel, state.canView, {}) }
                }
                NotificationPromptScreenshotTest.STATES.forEach { (name, state) ->
                    put("notificationPrompt.$name") {
                        ChatListScreen(
                            state = ChatListPreviewData.content,
                            onEvent = {},
                            header = { NotificationPromptCard(state = state, onEvent = {}) },
                        )
                    }
                }
                put("search.suggestions", search(SearchPreviewData.suggestions))
                put("search.hint", search(SearchPreviewData.hint))
                put("search.results", search(SearchPreviewData.results))
                put("search.loading", search(SearchPreviewData.loading))
                put("search.error", search(SearchPreviewData.error))
                put("search.noResults", search(SearchPreviewData.noResults))
                put("search.media", search(SearchPreviewData.media))
                NewChatScreenScreenshotTest.STATES.forEach { (name, state) ->
                    put("newChat.$name") { NewChatScreen(state = state, onEvent = {}, onBack = {}) }
                }
                CreateChatScreenshotTest.SCREENS.forEach { (name, screen) -> put("create.$name", screen) }
                with(ArchiveSettingsPreviewData) {
                    listOf("content" to content, "unavailable" to unavailable, "loading" to loading, "error" to error)
                }.forEach { (name, state) ->
                    put("archiveSettings.$name") { ArchiveSettingsScreen(state = state, onEvent = {}, onBack = {}) }
                }
                with(CatchUpPreviewData) {
                    listOf(
                        "cards" to cards,
                        "replying" to replying,
                        "done" to done,
                        "empty" to CatchUpUiState(CatchUpStage.Empty),
                    )
                }.forEach { (name, state) ->
                    put("catchUp.$name") { CatchUpScreen(state = state, onEvent = {}, onBack = {}) }
                }
                put("chatList.catchUpChip") {
                    ChatListScreen(
                        state = ChatListPreviewData.content,
                        onEvent = {},
                        summary = { ChatListPreviewData.summary.copy(catchUpChats = 12) },
                    )
                }
            }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = SCREENS.keys.map { arrayOf(it) }
    }
}

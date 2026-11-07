package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.PinnedPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The conversation in the experimental look (`docs/DESIGN_SYSTEM.md` §13.5): a group's centred top bar with its cluster
 * of the people who wrote last and its online members, the pill-shaped pinned bar, "Jump to mention" and the composer
 * with its attach and camera buttons; a channel's mute toggle and menu; a private chat's call button. Every state in
 * light and dark, the group in the audit's other looks and at 200% font.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ExperimentalChatScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "group")
        capture(ScreenshotVariant.Light, "fontScale200")
    }

    @Test
    fun connectingLight() {
        assumeTrue(stateName == "group")
        capture(ScreenshotVariant.Light, "connectingLight", ConnectionStatus.Connecting)
    }

    private fun captureAudit(variant: ScreenshotVariant) {
        assumeTrue(stateName == "group")
        capture(variant)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
        connection: ConnectionStatus = ConnectionStatus.Ready,
    ) {
        val state = STATES.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                CompositionLocalProvider(LocalExperimentalUi provides true) {
                    ChatScreen(state = state, onEvent = {}, onBack = {}, connection = connection)
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        private val members =
            persistentListOf(
                HeaderMember(1, UiText.Plain("Grace Hopper"), null),
                HeaderMember(2, UiText.Plain("Linus Torvalds"), null),
                HeaderMember(3, UiText.Plain("Ada Lovelace"), null),
                HeaderMember(4, UiText.Plain("Alan Turing"), null),
                HeaderMember(5, UiText.Plain("Hedy Lamarr"), null),
            )

        private val group =
            PinnedPreviewData.groupWithPins.let { state ->
                state.copy(
                    header =
                        state.header?.copy(
                            subtitle = ChatSubtitle.Status(UiText.Plain("1,248 members")),
                            opensChatInfo = true,
                            avatarShape = AvatarShape.Group,
                            onlineCount = 36,
                            members = members,
                        ),
                    unreadMentionCount = 2,
                )
            }

        private val channel =
            ChatPreviewData.channel.let { state ->
                state.copy(
                    header =
                        state.header?.copy(
                            opensChatInfo = true,
                            isChannel = true,
                            isMuted = true,
                            avatarShape = AvatarShape.Channel,
                        ),
                )
            }

        private val private =
            ChatPreviewData.privateChat.let { state ->
                state.copy(header = state.header?.copy(callUserId = null))
            }

        val STATES = mapOf("group" to group, "channel" to channel, "privateChat" to private)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}

package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.SenderHues
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.PinnedPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
    fun highContrastTinted() {
        assumeTrue(stateName == "groupTinted")
        capture(ScreenshotVariant.HighContrast)
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
        private const val MENTION_LENGTH = 4

        private val MENTION =
            FormattedText(
                "@ada can you add him to Phone reports?",
                persistentListOf(TextEntity(0, MENTION_LENGTH, TextEntityType.Mention)),
            )

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
                    groupMemberCount = SMALL_GROUP,
                    content = (state.content as ChatContent.Content).withHues(),
                    typing =
                        TypingBubble(
                            userId = 1,
                            name = UiText.Plain("Grace Hopper"),
                            photo = null,
                            hue = 2,
                            text = UiText.Plain("Grace is typing"),
                        ),
                )
            }

        /** The group in a larger group's tinted bubbles, names inside in their hue's ink. */
        private val groupTinted = group.copy(groupMemberCount = LARGE_GROUP, typing = null)

        /** Incoming bubbles in their senders' hues, the first with a role, one mentioning the user. */
        private fun ChatContent.Content.withHues(): ChatContent.Content {
            var first = true
            return copy(
                items =
                    items
                        .map { item ->
                            if (item !is ChatItem.Bubble || item.isOutgoing) return@map item
                            val role = if (first) UiText.Plain("Admin") else null
                            first = false
                            item.copy(
                                senderHue = SenderHues.indexOf(-1, item.senderId),
                                senderRole = role,
                                mentionsMe = item.reply != null,
                                // The mention of the user as the experimental look's chip.
                                text = if (item.reply != null) MENTION else item.text,
                                myMentions =
                                    if (item.reply !=
                                        null
                                    ) {
                                        persistentListOf(0 until MENTION_LENGTH)
                                    } else {
                                        item.myMentions
                                    },
                                reply =
                                    item.reply?.let {
                                        it.copy(
                                            senderHue = SenderHues.indexOf(-1, it.senderId ?: 0),
                                        )
                                    },
                            )
                        }.toImmutableList(),
            )
        }

        private const val SMALL_GROUP = 12
        private const val LARGE_GROUP = 1_248

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

        /** A channel's posts with their views, the unread divider counting them, and four files sent together. */
        private val channelPosts =
            channel.copy(
                content =
                    ChatContent.Content(
                        items =
                            persistentListOf(
                                file(
                                    14,
                                    "app-foss-x86_64-debug.apk",
                                    GroupPosition.Last,
                                    caption = "Debug build, all ABIs",
                                ),
                                file(13, "app-foss-armeabi-v7a-debug.apk", GroupPosition.Middle),
                                file(12, "app-foss-arm64-v8a-debug.apk", GroupPosition.Middle),
                                file(11, "app-foss-universal-debug.apk", GroupPosition.First),
                                ChatItem.UnreadDivider(UiText.Plain("Today"), count = 4, isChannel = true),
                                ChatPreviewData
                                    .bubble(
                                        10,
                                        "$HEADLINE\nfeat(chats): chat folders slide like a pager",
                                        "2:18 PM",
                                        entities = listOf(TextEntity(0, HEADLINE.length, TextEntityType.Bold)),
                                    ).copy(
                                        isChannelPost = true,
                                        views = 12_480,
                                        hasShareButton = true,
                                        headlineLength = HEADLINE.length,
                                    ),
                                ChatItem.DateSeparator(epochDay = 1, date = UiText.Plain("Today")),
                            ),
                        reachedStart = true,
                        isLoadingOlder = false,
                        olderError = null,
                    ),
            )

        private fun file(
            id: Long,
            name: String,
            position: GroupPosition,
            caption: String = "",
        ) = ChatPreviewData
            .bubble(id, caption, "2:28 PM", position = position, content = ContentPreviewData.document(name, MIME_APK))
            .copy(isChannelPost = true, views = 1, showsTime = position == GroupPosition.Last)

        private const val HEADLINE = "36dea27"

        /** A group where Grace and Linus shout one word each (§13.6). */
        private val groupShouts =
            group.copy(
                pinnedBar = null,
                typing = null,
                unreadMentionCount = 0,
                content =
                    ChatContent.Content(
                        items =
                            persistentListOf(
                                shout(23, "SHIP!", senderId = 2, name = "Linus Torvalds", GroupPosition.Single),
                                ChatPreviewData.bubble(22, "We can ship it tonight", "9:41 PM", isOutgoing = true),
                                shout(21, "YES", senderId = 1, name = "Grace Hopper", GroupPosition.Single),
                                ChatPreviewData
                                    .bubble(
                                        20,
                                        "Is the release ready?",
                                        "9:40 PM",
                                        sender = UiText.Plain("Grace Hopper"),
                                        senderId = 1,
                                        groupChat = true,
                                    ).copy(senderHue = SenderHues.indexOf(-1, 1), hasAvatarColumn = true),
                                ChatItem.DateSeparator(epochDay = 1, date = UiText.Plain("Today")),
                            ),
                        reachedStart = true,
                        isLoadingOlder = false,
                        olderError = null,
                    ),
            )

        private fun shout(
            id: Long,
            text: String,
            senderId: Long,
            name: String,
            position: GroupPosition,
        ) = ChatPreviewData
            .bubble(id, text, "9:41 PM", sender = UiText.Plain(name), senderId = senderId, groupChat = true)
            .copy(
                isShout = true,
                senderHue = SenderHues.indexOf(-1, senderId),
                hasAvatarColumn = true,
                position = position,
            )

        private const val MIME_APK = "application/vnd.android.package-archive"

        private val private =
            ChatPreviewData.privateChat.let { state ->
                state.copy(header = state.header?.copy(callUserId = null))
            }

        val STATES =
            mapOf(
                "group" to group,
                "groupTinted" to groupTinted,
                "channel" to channel,
                "channelPosts" to channelPosts,
                "groupShouts" to groupShouts,
                "privateChat" to private,
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}

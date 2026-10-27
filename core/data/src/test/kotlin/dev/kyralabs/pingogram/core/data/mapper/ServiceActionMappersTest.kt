package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.CallDiscardReason
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

class ServiceActionMappersTest {
    @Test
    fun `service messages map to their action`() {
        val actions =
            listOf(
                TdApi.MessageBasicGroupChatCreate("Team", longArrayOf(42, 43)),
                TdApi.MessageChatUpgradeTo(500),
                TdApi.MessageChatUpgradeFrom("Team", 400),
                TdApi.MessageChatChangeTitle("New team"),
                TdApi.MessageChatDeletePhoto(),
                TdApi.MessageChatJoinByLink(),
                TdApi.MessageChatJoinByRequest(),
                TdApi.MessagePinMessage(9),
                TdApi.MessageScreenshotTaken(),
                TdApi.MessageVideoChatScheduled(1, 1_700_003_600),
                TdApi.MessageVideoChatStarted(1),
                TdApi.MessageVideoChatEnded(1_800),
                TdApi.MessageInviteVideoChatParticipants(1, longArrayOf(43, 44)),
                TdApi.MessageChatSetMessageAutoDeleteTime(86_400, 0),
                TdApi.MessageContactRegistered(),
                TdApi.MessageCustomServiceAction("Something happened"),
            ).map { it.action() }

        assertEquals(
            listOf(
                ServiceAction.BasicGroupCreated("Team", persistentListOf(42, 43)),
                ServiceAction.UpgradedToSupergroup(500),
                ServiceAction.UpgradedFromBasicGroup("Team", 400),
                ServiceAction.TitleChanged("New team"),
                ServiceAction.PhotoDeleted,
                ServiceAction.MemberJoinedByLink,
                ServiceAction.MemberJoinedByRequest,
                ServiceAction.MessagePinned(9),
                ServiceAction.ScreenshotTaken,
                ServiceAction.VideoChatScheduled(1_700_003_600),
                ServiceAction.VideoChatStarted,
                ServiceAction.VideoChatEnded(1_800),
                ServiceAction.VideoChatInvited(persistentListOf(43, 44)),
                ServiceAction.AutoDeleteTimerChanged(86_400, 0),
                ServiceAction.ContactRegistered,
                ServiceAction.Custom("Something happened"),
            ),
            actions,
        )
    }

    @Test
    fun `a new supergroup in a channel is a new channel`() {
        val create = TdApi.MessageSupergroupChatCreate("News")

        assertEquals(
            listOf(ServiceAction.SupergroupCreated("News"), ServiceAction.ChannelCreated("News")),
            listOf(create.action(isChannelPost = false), create.action(isChannelPost = true)),
        )
    }

    @Test
    fun `a new chat photo carries the photo`() {
        val chatPhoto =
            TdApi.ChatPhoto(
                1,
                0,
                null,
                arrayOf(TdApiFixtures.photoSize("a", 160, 160, TdApiFixtures.file(id = 60))),
                null,
                null,
                null,
            )

        assertEquals(
            ServiceAction.PhotoChanged(Photo(persistentListOf(PhotoSize("a", 160, 160, FileRef(60, 1_024))), null)),
            TdApi.MessageChatChangePhoto(chatPhoto).action(),
        )
    }

    @Test
    fun `members added by the sender, or the sender joining`() {
        val actions =
            listOf(
                TdApiFixtures.messageChatAddMembers(43, 44).action(sender = TdApi.MessageSenderUser(42)),
                TdApiFixtures.messageChatAddMembers(43).action(sender = TdApi.MessageSenderUser(42)),
                TdApiFixtures.messageChatAddMembers(42).action(sender = TdApi.MessageSenderUser(42)),
            )

        assertEquals(
            listOf(
                ServiceAction.MembersAdded(persistentListOf(43, 44)),
                ServiceAction.MembersAdded(persistentListOf(43)),
                ServiceAction.MemberJoined,
            ),
            actions,
        )
    }

    @Test
    fun `a member removed by the sender, or the sender leaving`() {
        val actions =
            listOf(
                TdApi.MessageChatDeleteMember(43).action(sender = TdApi.MessageSenderUser(42)),
                TdApi.MessageChatDeleteMember(42).action(sender = TdApi.MessageSenderUser(42)),
                TdApi.MessageChatDeleteMember(43).action(sender = TdApi.MessageSenderChat(-1001)),
            )

        assertEquals(
            listOf(ServiceAction.MemberRemoved(43), ServiceAction.MemberLeft, ServiceAction.MemberRemoved(43)),
            actions,
        )
    }

    @Test
    fun `calls keep video, duration and why they ended`() {
        val reasons =
            listOf(
                TdApi.CallDiscardReasonEmpty(),
                TdApi.CallDiscardReasonMissed(),
                TdApi.CallDiscardReasonDeclined(),
                TdApi.CallDiscardReasonDisconnected(),
                TdApi.CallDiscardReasonHungUp(),
                TdApi.CallDiscardReasonUpgradeToGroupCall("https://t.me/call/x"),
            )

        assertEquals(
            CallDiscardReason.entries.map { ServiceAction.Call(isVideo = true, duration = 61, discardReason = it) },
            reasons.map { TdApi.MessageCall(0, true, it, 61).action() },
        )
    }

    @Test
    fun `conference call invitations keep whether they go on, were missed, video and duration`() {
        assertEquals(
            listOf(
                ServiceAction.GroupCall(isActive = true, wasMissed = false, isVideo = true, duration = 0),
                ServiceAction.GroupCall(isActive = false, wasMissed = true, isVideo = false, duration = 0),
            ),
            listOf(
                TdApi.MessageGroupCall(7, true, false, true, 0, emptyArray()).action(),
                TdApi.MessageGroupCall(7, false, true, false, 0, emptyArray()).action(),
            ),
        )
    }

    @Test
    fun `every other service message maps to Other`() {
        val actions = OTHER_SERVICE_CONTENTS.map { it.getDeclaredConstructor().newInstance().action() }

        assertEquals(List(OTHER_SERVICE_CONTENTS.size) { ServiceAction.Other }, actions)
    }

    /**
     * Guards TDLib bumps: every content class is mapped to a model, listed as a service message without one
     * (OTHER_SERVICE_CONTENTS), or listed as unsupported regular content (MessageContentMappersTest).
     */
    @Test
    fun `every content class of the pinned TDLib version is classified`() {
        val classified =
            MAPPED_CONTENTS +
                MessageContentMappersTest.UNSUPPORTED_CONTENTS.map { it.simpleName } +
                OTHER_SERVICE_CONTENTS.map { it.simpleName }

        assertEquals(concreteTdApiSubclassesOf(TdApi.MessageContent::class.java), classified.toSet())
        assertEquals(classified.size, classified.toSet().size, "a class is classified twice")
    }

    private fun TdApi.MessageContent.action(
        sender: TdApi.MessageSender = TdApi.MessageSenderUser(42),
        isChannelPost: Boolean = false,
    ) = (
        TdApiFixtures
            .message(
                sender = sender,
                isChannelPost = isChannelPost,
                content = this,
            ).toMessage()
            .content as MessageContent.Service
    ).action

    private fun concreteTdApiSubclassesOf(type: Class<*>): Set<String> =
        TdApi::class.java.declaredClasses
            .filter { type.isAssignableFrom(it) && !Modifier.isAbstract(it.modifiers) }
            .map { it.simpleName }
            .toSet()

    private companion object {
        /** Contents with a model or a service action of their own, each tested in the mapper tests. */
        val MAPPED_CONTENTS =
            listOf(
                "MessageText",
                "MessagePhoto",
                "MessageVideo",
                "MessageAnimation",
                "MessageSticker",
                "MessageVoiceNote",
                "MessageVideoNote",
                "MessageAudio",
                "MessageDocument",
                "MessageLocation",
                "MessageLiveLocation",
                "MessageVenue",
                "MessageContact",
                "MessagePoll",
                "MessageDice",
                "MessageAnimatedEmoji",
                "MessageGame",
                "MessageInvoice",
                "MessageStory",
                "MessageExpiredPhoto",
                "MessageExpiredVideo",
                "MessageExpiredVideoNote",
                "MessageExpiredVoiceNote",
                "MessageBasicGroupChatCreate",
                "MessageSupergroupChatCreate",
                "MessageChatUpgradeTo",
                "MessageChatUpgradeFrom",
                "MessageChatChangeTitle",
                "MessageChatChangePhoto",
                "MessageChatDeletePhoto",
                "MessageChatAddMembers",
                "MessageChatJoinByLink",
                "MessageChatJoinByRequest",
                "MessageChatDeleteMember",
                "MessagePinMessage",
                "MessageScreenshotTaken",
                "MessageCall",
                "MessageGroupCall",
                "MessageVideoChatScheduled",
                "MessageVideoChatStarted",
                "MessageVideoChatEnded",
                "MessageInviteVideoChatParticipants",
                "MessageChatSetMessageAutoDeleteTime",
                "MessageContactRegistered",
                "MessageCustomServiceAction",
            )
    }
}

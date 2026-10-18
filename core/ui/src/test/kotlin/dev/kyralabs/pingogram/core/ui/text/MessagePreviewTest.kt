package dev.kyralabs.pingogram.core.ui.text

import android.content.res.Resources
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.Audio
import dev.kyralabs.pingogram.core.domain.model.CallDiscardReason
import dev.kyralabs.pingogram.core.domain.model.ChatAction
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.Document
import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.ExpiredMedia
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollType
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.model.Venue
import dev.kyralabs.pingogram.core.domain.model.Video
import dev.kyralabs.pingogram.core.domain.model.VideoNote
import dev.kyralabs.pingogram.core.domain.model.VoiceNote
import dev.kyralabs.pingogram.core.domain.model.Waveform
import dev.kyralabs.pingogram.core.ui.R
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Message previews, typing texts and names, resolved to their English strings. */
@RunWith(RobolectricTestRunner::class)
class MessagePreviewTest {
    private val resources: Resources = RuntimeEnvironment.getApplication().resources

    private val names = mapOf(ALICE to "Alice", BOB to "Bob", ME to "Me")

    private fun name(sender: MessageSender): UiText? =
        when (sender) {
            is MessageSender.User -> names[sender.userId]?.let(UiText::Plain)
            is MessageSender.Chat -> null
        }

    private fun preview(
        content: MessageContent,
        sender: MessageSender = MessageSender.User(ALICE),
        isOutgoing: Boolean = false,
    ): String = messagePreviewText(message(content, sender, isOutgoing), ME, ::name).resolve(resources)

    private fun service(
        action: ServiceAction,
        sender: MessageSender = MessageSender.User(ALICE),
        isOutgoing: Boolean = false,
    ) = preview(MessageContent.Service(action), sender, isOutgoing)

    // Regular messages

    @Test
    fun textIsOneLineWithoutFormatting() {
        val text = FormattedText("Hello\n\nworld ", persistentListOf(TextEntity(0, 5, TextEntityType.Bold)))

        assertEquals("Hello world", preview(MessageContent.Text(text, linkPreview = null)))
    }

    @Test
    fun longTextIsCutWithoutSplittingAnEmoji() {
        val text = "a".repeat(PREVIEW_MAX_LENGTH - 1) + "😀" + "b"

        assertEquals("a".repeat(PREVIEW_MAX_LENGTH - 1), preview(MessageContent.Text(text(text), linkPreview = null)))
    }

    @Test
    fun mediaShowsItsKindAndCaption() {
        assertEquals("Photo", preview(MessageContent.Photo(photo, text(""), false, false, false)))
        assertEquals("Photo · Sunset", preview(MessageContent.Photo(photo, text("Sunset"), false, false, false)))
        assertEquals("Video", preview(MessageContent.Video(video, text(""), false, false, false)))
        assertEquals("GIF · lol", preview(MessageContent.Animation(animation, text("lol"), false, false, false)))
        assertEquals("Voice message", preview(MessageContent.VoiceNote(voiceNote, text(""), isListened = false)))
        assertEquals("Video message", preview(MessageContent.VideoNote(videoNote, isViewed = false, isSecret = false)))
    }

    @Test
    fun contentKindLeavesTheCaptionOut() {
        val kinds =
            listOf(
                MessageContent.Photo(photo, text("Sunset"), false, false, false),
                MessageContent.Document(document("report.pdf"), text("Q3")),
                MessageContent.Text(text("Hello"), linkPreview = null),
            ).map { contentKindText(it).resolve(resources) }

        assertEquals(listOf("Photo", "report.pdf", "Hello"), kinds)
    }

    @Test
    fun stickersShowTheirEmoji() {
        assertEquals("😀 Sticker", preview(MessageContent.Sticker(sticker("😀"))))
        assertEquals("Sticker", preview(MessageContent.Sticker(sticker(""))))
    }

    @Test
    fun audioShowsPerformerAndTitleOrItsFileName() {
        assertEquals("Queen – Bohemian Rhapsody", preview(audio(title = "Bohemian Rhapsody", performer = "Queen")))
        assertEquals("Bohemian Rhapsody", preview(audio(title = "Bohemian Rhapsody")))
        assertEquals("track01.mp3", preview(audio(fileName = "track01.mp3")))
        assertEquals("Audio", preview(audio()))
    }

    @Test
    fun documentsShowTheirFileName() {
        assertEquals("report.pdf", preview(MessageContent.Document(document("report.pdf"), text(""))))
        assertEquals("report.pdf · Q3", preview(MessageContent.Document(document("report.pdf"), text("Q3"))))
        assertEquals("File", preview(MessageContent.Document(document(""), text(""))))
    }

    @Test
    fun placesContactsAndPollsShowWhatTheyAre() {
        assertEquals("Location", preview(MessageContent.Location(location, livePeriod = 0)))
        assertEquals("Live location", preview(MessageContent.Location(location, livePeriod = 900)))
        assertEquals("Location · Eiffel Tower", preview(MessageContent.Venue(Venue(location, "Eiffel Tower", ""))))
        assertEquals("Contact · Ada Lovelace", preview(MessageContent.Contact(Contact("", "Ada", "Lovelace", 0))))
        assertEquals("Poll · Lunch?", preview(poll(PollType.Regular)))
        assertEquals("Quiz · Lunch?", preview(poll(PollType.Quiz(text("")))))
    }

    @Test
    fun otherContentShowsWhatItIs() {
        assertEquals("🎲", preview(MessageContent.Dice("🎲", value = 4)))
        assertEquals("🎉", preview(MessageContent.AnimatedEmoji("🎉", sticker = null)))
        assertEquals("Game · Lumberjack", preview(MessageContent.Game("Lumberjack")))
        assertEquals("Invoice · Coffee beans", preview(MessageContent.Invoice("Coffee beans")))
        assertEquals("Story", preview(MessageContent.Story(storyPosterChatId = 1, storyId = 2)))
        assertEquals("Unsupported message", preview(MessageContent.Unsupported))
        assertEquals(
            listOf("Photo expired", "Video expired", "Video message expired", "Voice message expired"),
            ExpiredMedia.entries.map { preview(MessageContent.Expired(it)) },
        )
    }

    // Service messages

    @Test
    fun serviceMessagesNameTheirSenderOrYou() {
        assertEquals("Alice joined the group", service(ServiceAction.MemberJoined))
        assertEquals("You joined the group", service(ServiceAction.MemberJoined, MessageSender.User(ME), true))
        assertEquals("Alice joined via invite link", service(ServiceAction.MemberJoinedByLink))
        assertEquals("Alice left the group", service(ServiceAction.MemberLeft))
        assertEquals("Alice pinned a message", service(ServiceAction.MessagePinned(messageId = 7)))
        assertEquals("You took a screenshot", service(ServiceAction.ScreenshotTaken, MessageSender.User(ME), true))
        assertEquals(
            "Alice created the group “Club”",
            service(ServiceAction.BasicGroupCreated("Club", persistentListOf())),
        )
        assertEquals("Alice changed the name to “Club”", service(ServiceAction.TitleChanged("Club")))
        assertEquals("You removed the photo", service(ServiceAction.PhotoDeleted, MessageSender.User(ME), true))
        assertEquals("Alice joined Telegram", service(ServiceAction.ContactRegistered))
    }

    @Test
    fun anUnknownSenderIsSomeone() {
        assertEquals("Someone joined the group", service(ServiceAction.MemberJoined, MessageSender.User(UNKNOWN)))
    }

    @Test
    fun eventsWithoutAnAuthorSayWhatHappened() {
        assertEquals("Channel created", service(ServiceAction.ChannelCreated("News"), MessageSender.Chat(-100)))
        assertEquals("Video chat started", service(ServiceAction.VideoChatStarted))
        assertEquals("Auto-delete turned on", service(ServiceAction.AutoDeleteTimerChanged(86_400, fromUserId = 0)))
        assertEquals("Auto-delete turned off", service(ServiceAction.AutoDeleteTimerChanged(0, fromUserId = 0)))
        assertEquals("The group was upgraded to a supergroup", service(ServiceAction.UpgradedToSupergroup(5)))
        assertEquals("Chat event", service(ServiceAction.Other))
        assertEquals("Alice won the raffle", service(ServiceAction.Custom("Alice won\nthe raffle")))
    }

    @Test
    fun addedMembersAreNamedOrCounted() {
        assertEquals("Alice added Bob", service(ServiceAction.MembersAdded(persistentListOf(BOB))))
        assertEquals("Alice added you", service(ServiceAction.MembersAdded(persistentListOf(ME))))
        assertEquals(
            "You added Bob",
            service(ServiceAction.MembersAdded(persistentListOf(BOB)), MessageSender.User(ME), isOutgoing = true),
        )
        assertEquals("Alice added 3 members", service(ServiceAction.MembersAdded(persistentListOf(BOB, ME, UNKNOWN))))
        assertEquals("Alice joined the group", service(ServiceAction.MembersAdded(persistentListOf(ALICE))))
    }

    @Test
    fun removedMembersAreNamed() {
        assertEquals("Alice removed Bob", service(ServiceAction.MemberRemoved(BOB)))
        assertEquals("Alice removed you", service(ServiceAction.MemberRemoved(ME)))
        assertEquals("Alice left the group", service(ServiceAction.MemberRemoved(ALICE)))
        assertEquals(
            "You removed Someone",
            service(ServiceAction.MemberRemoved(UNKNOWN), MessageSender.User(ME), isOutgoing = true),
        )
    }

    @Test
    fun callsAreSeenFromTheCurrentUsersSide() {
        fun call(
            reason: CallDiscardReason,
            isOutgoing: Boolean,
            isVideo: Boolean = false,
        ) = service(
            ServiceAction.Call(isVideo, duration = 60, discardReason = reason),
            if (isOutgoing) MessageSender.User(ME) else MessageSender.User(ALICE),
            isOutgoing,
        )

        assertEquals("Outgoing call", call(CallDiscardReason.HungUp, isOutgoing = true))
        assertEquals("Incoming video call", call(CallDiscardReason.HungUp, isOutgoing = false, isVideo = true))
        assertEquals("Missed call", call(CallDiscardReason.Missed, isOutgoing = false))
        assertEquals("Cancelled call", call(CallDiscardReason.Missed, isOutgoing = true))
        assertEquals("Declined video call", call(CallDiscardReason.Declined, isOutgoing = false, isVideo = true))
    }

    // Drafts, typing and names

    @Test
    fun draftsShowTheirTextOrKind() {
        assertEquals("See you soon", draftPreviewText(DraftContent.Text(text("See you\nsoon"))).resolve(resources))
        assertEquals("Voice message", draftPreviewText(DraftContent.VoiceNote).resolve(resources))
        assertEquals("Video message", draftPreviewText(DraftContent.VideoNote).resolve(resources))
    }

    @Test
    fun privateChatsShowTheActionAlone() {
        assertEquals("typing…", typing(isGroup = false, ChatAction.Typing to ALICE))
        assertEquals("sending a photo…", typing(isGroup = false, ChatAction.UploadingPhoto(progress = 40) to ALICE))
        assertEquals("watching 🎉…", typing(isGroup = false, ChatAction.WatchingAnimations("🎉") to ALICE))
    }

    @Test
    fun groupsNameWhoIsDoingWhat() {
        assertEquals("Alice is typing…", typing(isGroup = true, ChatAction.Typing to ALICE))
        assertEquals(
            "Alice and Bob are typing…",
            typing(isGroup = true, ChatAction.Typing to ALICE, ChatAction.Typing to BOB),
        )
        assertEquals(
            "3 people are typing…",
            typing(isGroup = true, ChatAction.Typing to ALICE, ChatAction.Typing to BOB, ChatAction.Typing to ME),
        )
        assertEquals(
            "Bob is recording a video message…",
            typing(isGroup = true, ChatAction.RecordingVideoNote to BOB, ChatAction.Typing to ALICE),
        )
        assertEquals("Someone is choosing a sticker…", typing(isGroup = true, ChatAction.ChoosingSticker to UNKNOWN))
    }

    @Test
    fun nobodyDoingAnythingHasNoText() {
        assertNull(chatActionText(emptyList(), isGroup = true, ::name))
    }

    @Test
    fun shortNamesAreTheFirstNameOrDeletedAccount() {
        assertEquals("Ada", user("Ada", "Lovelace").shortName().resolve(resources))
        assertEquals("Lovelace", user("", "Lovelace").shortName().resolve(resources))
        assertEquals("Deleted Account", user("", "", UserType.Deleted).shortName().resolve(resources))
    }

    @Test
    fun uiTextResolvesNestedArgumentsAndPlurals() {
        val nested = UiText.resource(R.string.preview_with_text, UiText.resource(R.string.preview_photo), "Sunset")

        assertEquals("Photo · Sunset", nested.resolve(resources))
        assertEquals("1 person is typing…", UiText.plural(R.plurals.typing_many, 1, 1).resolve(resources))
        assertEquals("Plain(…)", UiText.Plain("secret").toString())
    }

    private fun typing(
        isGroup: Boolean,
        vararg actions: Pair<ChatAction, Long>,
    ): String? =
        chatActionText(
            actions.map { (action, userId) -> SenderAction(MessageSender.User(userId), action) },
            isGroup,
            ::name,
        )?.resolve(resources)

    private companion object {
        const val ME = 1L
        const val ALICE = 2L
        const val BOB = 3L
        const val UNKNOWN = 4L

        val file = FileRef(id = 1, size = 0)
        val photo = Photo(persistentListOf(), minithumbnail = null)
        val video = Video(10, 640, 480, null, null, supportsStreaming = true, file = file)
        val animation = Animation(3, 320, 240, "video/mp4", null, null, file)
        val voiceNote = VoiceNote(5, Waveform(ByteArray(0)), file)
        val videoNote = VideoNote(5, 240, null, null, file)
        val location = Location(48.858, 2.294)

        fun text(text: String) = FormattedText(text, persistentListOf())

        fun sticker(emoji: String) = Sticker(0, 512, 512, emoji, StickerFormat.Webp, null, file)

        fun audio(
            title: String = "",
            performer: String = "",
            fileName: String = "",
        ) = MessageContent.Audio(Audio(180, title, performer, fileName, null, null, file), text(""))

        fun document(fileName: String) = Document(fileName, "application/pdf", null, null, file)

        fun poll(type: PollType) =
            MessageContent.Poll(
                Poll(1, text("Lunch?"), persistentListOf(), 0, true, false, false, false, type),
                description = text(""),
            )

        fun user(
            firstName: String,
            lastName: String,
            type: UserType = UserType.Regular,
        ) = User(
            id = 9,
            firstName = firstName,
            lastName = lastName,
            usernames = persistentListOf(),
            phoneNumber = "",
            status = UserStatus.Empty,
            profilePhoto = null,
            isVerified = false,
            isScam = false,
            isFake = false,
            isPremium = false,
            isContact = false,
            isMutualContact = false,
            isSupport = false,
            type = type,
            haveAccess = true,
        )

        fun message(
            content: MessageContent,
            sender: MessageSender,
            isOutgoing: Boolean,
        ) = Message(
            id = 1,
            chatId = 100,
            sender = sender,
            date = 0,
            editDate = 0,
            isOutgoing = isOutgoing,
            sendingState = null,
            isPinned = false,
            isChannelPost = false,
            containsUnreadMention = false,
            mediaAlbumId = 0,
            authorSignature = "",
            viaBotUserId = 0,
            replyTo = null,
            forwardInfo = null,
            interactionInfo = null,
            selfDestruct = null,
            content = content,
        )
    }
}

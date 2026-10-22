package dev.kyralabs.pingogram.feature.profile.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibBlockListRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageActionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSearchRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSecretChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSharedMediaRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserProfileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.text.MentionTarget
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * [ProfileViewModel] through the real TDLib repositories on a [FakeTdlibClient], as the app wires them: the user and
 * their full info, the private chat, the actions, the photos, the shared media, errors, and the tab kept across
 * process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val client = FakeTdlibClient()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    /** The view models the test made, whose scopes end with it, before the main dispatcher is reset. */
    private val created = mutableListOf<ProfileViewModel>()

    @AfterEach
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private val ada =
        TdApiFixtures.user(
            id = ADA,
            firstName = "Ada",
            lastName = "Lovelace",
            usernames = listOf("ada"),
            phoneNumber = "447700900123",
            status = TdApi.UserStatusOnline((NOW.epochSecond + 60).toInt()),
            profilePhoto = TdApiFixtures.profilePhoto(),
            isContact = true,
        )

    private fun fullInfo(
        bio: String = "Mathematician",
        blocked: Boolean = false,
        birthdate: TdApi.Birthdate? = TdApi.Birthdate(10, 12, 1815),
        bot: TdApi.BotInfo? = null,
    ) = TdApi.UserFullInfo().also {
        it.bio = TdApiFixtures.formattedText(bio)
        it.blockList = if (blocked) TdApi.BlockListMain() else null
        it.birthdate = birthdate
        it.botInfo = bot
    }

    /** Answers what opening a profile asks TDLib for: the full info, the private chat, the photos, the media. */
    private fun answerProfile(
        user: TdApi.User = ada,
        info: TdResult<TdApi.UserFullInfo> = TdResult.Success(fullInfo()),
        muted: Boolean = false,
    ) {
        client.on(TdApi.GetUser::class, TdResult.Success(user))
        client.on(TdApi.GetUserFullInfo::class, info)
        val settings =
            TdApiFixtures.chatNotificationSettings(
                useDefaultMuteFor = false,
                muteFor = if (muted) Int.MAX_VALUE else 0,
            )
        val chat =
            TdApiFixtures.chat(
                id = user.id,
                type = TdApi.ChatTypePrivate(user.id),
                notificationSettings = settings,
            )
        client.on(TdApi.CreatePrivateChat::class) { request ->
            client.emit(TdApiFixtures.updateNewChat(chat))
            TdResult.Success(chat.takeIf { request.userId == user.id } ?: chat)
        }
        client.on(TdApi.GetUserProfilePhotos::class, TdResult.Success(TdApi.ChatPhotos(0, emptyArray())))
        client.on(
            TdApi.SearchChatMessages::class,
            TdResult.Success(TdApi.FoundChatMessages(0, emptyArray(), 0)),
        )
    }

    private val clock =
        object : WallClock {
            override val minutes = MutableStateFlow(NOW)

            override fun now(): Instant = NOW

            override fun zone(): ZoneId = ZoneOffset.UTC
        }

    private val patterns =
        object : TimePatterns {
            override fun is24HourClock() = false

            override fun bestPattern(
                locale: Locale,
                skeleton: String,
            ) = mapOf("hm" to "h:mm a", "Hm" to "HH:mm", "MMMd" to "MMM d", "yMd" to "M/d/y", "yMMMd" to "MMM d, y")
                .plus("yMMMMd" to "MMMM d, y")
                .plus("MMMMd" to "MMMM d")[skeleton] ?: error("No pattern for $skeleton")
        }

    private suspend fun TestScope.viewModel(
        userId: Long = ADA,
        handle: SavedStateHandle = SavedStateHandle(),
        myId: Long = ME,
    ): ProfileViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        client.emit(TdApiFixtures.myIdOption(myId))
        runCurrent()
        val viewModel =
            ProfileViewModel(
                userId = userId,
                savedStateHandle = handle,
                userRepository = users,
                profileRepository = TdlibUserProfileRepository(client),
                secretChatRepository = TdlibSecretChatRepository(client, backgroundScope),
                chatRepository = chats,
                blockListRepository = TdlibBlockListRepository(client),
                messageActionRepository = TdlibMessageActionRepository(client),
                sharedMediaRepository = TdlibSharedMediaRepository(client),
                searchRepository = TdlibSearchRepository(client, chats),
                clock = clock,
                timePatterns = patterns,
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        created += viewModel
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.media.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: ProfileViewModel): List<ProfileEffect> {
        val effects = mutableListOf<ProfileEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: ProfileViewModel,
        vararg events: ProfileEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private val ProfileViewModel.content get() = uiState.value as ProfileUiState.Content

    // The profile

    @Test
    fun `loads before the user is found, then shows the header, the info and the actions`() =
        runTest {
            answerProfile()
            val viewModel = viewModel()

            val content = viewModel.content
            assertEquals(UiText.Plain("Ada Lovelace"), content.header.name)
            assertEquals(UiText.resource(UiR.string.user_status_online), content.header.status)
            assertTrue(content.header.isOnline)
            assertEquals("Mathematician", content.info.bio?.text)
            assertEquals(listOf("ada"), content.info.usernames)
            assertEquals("+447700900123", content.info.phoneNumber)
            assertEquals("December 10, 1815", content.info.birthday)
            with(content.actions) {
                assertTrue(canMessage)
                assertEquals(false, isMuted)
                assertTrue(canStartSecretChat)
                assertEquals(ContactAction.Edit, contact)
                assertEquals(false, isBlocked)
                assertTrue(canReport)
            }
        }

    @Test
    fun `a user TDLib does not know is an error, and retrying looks again`() =
        runTest {
            answerProfile()
            client.on(TdApi.GetUser::class, TdResult.Failure(TdError(404, "Not Found")))
            val viewModel = viewModel()
            assertEquals(ProfileUiState.Error(DomainError.NotFound), viewModel.uiState.value)

            client.on(TdApi.GetUser::class, TdResult.Success(ada))
            send(viewModel, ProfileEvent.Retry)

            assertInstanceOf(ProfileUiState.Content::class.java, viewModel.uiState.value)
        }

    @Test
    fun `a flood wait on the full info is a snackbar, and the rows it fills are left out`() =
        runTest {
            answerProfile(info = TdResult.Failure(TdError(429, "Too Many Requests: retry after 7")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            runCurrent()

            assertNull(viewModel.content.info.bio)
            assertNull(viewModel.content.actions.isBlocked)
            assertEquals(listOf(ProfileEffect.ShowError(DomainError.FloodWait(7))), effects)
        }

    @Test
    fun `a bot shows its description, and offers no secret chat and no contact`() =
        runTest {
            val bot = TdApiFixtures.user(id = BOT, firstName = "Finder", type = TdApi.UserTypeBot())
            val description = TdApi.BotInfo().also { it.description = "Finds stickers" }
            answerProfile(user = bot, info = TdResult.Success(fullInfo(bio = "", bot = description)))
            val viewModel = viewModel(userId = BOT)

            with(viewModel.content) {
                assertEquals(UiText.resource(R.string.profile_status_bot), header.status)
                assertEquals("Finds stickers", info.bio?.text)
                assertTrue(info.isBot)
                assertFalse(actions.canStartSecretChat)
                assertNull(actions.contact)
            }
        }

    @Test
    fun `a deleted account has no status, nothing to block or report, and no secret chat`() =
        runTest {
            val deleted =
                TdApiFixtures.user(
                    id = DELETED,
                    firstName = "",
                    phoneNumber = "",
                    type = TdApi.UserTypeDeleted(),
                )
            answerProfile(user = deleted)
            val viewModel = viewModel(userId = DELETED)

            with(viewModel.content) {
                assertEquals(UiText.resource(UiR.string.user_deleted_account), header.name)
                assertNull(header.status)
                assertNull(actions.isBlocked)
                assertFalse(actions.canReport)
                assertFalse(actions.canStartSecretChat)
            }
        }

    @Test
    fun `one's own profile opens Saved Messages and offers nothing to block, mute or report`() =
        runTest {
            answerProfile()
            val viewModel = viewModel(myId = ADA)

            with(viewModel.content.actions) {
                assertTrue(isSelf)
                assertNull(isMuted)
                assertNull(isBlocked)
                assertFalse(canReport)
                assertFalse(canStartSecretChat)
                assertNull(contact)
            }
        }

    // Actions

    @Test
    fun `Message opens the private chat`() =
        runTest {
            answerProfile()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ProfileEvent.SendMessage)

            assertEquals(listOf(ProfileEffect.OpenChat(ADA)), effects)
        }

    @Test
    fun `a secret chat starts after the confirmation and opens`() =
        runTest {
            answerProfile()
            client.on(
                TdApi.CreateNewSecretChat::class,
                TdResult.Success(TdApiFixtures.chat(id = SECRET_CHAT, type = TdApi.ChatTypeSecret(3, ADA))),
            )
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ProfileEvent.StartSecretChat)
            assertEquals(ProfileDialog.SecretChat, viewModel.content.dialog)
            assertTrue(client.sentOf<TdApi.CreateNewSecretChat>().isEmpty())

            send(viewModel, ProfileEvent.ConfirmSecretChat)

            assertNull(viewModel.content.dialog)
            assertEquals(ADA, client.sentOf<TdApi.CreateNewSecretChat>().single().userId)
            assertEquals(listOf(ProfileEffect.OpenChat(SECRET_CHAT)), effects)
        }

    @Test
    fun `dismissing the secret chat dialog starts nothing`() =
        runTest {
            answerProfile()
            val viewModel = viewModel()

            send(viewModel, ProfileEvent.StartSecretChat, ProfileEvent.DismissDialog)

            assertNull(viewModel.content.dialog)
            assertTrue(client.sentOf<TdApi.CreateNewSecretChat>().isEmpty())
        }

    @Test
    fun `a refused secret chat is a snackbar`() =
        runTest {
            answerProfile()
            client.on(
                TdApi.CreateNewSecretChat::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 3")),
            )
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ProfileEvent.StartSecretChat, ProfileEvent.ConfirmSecretChat)

            assertEquals(listOf(ProfileEffect.ShowError(DomainError.FloodWait(3))), effects)
            assertFalse(viewModel.content.actions.isStartingSecretChat)
        }

    @Test
    fun `the mute toggle mutes the private chat`() =
        runTest {
            answerProfile()
            client.on(TdApi.SetChatNotificationSettings::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()

            send(viewModel, ProfileEvent.ToggleMute)

            val request = client.sentOf<TdApi.SetChatNotificationSettings>().single()
            assertEquals(ADA, request.chatId)
            assertTrue(request.notificationSettings.muteFor > 0)
        }

    @Test
    fun `blocking asks first, then blocks, shows it at once and says so`() =
        runTest {
            answerProfile()
            client.on(TdApi.SetMessageSenderBlockList::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ProfileEvent.Block)
            assertInstanceOf(ProfileDialog.Block::class.java, viewModel.content.dialog)

            send(viewModel, ProfileEvent.ConfirmBlock)

            val request = client.sentOf<TdApi.SetMessageSenderBlockList>().single()
            assertInstanceOf(TdApi.BlockListMain::class.java, request.blockList)
            assertEquals(true, viewModel.content.actions.isBlocked)
            assertEquals(listOf(ProfileEffect.BlockChanged(UiText.Plain("Ada Lovelace"), blocked = true)), effects)
        }

    @Test
    fun `unblocking a blocked user takes them off the block list`() =
        runTest {
            answerProfile(info = TdResult.Success(fullInfo(blocked = true)))
            client.on(TdApi.SetMessageSenderBlockList::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            assertEquals(true, viewModel.content.actions.isBlocked)

            send(viewModel, ProfileEvent.Unblock)

            assertNull(client.sentOf<TdApi.SetMessageSenderBlockList>().single().blockList)
            assertEquals(false, viewModel.content.actions.isBlocked)
        }

    @Test
    fun `a failed block is a snackbar and changes nothing`() =
        runTest {
            answerProfile()
            client.on(TdApi.SetMessageSenderBlockList::class, TdResult.Failure(TdError(401, "Unauthorized")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ProfileEvent.Block, ProfileEvent.ConfirmBlock)

            assertEquals(false, viewModel.content.actions.isBlocked)
            assertEquals(listOf(ProfileEffect.ShowError(DomainError.Unauthorized)), effects)
        }

    @Test
    fun `a change of the full info elsewhere follows`() =
        runTest {
            answerProfile()
            val viewModel = viewModel()

            client.emit(TdApi.UpdateUserFullInfo(ADA, fullInfo(bio = "Poet", blocked = true)))
            runCurrent()

            assertEquals(
                "Poet",
                viewModel.content.info.bio
                    ?.text,
            )
            assertEquals(true, viewModel.content.actions.isBlocked)
        }

    @Test
    fun `reporting asks TDLib for the reasons of the private chat, without messages`() =
        runTest {
            answerProfile()
            val options = arrayOf(TdApi.ReportOption(byteArrayOf(1), "Spam"))
            client.on(TdApi.ReportChat::class) { request ->
                if (request.optionId.isEmpty()) {
                    TdResult.Success(TdApi.ReportChatResultOptionRequired("Why?", options))
                } else {
                    TdResult.Success(TdApi.ReportChatResultOk())
                }
            }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ProfileEvent.Report)
            assertEquals(
                ProfileReportStep.Options("Why?", persistentListOf("Spam")),
                viewModel.reportSheet.value?.step,
            )
            send(viewModel, ProfileEvent.ChooseReportOption(0))

            val requests = client.sentOf<TdApi.ReportChat>()
            assertEquals(listOf(ADA, ADA), requests.map { it.chatId })
            assertTrue(requests.all { it.messageIds.isEmpty() })
            assertNull(viewModel.reportSheet.value)
            assertEquals(listOf(ProfileEffect.Reported), effects)
        }

    @Test
    fun `a report that needs messages says to report them from the chat`() =
        runTest {
            answerProfile()
            client.on(TdApi.ReportChat::class, TdResult.Success(TdApi.ReportChatResultMessagesRequired()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ProfileEvent.Report)

            assertNull(viewModel.reportSheet.value)
            assertEquals(listOf(ProfileEffect.ReportNeedsMessages), effects)
        }

    @Test
    fun `usernames and the phone number are copied, shared and dialed`() =
        runTest {
            answerProfile()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(
                viewModel,
                ProfileEvent.CopyUsername("ada"),
                ProfileEvent.ShareUsername("ada"),
                ProfileEvent.CopyPhone,
                ProfileEvent.CallPhone,
                ProfileEvent.CopyBio,
            )

            assertEquals(
                listOf(
                    ProfileEffect.CopyText("@ada", CopiedText.Username),
                    ProfileEffect.Share("https://t.me/ada"),
                    ProfileEffect.CopyText("+447700900123", CopiedText.Phone),
                    ProfileEffect.Dial("+447700900123"),
                    ProfileEffect.CopyText("Mathematician", CopiedText.Bio),
                ),
                effects,
            )
        }

    @Test
    fun `a mention opens the profile it names, or the group of a username`() =
        runTest {
            answerProfile()
            client.on(
                TdApi.SearchPublicChat::class,
                TdResult.Success(TdApiFixtures.chat(id = GROUP, type = TdApi.ChatTypeSupergroup(5, false))),
            )
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(
                viewModel,
                ProfileEvent.OpenMention(MentionTarget.User(BOT)),
                ProfileEvent.OpenMention(MentionTarget.Username("group")),
            )

            assertEquals(listOf(ProfileEffect.OpenProfile(BOT), ProfileEffect.OpenChat(GROUP)), effects)
        }

    // Photos

    @Test
    fun `the photos load a page at a time`() =
        runTest {
            answerProfile()
            val photo = TdApi.ChatPhoto(5, 0, null, arrayOf(TdApiFixtures.photoSize("a", 160, 160)), null, null, null)
            val second = TdApi.ChatPhoto(6, 0, null, arrayOf(TdApiFixtures.photoSize("a", 160, 160)), null, null, null)
            client.on(TdApi.GetUserProfilePhotos::class) { request ->
                TdResult.Success(TdApi.ChatPhotos(2, if (request.offset == 0) arrayOf(photo) else arrayOf(second)))
            }
            val viewModel = viewModel()
            assertEquals(
                listOf(5L),
                viewModel.content.photos.items
                    .map { it.id },
            )
            assertEquals(2, viewModel.content.photos.totalCount)

            send(viewModel, ProfileEvent.LoadMorePhotos)

            assertEquals(
                listOf(5L, 6L),
                viewModel.content.photos.items
                    .map { it.id },
            )
            assertEquals(listOf(0, 1), client.sentOf<TdApi.GetUserProfilePhotos>().map { it.offset })
            // All loaded: no more requests.
            send(viewModel, ProfileEvent.LoadMorePhotos)
            assertEquals(2, client.sentOf<TdApi.GetUserProfilePhotos>().size)
        }

    // Shared media

    @Test
    fun `the shared media show the private chat's photos and videos first`() =
        runTest {
            answerProfile()
            client.on(
                TdApi.SearchChatMessages::class,
                TdResult.Success(
                    TdApi.FoundChatMessages(
                        1,
                        arrayOf(TdApiFixtures.message(id = 9, chatId = ADA, content = TdApiFixtures.messagePhoto())),
                        0,
                    ),
                ),
            )
            val viewModel = viewModel()

            val media = viewModel.media.value
            assertEquals(ADA, media.chatId)
            assertEquals(SharedMediaKind.PhotosAndVideos, media.selected)
            val items = (media.content as SharedMediaContent.Items).items
            assertEquals(listOf(9L), items.map { it.messageId })
            assertEquals(MediaKind.Photo, (items.single() as SharedMediaItem.Media).kind)
            assertEquals(MoreMedia.None, (media.content as SharedMediaContent.Items).more)
        }

    @Test
    fun `a tab loads when it first shows, pages as it nears its end, and is kept in the saved state`() =
        runTest {
            answerProfile()
            val document = TdApiFixtures.messageDocument()
            client.on(TdApi.SearchChatMessages::class) { request ->
                val messages =
                    if (request.fromMessageId == 0L) {
                        arrayOf(TdApiFixtures.message(id = 20, chatId = ADA, content = document))
                    } else {
                        arrayOf(TdApiFixtures.message(id = 10, chatId = ADA, content = document))
                    }
                TdResult.Success(TdApi.FoundChatMessages(2, messages, if (request.fromMessageId == 0L) 20 else 0))
            }
            val handle = SavedStateHandle()
            val viewModel = viewModel(handle = handle)

            viewModel.onMediaEvent(SharedMediaEvent.Select(SharedMediaKind.Files))
            runCurrent()
            assertEquals(MoreMedia.Available, (viewModel.media.value.content as SharedMediaContent.Items).more)
            viewModel.onMediaEvent(SharedMediaEvent.LoadMore)
            runCurrent()

            val items = (viewModel.media.value.content as SharedMediaContent.Items).items
            assertEquals(listOf(20L, 10L), items.map { it.messageId })
            assertEquals(
                listOf(0L, 20L),
                client
                    .sentOf<TdApi.SearchChatMessages>()
                    .filter {
                        it.filter is TdApi.SearchMessagesFilterDocument
                    }.map { it.fromMessageId },
            )
            assertEquals(SharedMediaKind.Files, viewModel(handle = handle).media.value.selected)
        }

    @Test
    fun `a tab whose first page fails shows the error and tries again`() =
        runTest {
            answerProfile()
            client.on(
                TdApi.SearchChatMessages::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 2")),
            )
            val viewModel = viewModel()
            assertEquals(SharedMediaContent.Error(DomainError.FloodWait(2)), viewModel.media.value.content)

            client.on(TdApi.SearchChatMessages::class, TdResult.Success(TdApi.FoundChatMessages(0, emptyArray(), 0)))
            viewModel.onMediaEvent(SharedMediaEvent.Retry)
            runCurrent()

            assertEquals(SharedMediaContent.Empty, viewModel.media.value.content)
        }

    private companion object {
        const val ADA = 42L
        const val ME = 1L
        const val BOT = 7L
        const val DELETED = 9L
        const val GROUP = -100L
        const val SECRET_CHAT = -9L

        val NOW: Instant = Instant.parse("2026-10-18T09:41:30Z")
    }
}

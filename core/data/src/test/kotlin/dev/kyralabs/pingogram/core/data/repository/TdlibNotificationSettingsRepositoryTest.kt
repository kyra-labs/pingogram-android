package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.ChatMute
import dev.kyralabs.pingogram.core.domain.model.ChatMuteChange
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MuteChange
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettings
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettingsChange
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chatNotificationSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TdlibNotificationSettingsRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibNotificationSettingsRepository(client, FixedClock)

    /** Every field differs from the one before, so a field that is not copied shows. */
    private val custom = TdApi.ScopeNotificationSettings(0, 7, true, false, true, 8, false, true, false)

    private fun answerScope(settings: TdApi.ScopeNotificationSettings = custom) =
        client.on(TdApi.GetScopeNotificationSettings::class, TdResult.Success(settings))

    private fun answerChats(vararg chats: TdApi.Chat) =
        client.on(TdApi.GetChat::class) { request ->
            chats.firstOrNull { it.id == request.chatId }?.let { TdResult.Success(it) }
                ?: TdResult.Failure(TdError(400, "Chat not found"))
        }

    private fun answerExceptions(vararg chatIds: Long) =
        client.on(
            TdApi.GetChatNotificationSettingsExceptions::class,
            TdResult.Success(TdApi.Chats(chatIds.size, chatIds)),
        )

    private fun answerOk() {
        client.on(TdApi.SetScopeNotificationSettings::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.SetChatNotificationSettings::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.ResetAllNotificationSettings::class, TdResult.Success(TdApi.Ok()))
    }

    // The defaults of a scope

    @ParameterizedTest
    @EnumSource(NotificationScope::class)
    fun `asks TDLib for the defaults of each scope`(scope: NotificationScope) =
        runTest {
            answerScope()

            repository.scopeSettings(scope)

            val expected =
                when (scope) {
                    NotificationScope.PrivateChats -> TdApi.NotificationSettingsScopePrivateChats::class.java
                    NotificationScope.GroupChats -> TdApi.NotificationSettingsScopeGroupChats::class.java
                    NotificationScope.ChannelChats -> TdApi.NotificationSettingsScopeChannelChats::class.java
                }
            assertInstanceOf(expected, client.sentOf<TdApi.GetScopeNotificationSettings>().single().scope)
        }

    @Test
    fun `maps the defaults, pinned messages and mentions as notifying when not disabled`() =
        runTest {
            answerScope(TdApi.ScopeNotificationSettings(3_600, 0, false, true, false, 0, true, true, false))

            val settings = repository.scopeSettings(NotificationScope.GroupChats)

            assertEquals(
                Outcome.Success(
                    ScopeNotificationSettings(
                        isMuted = true,
                        showPreview = false,
                        pinnedMessages = false,
                        mentions = true,
                    ),
                ),
                settings,
            )
        }

    @Test
    fun `a failed read reports its error`() =
        runTest {
            client.on(
                TdApi.GetScopeNotificationSettings::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 30")),
            )

            assertEquals(
                Outcome.Failure(DomainError.FloodWait(30)),
                repository.scopeSettings(NotificationScope.PrivateChats),
            )
        }

    @Test
    fun `reports TDLib's changes of the defaults, leaving out scopes it does not know`() =
        runTest(UnconfinedTestDispatcher()) {
            val changes = mutableListOf<ScopeNotificationSettingsChange>()
            val job = launch { repository.scopeChanges.take(1).toList(changes) }

            client.emit(TdApi.UpdateScopeNotificationSettings(TdApi.NotificationSettingsScopeChannelChats(), custom))
            job.join()

            assertEquals(
                listOf(
                    ScopeNotificationSettingsChange(
                        NotificationScope.ChannelChats,
                        ScopeNotificationSettings(
                            isMuted = false,
                            showPreview = true,
                            pinnedMessages = false,
                            mentions = true,
                        ),
                    ),
                ),
                changes,
            )
        }

    @Test
    fun `changing the defaults keeps the sounds and the stories' settings`() =
        runTest {
            answerScope()
            answerOk()

            repository.setScopeSettings(
                NotificationScope.GroupChats,
                ScopeNotificationSettings(isMuted = true, showPreview = false, pinnedMessages = true, mentions = false),
            )

            val sent = client.sentOf<TdApi.SetScopeNotificationSettings>().single()
            assertInstanceOf(TdApi.NotificationSettingsScopeGroupChats::class.java, sent.scope)
            val settings = sent.notificationSettings
            // Muted forever, as TDLib takes more than 366 days.
            assertTrue(settings.muteFor > 366 * 86_400)
            assertEquals(
                listOf<Any>(7L, false, false, true, 8L, false, false, true),
                listOf<Any>(
                    settings.soundId,
                    settings.showPreview,
                    settings.useDefaultMuteStories,
                    settings.muteStories,
                    settings.storySoundId,
                    settings.showStoryPoster,
                    settings.disablePinnedMessageNotifications,
                    settings.disableMentionNotifications,
                ),
            )
        }

    @Test
    fun `unmuting the defaults sets no mute, and a running limited mute stays as it is`() =
        runTest {
            answerOk()
            answerScope(TdApiFixtures.scopeNotificationSettings(muteFor = 3_600))
            val muted =
                ScopeNotificationSettings(isMuted = true, showPreview = true, pinnedMessages = true, mentions = true)

            repository.setScopeSettings(NotificationScope.PrivateChats, muted)
            repository.setScopeSettings(NotificationScope.PrivateChats, muted.copy(isMuted = false))

            assertEquals(
                listOf(3_600, 0),
                client.sentOf<TdApi.SetScopeNotificationSettings>().map { it.notificationSettings.muteFor },
            )
        }

    @Test
    fun `changing the defaults sends nothing if they cannot be read`() =
        runTest {
            val settings =
                ScopeNotificationSettings(isMuted = true, showPreview = true, pinnedMessages = true, mentions = true)

            val result = repository.setScopeSettings(NotificationScope.PrivateChats, settings)

            assertInstanceOf(Outcome.Failure::class.java, result)
            assertTrue(client.sentOf<TdApi.SetScopeNotificationSettings>().isEmpty())
        }

    // Exceptions

    @Test
    fun `lists the exceptions in TDLib's order with their mutes, leaving out chats TDLib no longer knows`() =
        runTest {
            answerExceptions(1, 2, 3, 4)
            answerChats(
                chat(
                    id = 1,
                    title = "Ada",
                    notificationSettings = chatNotificationSettings(useDefaultMuteFor = false, muteFor = 3_600),
                ),
                chat(
                    id = 3,
                    type = TdApi.ChatTypeSupergroup(5, false),
                    title = "Engines",
                    notificationSettings = chatNotificationSettings(useDefaultMuteFor = false, muteFor = 0),
                ),
                chat(
                    id = 4,
                    type = TdApi.ChatTypeBasicGroup(6),
                    title = "Looms",
                    notificationSettings = chatNotificationSettings(useDefaultMuteFor = false, muteFor = Int.MAX_VALUE),
                ),
            )

            val exceptions = (repository.exceptions(NotificationScope.GroupChats) as Outcome.Success).value

            assertEquals(
                listOf(
                    Triple(1L, "Ada", ChatMute.MutedUntil(NOW + 3_600)),
                    Triple(3L, "Engines", ChatMute.Unmuted),
                    Triple(4L, "Looms", ChatMute.MutedForever),
                ),
                exceptions.map { Triple(it.chatId, it.title, it.mute) },
            )
            assertEquals(ChatType.Supergroup(5, isChannel = false), exceptions[1].type)
            val request = client.sentOf<TdApi.GetChatNotificationSettingsExceptions>().single()
            assertInstanceOf(TdApi.NotificationSettingsScopeGroupChats::class.java, request.scope)
            assertEquals(false, request.compareSound)
        }

    @Test
    fun `a chat that cannot be read for another reason fails the list`() =
        runTest {
            answerExceptions(1)
            client.on(TdApi.GetChat::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 5")))

            assertEquals(
                Outcome.Failure(DomainError.FloodWait(5)),
                repository.exceptions(NotificationScope.PrivateChats),
            )
        }

    @Test
    fun `reports chats' changed mutes`() =
        runTest(UnconfinedTestDispatcher()) {
            val change =
                launch {
                    assertEquals(ChatMuteChange(9, ChatMute.Default), repository.chatMuteChanges.first())
                }

            client.emit(TdApi.UpdateChatNotificationSettings(9, chatNotificationSettings(useDefaultMuteFor = true)))
            change.join()
        }

    @Test
    fun `changing a chat's mute keeps its other settings`() =
        runTest {
            val settings =
                chatNotificationSettings(
                    useDefaultMuteFor = true,
                    useDefaultSound = false,
                    soundId = 7,
                    showPreview = false,
                )
            answerChats(chat(id = 1, notificationSettings = settings))
            answerOk()

            for (change in listOf(
                MuteChange.MuteFor(3_600),
                MuteChange.MuteForever,
                MuteChange.Unmute,
                MuteChange.UseDefault,
            )) {
                repository.setChatMute(1, change)
            }

            val sent = client.sentOf<TdApi.SetChatNotificationSettings>().map { it.notificationSettings }
            assertEquals(
                listOf(false to 3_600, false to Int.MAX_VALUE, false to 0, true to 0),
                sent.map { it.useDefaultMuteFor to it.muteFor },
            )
            assertTrue(sent.all { !it.useDefaultSound && it.soundId == 7L && !it.showPreview })
            assertTrue(client.sentOf<TdApi.SetChatNotificationSettings>().all { it.chatId == 1L })
        }

    @Test
    fun `resetting asks TDLib to reset everything`() =
        runTest {
            answerOk()

            assertEquals(Outcome.Success(Unit), repository.resetAll())
            assertEquals(1, client.sentOf<TdApi.ResetAllNotificationSettings>().size)
        }

    private object FixedClock : WallClock {
        override fun now(): Instant = Instant.ofEpochSecond(NOW)

        override fun zone(): ZoneId = ZoneOffset.UTC

        override val minutes = MutableStateFlow(now())
    }

    private companion object {
        const val NOW = 1_800_000_000L
    }
}

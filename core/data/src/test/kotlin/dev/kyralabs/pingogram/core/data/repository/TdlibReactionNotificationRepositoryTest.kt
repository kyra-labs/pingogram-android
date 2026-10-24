package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSettings
import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSource
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdlibReactionNotificationRepositoryTest {
    private val client = FakeTdlibClient()

    private val announced =
        TdApi.ReactionNotificationSettings(
            TdApi.ReactionNotificationSourceAll(),
            TdApi.ReactionNotificationSourceContacts(),
            TdApi.ReactionNotificationSourceNone(),
            SOUND,
            true,
        )

    @Test
    fun `holds what TDLib announced, and nothing before`() =
        runTest {
            val repository = TdlibReactionNotificationRepository(client, backgroundScope)
            runCurrent()
            assertNull(repository.settings.value)

            client.emit(TdApi.UpdateReactionNotificationSettings(announced))
            runCurrent()

            assertEquals(
                ReactionNotificationSettings(
                    messages = ReactionNotificationSource.All,
                    stories = ReactionNotificationSource.Contacts,
                    pollVotes = ReactionNotificationSource.None,
                    showPreview = true,
                ),
                repository.settings.value,
            )
        }

    @Test
    fun `sends every setting, keeping the sound Telegram has`() =
        runTest {
            client.on(TdApi.SetReactionNotificationSettings::class, TdResult.Success(TdApi.Ok()))
            val repository = TdlibReactionNotificationRepository(client, backgroundScope)
            runCurrent()
            client.emit(TdApi.UpdateReactionNotificationSettings(announced))
            runCurrent()

            val changed =
                ReactionNotificationSettings(
                    messages = ReactionNotificationSource.Contacts,
                    stories = ReactionNotificationSource.None,
                    pollVotes = ReactionNotificationSource.All,
                    showPreview = false,
                )
            assertEquals(Outcome.Success(Unit), repository.setSettings(changed))

            val sent = client.sentOf<TdApi.SetReactionNotificationSettings>().single().notificationSettings
            assertTrue(sent.messageReactionSource is TdApi.ReactionNotificationSourceContacts)
            assertTrue(sent.storyReactionSource is TdApi.ReactionNotificationSourceNone)
            assertTrue(sent.pollVoteSource is TdApi.ReactionNotificationSourceAll)
            assertEquals(SOUND to false, sent.soundId to sent.showPreview)
        }

    @Test
    fun `logging out forgets the settings`() =
        runTest {
            val repository = TdlibReactionNotificationRepository(client, backgroundScope)
            runCurrent()
            client.emit(TdApi.UpdateReactionNotificationSettings(announced))
            client.emit(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
            runCurrent()

            assertNull(repository.settings.value)
        }

    private companion object {
        const val SOUND = 7L
    }
}

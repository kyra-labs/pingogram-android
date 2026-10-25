package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSettings
import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSource
import dev.kyralabs.pingogram.core.domain.repository.ReactionNotificationRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [ReactionNotificationRepository] on TDLib: the settings from `updateReactionNotificationSettings` (a
 * [TdlibUpdateCache], as TDLib announces them once per start and cannot be asked), changed with
 * `setReactionNotificationSettings`, which replaces every field, so the sound Telegram keeps is sent back as it was.
 * TDLib announces the change, which the cache then holds; a change it refused leaves the cache as it was.
 *
 * TDLib (a94b83d) does not yet turn Telegram's reaction pushes into notifications, so these settings reach Telegram
 * and the account's other apps, while Pingogram shows no reaction notifications itself.
 */
@AccountScoped
class TdlibReactionNotificationRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : ReactionNotificationRepository,
        TdlibUpdateCache {
        private val store =
            UpdateStore<Announced>(client, scope, Announced(null)) { state, update ->
                if (update is TdApi.UpdateReactionNotificationSettings) {
                    Announced(
                        update.notificationSettings,
                    )
                } else {
                    state
                }
            }

        override val settings: StateFlow<ReactionNotificationSettings?> =
            store.state
                .map { it.settings?.toSettings() }
                .stateIn(scope, SharingStarted.Eagerly, null)

        override suspend fun setSettings(settings: ReactionNotificationSettings): Outcome<Unit> {
            val sound =
                store.state.value.settings
                    ?.soundId ?: 0
            val request =
                TdApi.ReactionNotificationSettings(
                    settings.messages.toTdlib(),
                    settings.stories.toTdlib(),
                    settings.pollVotes.toTdlib(),
                    sound,
                    settings.showPreview,
                )
            return client.request(TdApi.SetReactionNotificationSettings(request)).map {}
        }

        /** What TDLib announced last; a holder, as [UpdateStore] keeps non-null states. */
        private data class Announced(
            val settings: TdApi.ReactionNotificationSettings?,
        )
    }

private fun TdApi.ReactionNotificationSettings.toSettings() =
    ReactionNotificationSettings(
        messages = messageReactionSource.toSource(),
        stories = storyReactionSource.toSource(),
        pollVotes = pollVoteSource.toSource(),
        showPreview = showPreview,
    )

private fun TdApi.ReactionNotificationSource?.toSource(): ReactionNotificationSource =
    when (this) {
        is TdApi.ReactionNotificationSourceAll -> ReactionNotificationSource.All
        is TdApi.ReactionNotificationSourceContacts -> ReactionNotificationSource.Contacts
        else -> ReactionNotificationSource.None
    }

private fun ReactionNotificationSource.toTdlib(): TdApi.ReactionNotificationSource =
    when (this) {
        ReactionNotificationSource.All -> TdApi.ReactionNotificationSourceAll()
        ReactionNotificationSource.Contacts -> TdApi.ReactionNotificationSourceContacts()
        ReactionNotificationSource.None -> TdApi.ReactionNotificationSourceNone()
    }

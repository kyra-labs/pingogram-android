package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * What a message's [SendOptions] leave to the user's settings, filled in as it is sent: the notification sound, from
 * "Send silently by default" (`REQUIREMENTS.md` FR-X11) where neither "Send silently" nor "Send with sound" was picked.
 */
class SendDefaults
    @Inject
    constructor(
        private val composerSettings: ComposerSettingsRepository,
    ) {
        /** [options] with their sound decided. */
        suspend fun resolve(options: SendOptions): SendOptions =
            if (options.silent != null) options else options.copy(silent = composerSettings.sendsSilently.first())
    }

package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.link.LinkCleaner
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.repository.CleanLinksRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * What a text being sent loses before it goes (`REQUIREMENTS.md` FR-X8): its links' tracking parameters
 * ([LinkCleaner]) while Privacy and security's "Remove tracking from links" ([settings]) is on, nothing while it is
 * off. Messages, notification replies and the share sheet's comment go through it.
 */
class OutgoingLinkCleaner
    @Inject
    constructor(
        private val settings: CleanLinksRepository,
    ) {
        /** [text] as it is sent: cleaned while the setting is on. */
        suspend fun clean(text: FormattedText): FormattedText =
            if (settings.enabled.first()) LinkCleaner.clean(text) else text
    }

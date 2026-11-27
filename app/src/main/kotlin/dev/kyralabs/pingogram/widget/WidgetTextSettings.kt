package dev.kyralabs.pingogram.widget

import dev.kyralabs.pingogram.core.domain.repository.MutedWordsRepository
import dev.kyralabs.pingogram.core.domain.repository.NotesAndNamesRepository
import dev.kyralabs.pingogram.core.domain.repository.PrivacyScreenRepository
import javax.inject.Inject

/**
 * The device's settings that change the text the widgets show: names given on this phone (`REQUIREMENTS.md` FR-X20),
 * muted words (FR-X21) and the privacy screen (FR-X25), which keeps message text off the home screen.
 */
internal class WidgetTextSettings
    @Inject
    constructor(
        val notesAndNames: NotesAndNamesRepository,
        val mutedWords: MutedWordsRepository,
        val privacyScreen: PrivacyScreenRepository,
    )

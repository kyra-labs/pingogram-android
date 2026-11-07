package dev.kyralabs.pingogram.feature.settings.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.AppLanguageRepository
import dev.kyralabs.pingogram.core.domain.repository.SessionRepository
import dev.kyralabs.pingogram.core.domain.repository.StorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The values the experimental look's Settings home shows beside its rows (`docs/DESIGN_SYSTEM.md` §13.9): how many
 * sessions are active ([sessionCount]), how much the account's files and database take ([storageBytes]), and the app's
 * language ([languageTag], null while it follows the system's). Each is null until known, and stays null when TDLib
 * could not tell; the rows then show their plain summaries.
 */
@Immutable
data class SettingsHomeSummaries(
    val sessionCount: Int? = null,
    val storageBytes: Long? = null,
    val languageTag: String? = null,
)

/**
 * Reads [SettingsHomeSummaries] for the experimental look's Settings home: the active sessions and the storage once
 * (TDLib answers both from its memory or database; `getActiveSessions`, `getStorageStatisticsFast`), the language as
 * it changes. Only the experimental look asks for this view model, so the standard one sends nothing more.
 */
@HiltViewModel
class SettingsHomeViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        private val storage: StorageRepository,
        private val language: AppLanguageRepository,
    ) : ViewModel() {
        private val mutableSummaries = MutableStateFlow(SettingsHomeSummaries())
        val summaries: StateFlow<SettingsHomeSummaries> = mutableSummaries.asStateFlow()

        init {
            viewModelScope.launch {
                val count = (sessions.activeSessions() as? Outcome.Success)?.value?.sessions?.size
                mutableSummaries.update { it.copy(sessionCount = count) }
            }
            viewModelScope.launch {
                val usage = (storage.storageUsage() as? Outcome.Success)?.value
                mutableSummaries.update { it.copy(storageBytes = usage?.let { u -> u.filesSize + u.databaseSize }) }
            }
            viewModelScope.launch {
                language.language.collect { tag -> mutableSummaries.update { it.copy(languageTag = tag) } }
            }
        }
    }

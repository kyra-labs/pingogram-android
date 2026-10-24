package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ActiveSessions
import dev.kyralabs.pingogram.core.domain.model.BlockedSendersPage
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacyRulesChange
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.domain.model.TwoStepVerificationState
import dev.kyralabs.pingogram.core.domain.repository.AccountRepository
import dev.kyralabs.pingogram.core.domain.repository.BlockListRepository
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import dev.kyralabs.pingogram.core.domain.repository.PrivacyRepository
import dev.kyralabs.pingogram.core.domain.repository.SessionRepository
import dev.kyralabs.pingogram.core.domain.repository.TwoStepVerificationRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Privacy and security page (`REQUIREMENTS.md` FR-S1): the main choice of every privacy setting, how many users and
 * chats are blocked, how many sessions the account has, whether 2-step verification is on, whether the passcode
 * lock is on, which follows its settings as they change ([PasscodeRepository]), and how long the account may stay
 * unused before Telegram deletes it ([AccountRepository]), shown on "Delete account".
 *
 * Everything loads at once when the page opens; if anything fails, the page shows why and offers to try again. TDLib's
 * changes of the privacy settings apply as they come, made here or elsewhere, so the page shows a choice made on a
 * setting's page as soon as it is back. The counts and 2-step verification load again whenever the page shows again
 * ([PrivacySecurityEvent.Refresh]), keeping what shows meanwhile and if that fails: there are no updates for them.
 */
@Suppress("LongParameterList") // One repository per part of the page, as the page sums up each of their pages.
@HiltViewModel
class PrivacySecurityViewModel
    @Inject
    constructor(
        private val privacyRepository: PrivacyRepository,
        private val blockListRepository: BlockListRepository,
        private val sessionRepository: SessionRepository,
        private val twoStepVerificationRepository: TwoStepVerificationRepository,
        private val passcodeRepository: PasscodeRepository,
        private val accountRepository: AccountRepository,
        private val timeFormats: SettingsTimeFormats,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow<PrivacySecurityUiState>(PrivacySecurityUiState.Loading)
        val uiState: StateFlow<PrivacySecurityUiState> = mutableUiState.asStateFlow()

        private var loadJob: Job? = null

        /** The changes TDLib reported since the running load started; its answers may predate them. */
        private val changedDuringLoad = mutableMapOf<PrivacySetting, PrivacyRules>()

        /** Whether the passcode lock is on, as its settings last said. */
        private var passcodeLock = false

        init {
            // Subscribed before the first request, so no change in between is missed.
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                privacyRepository.changes.collect(::onChange)
            }
            viewModelScope.launch {
                passcodeRepository.settings.collect { settings ->
                    passcodeLock = settings.isSet
                    mutableUiState.update { state ->
                        if (state is PrivacySecurityUiState.Content) {
                            state.copy(
                                passcodeLock = settings.isSet,
                            )
                        } else {
                            state
                        }
                    }
                }
            }
            load(keepContent = false)
        }

        fun onEvent(event: PrivacySecurityEvent) {
            when (event) {
                PrivacySecurityEvent.Retry -> {
                    if (uiState.value is PrivacySecurityUiState.Error) load(keepContent = false)
                }

                PrivacySecurityEvent.Refresh -> {
                    if (loadJob?.isActive != true) load(keepContent = true)
                }
            }
        }

        private fun onChange(change: PrivacyRulesChange) {
            if (loadJob?.isActive == true) changedDuringLoad[change.setting] = change.rules
            mutableUiState.update { state ->
                if (state is PrivacySecurityUiState.Content) {
                    state.copy(rules = state.rules.toPersistentMap().putting(change.setting, change.rules))
                } else {
                    state
                }
            }
        }

        /** Loads everything; with [keepContent], what shows stays meanwhile, and if loading fails. */
        private fun load(keepContent: Boolean) {
            loadJob?.cancel()
            changedDuringLoad.clear()
            if (!keepContent || uiState.value !is PrivacySecurityUiState.Content) {
                mutableUiState.value = PrivacySecurityUiState.Loading
            }
            loadJob =
                viewModelScope.launch {
                    when (val loaded = fetch()) {
                        is Outcome.Success -> {
                            val content = loaded.value
                            mutableUiState.value =
                                content.copy(
                                    rules = (content.rules + changedDuringLoad).toImmutableMap(),
                                    passcodeLock = passcodeLock,
                                )
                        }

                        is Outcome.Failure -> {
                            if (uiState.value !is PrivacySecurityUiState.Content) {
                                mutableUiState.value = PrivacySecurityUiState.Error(loaded.error)
                            }
                        }
                    }
                    changedDuringLoad.clear()
                }
        }

        /** Everything the page shows, asked for at once; the first failure fails it all. */
        private suspend fun fetch(): Outcome<PrivacySecurityUiState.Content> =
            coroutineScope {
                val rules = PrivacySettings.associateWith { async { privacyRepository.rules(it) } }
                val blocked = async { blockListRepository.blockedSenders(offset = 0, limit = 1) }
                val sessions = async { sessionRepository.activeSessions() }
                val password = async { twoStepVerificationRepository.state() }
                val accountTtl = async { accountRepository.accountTtl() }
                val autoDelete = async { accountRepository.defaultMessageAutoDeleteTime() }
                val loadedRules = rules.mapValues { it.value.await() }
                val outcomes =
                    loadedRules.values +
                        listOf(
                            blocked.await(),
                            sessions.await(),
                            password.await(),
                            accountTtl.await(),
                            autoDelete.await(),
                        )
                val error = outcomes.firstNotNullOfOrNull { (it as? Outcome.Failure)?.error }
                if (error != null) {
                    Outcome.Failure(error)
                } else {
                    Outcome.Success(
                        content(
                            rules = loadedRules.mapValues { it.value.value() },
                            blocked = blocked.await().value(),
                            sessions = sessions.await().value(),
                            password = password.await().value(),
                            accountTtlDays = accountTtl.await().value(),
                            autoDeleteSeconds = autoDelete.await().value(),
                        ),
                    )
                }
            }

        private fun content(
            rules: Map<PrivacySetting, PrivacyRules>,
            blocked: BlockedSendersPage,
            sessions: ActiveSessions,
            password: TwoStepVerificationState,
            accountTtlDays: Int,
            autoDeleteSeconds: Int,
        ) = PrivacySecurityUiState.Content(
            rules = rules.toImmutableMap(),
            blockedCount = blocked.totalCount,
            sessionCount = sessions.sessions.size,
            twoStepVerification = password.summary(),
            accountTtlDays = accountTtlDays,
            loginEmailPattern = password.loginEmailPattern,
            defaultAutoDeleteSeconds = autoDeleteSeconds,
        )

        private fun TwoStepVerificationState.summary(): TwoStepVerificationSummary {
            // Isolated in the sentence (`bidiIsolated`, `docs/DESIGN_SYSTEM.md` §9).
            val pattern = pendingRecoveryEmailPattern?.bidiIsolated()
            val status =
                when {
                    !hasPassword -> UiText.resource(R.string.settings_two_step_verification_off)
                    pattern != null -> UiText.resource(R.string.settings_two_step_verification_pending_email, pattern)
                    hasRecoveryEmail -> UiText.resource(R.string.settings_two_step_verification_on_recovery_email)
                    else -> UiText.resource(R.string.settings_two_step_verification_on_no_recovery_email)
                }
            return TwoStepVerificationSummary(
                status = status,
                pendingResetDate = pendingResetDate?.let { timeFormats.formatter().dateTime(it) },
            )
        }
    }

/** The value of a success; only for outcomes known to be one. */
private fun <T> Outcome<T>.value(): T = (this as Outcome.Success).value

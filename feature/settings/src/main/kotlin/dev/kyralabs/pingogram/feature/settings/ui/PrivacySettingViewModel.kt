package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.domain.repository.PrivacyRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The page of one privacy [setting] (`REQUIREMENTS.md` FR-S1): its main choice, which the user changes, and its
 * exceptions, counted, which stay as they are.
 *
 * - A selected choice shows at once and is saved; the choices wait meanwhile. If saving fails, the choice that was
 *   there shows again, with the error in a snackbar (none for `DomainError.Silent`).
 * - TDLib's changes of the setting apply as they come, made here or on another device.
 * - Voice and video messages ([PrivacySetting.VoiceMessages]) can only be limited with Telegram Premium: without it the
 *   page offers "Everybody" only ([PrivacySettingUiState.premiumRequired]), and if Telegram still refuses a choice
 *   (`DomainError.PremiumRequired`), it says so. Nothing pretends otherwise.
 * - Last seen ([PrivacySetting.LastSeen]) also shows whether the read time shows; a switch shows at once, is saved, and
 *   switches back with the error if Telegram refuses. Where it cannot be read, the page leaves it out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = PrivacySettingViewModel.Factory::class)
class PrivacySettingViewModel
    @AssistedInject
    constructor(
        @Assisted private val setting: PrivacySetting,
        private val privacyRepository: PrivacyRepository,
        userRepository: UserRepository,
    ) : ViewModel() {
        /** Creates the view model of the page of a setting. */
        @AssistedFactory
        interface Factory {
            fun create(setting: PrivacySetting): PrivacySettingViewModel
        }

        private val mutableUiState = MutableStateFlow(PrivacySettingUiState(setting))
        val uiState: StateFlow<PrivacySettingUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<PrivacySettingEffect>(Channel.BUFFERED)
        val effects: Flow<PrivacySettingEffect> = effectChannel.receiveAsFlow()

        private var loadJob: Job? = null

        /** A change came while the rules loaded, so their answer may be older. */
        private var changedDuringLoad = false

        init {
            // Subscribed before the first request, so no change in between is missed.
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                privacyRepository.changes
                    .filter { it.setting == setting }
                    .collect { change ->
                        if (loadJob?.isActive == true) changedDuringLoad = true
                        mutableUiState.update { it.copy(rules = PrivacyRulesState.Loaded(change.rules)) }
                    }
            }
            if (setting == PrivacySetting.VoiceMessages) {
                userRepository.myUserId
                    .flatMapLatest { id -> if (id == null) flowOf(null) else userRepository.user(id) }
                    // An unknown user is not taken for one without Premium: Telegram decides then.
                    .map { user -> user != null && !user.isPremium }
                    .distinctUntilChanged()
                    .onEach { required -> mutableUiState.update { it.copy(premiumRequired = required) } }
                    .launchIn(viewModelScope)
            }
            load()
            if (setting == PrivacySetting.LastSeen) {
                viewModelScope.launch {
                    val shows = privacyRepository.showsReadDate()
                    if (shows is Outcome.Success) mutableUiState.update { it.copy(showsReadTime = shows.value) }
                }
            }
        }

        fun onEvent(event: PrivacySettingEvent) {
            when (event) {
                is PrivacySettingEvent.Select -> select(event.choice)
                PrivacySettingEvent.Retry -> if (uiState.value.rules is PrivacyRulesState.Failed) load()
                is PrivacySettingEvent.ShowReadTime -> showReadTime(event.shows)
            }
        }

        /** Shows [shows] at once and saves it; a switch made meanwhile is saved after it, as each one is. */
        private fun showReadTime(shows: Boolean) {
            val before = uiState.value.showsReadTime ?: return
            if (before == shows) return
            mutableUiState.update { it.copy(showsReadTime = shows) }
            viewModelScope.launch {
                val saved = privacyRepository.setShowsReadDate(shows)
                if (saved is Outcome.Failure) {
                    mutableUiState.update { if (it.showsReadTime == shows) it.copy(showsReadTime = before) else it }
                    if (saved.error != DomainError.Silent) {
                        effectChannel.trySend(PrivacySettingEffect.ShowError(saved.error))
                    }
                }
            }
        }

        private fun load() {
            loadJob?.cancel()
            changedDuringLoad = false
            mutableUiState.update { it.copy(rules = PrivacyRulesState.Loading) }
            loadJob =
                viewModelScope.launch {
                    val loaded =
                        when (val rules = privacyRepository.rules(setting)) {
                            is Outcome.Success -> PrivacyRulesState.Loaded(rules.value)
                            is Outcome.Failure -> PrivacyRulesState.Failed(rules.error)
                        }
                    if (!changedDuringLoad) mutableUiState.update { it.copy(rules = loaded) }
                }
        }

        /** Saves one choice at a time; another one selected meanwhile (a double tap) does nothing. */
        private fun select(choice: PrivacyChoice) {
            val state = uiState.value
            val rules = (state.rules as? PrivacyRulesState.Loaded)?.rules ?: return
            val allowed = !state.premiumRequired || choice == PrivacyChoice.Everybody
            if (state.savingChoice != null || choice == rules.choice || !allowed) return
            mutableUiState.update { it.copy(savingChoice = choice) }
            viewModelScope.launch {
                when (val saved = privacyRepository.setChoice(setting, choice)) {
                    is Outcome.Success -> {
                        mutableUiState.update {
                            it.copy(rules = PrivacyRulesState.Loaded(saved.value), savingChoice = null)
                        }
                    }

                    is Outcome.Failure -> {
                        mutableUiState.update { it.copy(savingChoice = null) }
                        if (saved.error != DomainError.Silent) {
                            effectChannel.trySend(PrivacySettingEffect.ShowError(saved.error))
                        }
                    }
                }
            }
        }
    }

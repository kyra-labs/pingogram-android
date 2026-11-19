package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.GroupBubbles
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Appearance page shows (`REQUIREMENTS.md` FR-S2): nothing until the settings are read, then the settings. */
@Immutable
sealed interface AppearanceUiState {
    data object Loading : AppearanceUiState

    /** The [settings], and how the chat list looks: its story rings and chat type icons ([chatList]). */
    data class Content(
        val settings: AppearanceSettings,
        val chatList: ChatListSettings = ChatListSettings(),
    ) : AppearanceUiState
}

/** What the user changes on the Appearance page, handled by [AppearanceViewModel.onEvent]. */
sealed interface AppearanceEvent {
    data class SetThemeMode(
        val themeMode: ThemeMode,
    ) : AppearanceEvent

    data class SetAmoled(
        val enabled: Boolean,
    ) : AppearanceEvent

    data class SetContrast(
        val contrast: Contrast,
    ) : AppearanceEvent

    /** "Show stories" turned on or off. */
    data class SetShowStories(
        val show: Boolean,
    ) : AppearanceEvent

    /** "Chat type icons" turned on or off. */
    data class SetShowChatTypeIcons(
        val show: Boolean,
    ) : AppearanceEvent

    /** A color theme picked from the row of chips. */
    data class SetColorTheme(
        val option: ColorThemeOption,
    ) : AppearanceEvent

    /** How group bubbles are colored (`docs/DESIGN_SYSTEM.md` §13.6). */
    data class SetGroupBubbles(
        val bubbles: GroupBubbles,
    ) : AppearanceEvent

    /** "Reset": the look's settings back to their defaults ([AppearanceSettings]'s). */
    data object Reset : AppearanceEvent

    /** "Undo" after a reset: the settings as they were before it. */
    data object UndoReset : AppearanceEvent
}

/** One-off side effects of [AppearanceViewModel], handled once by its route. */
sealed interface AppearanceEffect {
    /** A setting could not be saved, shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : AppearanceEffect

    /** The look's settings were reset, shown in a snackbar with "Undo". */
    data object ResetDone : AppearanceEffect
}

/**
 * The Appearance page (`REQUIREMENTS.md` FR-S2): the saved settings, and saving each change. The page shows what is
 * saved, which the app's theme follows too, so a change shows on the page and in the theme at once, and a change that
 * cannot be saved changes neither. Nothing to restore after process death: the settings are saved. Its "Chat list"
 * group holds how the chat list looks ([ChatListSettingsRepository]); how chats behave is the Chats page's.
 */
@HiltViewModel
class AppearanceViewModel
    @Inject
    constructor(
        private val appearanceRepository: AppearanceRepository,
        private val chatListSettingsRepository: ChatListSettingsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<AppearanceUiState> =
            combine<AppearanceSettings, ChatListSettings, AppearanceUiState>(
                appearanceRepository.appearance,
                chatListSettingsRepository.settings,
            ) { settings, chatList -> AppearanceUiState.Content(settings, chatList) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceUiState.Loading)

        private val effectChannel = Channel<AppearanceEffect>(Channel.BUFFERED)
        val effects: Flow<AppearanceEffect> = effectChannel.receiveAsFlow()

        /** The settings before the last reset, for its "Undo"; in memory, as the snackbar does not outlive the page. */
        private var beforeReset: AppearanceSettings? = null

        fun onEvent(event: AppearanceEvent) {
            viewModelScope.launch {
                val saved = saveLook(event) ?: saveChatList(event)
                saved?.onFailure { effectChannel.trySend(AppearanceEffect.ShowError(it)) }
            }
        }

        /** Saves an [event] that changes how the app looks, or answers null for one that does not. */
        private suspend fun saveLook(event: AppearanceEvent): Outcome<Unit>? =
            when (event) {
                AppearanceEvent.Reset -> reset()
                AppearanceEvent.UndoReset -> beforeReset?.let { apply(it) }.also { beforeReset = null }
                is AppearanceEvent.SetThemeMode -> appearanceRepository.setThemeMode(event.themeMode)
                is AppearanceEvent.SetAmoled -> appearanceRepository.setAmoled(event.enabled)
                is AppearanceEvent.SetContrast -> appearanceRepository.setContrast(event.contrast)
                is AppearanceEvent.SetGroupBubbles -> appearanceRepository.setGroupBubbles(event.bubbles)
                is AppearanceEvent.SetColorTheme -> saveColorTheme(event.option)
                else -> null
            }

        private suspend fun saveColorTheme(option: ColorThemeOption): Outcome<Unit> =
            when (option) {
                ColorThemeOption.Wallpaper -> appearanceRepository.setDynamicColor(true)
                is ColorThemeOption.Palette -> appearanceRepository.setPalette(option.palette)
            }

        /** Saves an [event] about how the chat list looks. */
        private suspend fun saveChatList(event: AppearanceEvent): Outcome<Unit>? =
            when (event) {
                is AppearanceEvent.SetShowStories -> {
                    chatListSettingsRepository.setShowStories(event.show)
                }

                is AppearanceEvent.SetShowChatTypeIcons -> {
                    chatListSettingsRepository.setShowChatTypeIcons(event.show)
                }

                else -> {
                    null
                }
            }

        private suspend fun reset(): Outcome<Unit> {
            val current = (uiState.value as? AppearanceUiState.Content)?.settings ?: return Outcome.Success(Unit)
            val saved = apply(AppearanceSettings())
            if (saved is Outcome.Success) {
                beforeReset = current
                effectChannel.trySend(AppearanceEffect.ResetDone)
            }
            return saved
        }

        /** Saves every setting of the look as [target] has it, stopping at the first that cannot be saved. */
        private suspend fun apply(target: AppearanceSettings): Outcome<Unit> {
            val writes: List<suspend () -> Outcome<Unit>> =
                listOf(
                    { appearanceRepository.setThemeMode(target.themeMode) },
                    { appearanceRepository.setAmoled(target.amoled) },
                    { appearanceRepository.setContrast(target.contrast) },
                    // A palette turns dynamic color off; the dynamic color after it says whether it stays off.
                    { appearanceRepository.setPalette(target.palette) },
                    { appearanceRepository.setDynamicColor(target.dynamicColor) },
                    { appearanceRepository.setChatTextScale(target.chat.textScale) },
                    { appearanceRepository.setBubbleCorners(target.chat.bubbleCorners) },
                    { appearanceRepository.setChatWallpaper(target.chat.wallpaper) },
                    { appearanceRepository.setGroupBubbles(target.chat.groupBubbles) },
                )
            for (write in writes) {
                val saved = write()
                if (saved is Outcome.Failure) return saved
            }
            return Outcome.Success(Unit)
        }
    }

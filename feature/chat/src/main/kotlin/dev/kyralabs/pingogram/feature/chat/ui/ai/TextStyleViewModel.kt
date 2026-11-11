package dev.kyralabs.pingogram.feature.chat.ui.ai

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.TextCompositionStyle
import dev.kyralabs.pingogram.core.domain.repository.AiTextRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A shared style of Telegram's AI editor (`TextStyleRoute`, a `t.me` link's): finds it by its name
 * (`searchTextCompositionStyle`, only reading), and adds it to the user's styles, or removes it, only on a tap. How
 * many custom styles the account may add is shown as information; past it Telegram refuses with
 * `DomainError.TooManyTextStyles`, shown in the sheet.
 */
@HiltViewModel
class TextStyleViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val ai: AiTextRepository,
    ) : ViewModel() {
        private val name: String = savedStateHandle.get<String>(ROUTE_NAME).orEmpty()
        private val found = MutableStateFlow<Found>(Found.Loading)
        private val action = MutableStateFlow(Action())

        /** The sheet. */
        val uiState: StateFlow<TextStyleUiState> =
            combine(found, ai.styles, action) { found, styles, action ->
                when (found) {
                    Found.Loading -> {
                        TextStyleUiState.Loading
                    }

                    is Found.Failed -> {
                        TextStyleUiState.Failed(found.error)
                    }

                    is Found.Style -> {
                        TextStyleUiState.Content(
                            style = found.style,
                            isAdded = styles.any { it.name == found.style.name },
                            limit = found.limit,
                            busy = action.busy,
                            error = action.error,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TextStyleUiState.Loading)

        init {
            viewModelScope.launch { load() }
        }

        fun onEvent(event: TextStyleEvent) {
            when (event) {
                TextStyleEvent.Retry -> viewModelScope.launch { load() }
                TextStyleEvent.Add -> act { ai.addStyle(name) }
                TextStyleEvent.Remove -> act { ai.removeStyle(name) }
            }
        }

        private suspend fun load() {
            found.value = Found.Loading
            found.value =
                when (val style = ai.findStyle(name)) {
                    is Outcome.Success -> Found.Style(style.value, ai.addedStyleLimit())
                    is Outcome.Failure -> Found.Failed(style.error)
                }
        }

        private fun act(request: suspend () -> Outcome<Unit>) {
            if (action.value.busy) return
            action.value = Action(busy = true)
            viewModelScope.launch {
                action.value = Action(error = (request() as? Outcome.Failure)?.error)
            }
        }

        private sealed interface Found {
            data object Loading : Found

            data class Failed(
                val error: DomainError,
            ) : Found

            data class Style(
                val style: TextCompositionStyle,
                val limit: Int?,
            ) : Found
        }

        private data class Action(
            val busy: Boolean = false,
            val error: DomainError? = null,
        )

        private companion object {
            /** `TextStyleRoute.name`, which Navigation keeps under the property's name. */
            const val ROUTE_NAME = "name"
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** The shared style's sheet. */
@Immutable
sealed interface TextStyleUiState {
    data object Loading : TextStyleUiState

    /** No such style, or Telegram could not be asked. */
    data class Failed(
        val error: DomainError,
    ) : TextStyleUiState

    /**
     * The [style], whether the user has it ([isAdded]), how many custom styles they may add ([limit], null while
     * unknown), whether Telegram is answering ([busy]) and why it last refused ([error]).
     */
    data class Content(
        val style: TextCompositionStyle,
        val isAdded: Boolean,
        val limit: Int?,
        val busy: Boolean,
        val error: DomainError?,
    ) : TextStyleUiState
}

/** What the user does in the shared style's sheet. */
sealed interface TextStyleEvent {
    data object Add : TextStyleEvent

    data object Remove : TextStyleEvent

    data object Retry : TextStyleEvent
}

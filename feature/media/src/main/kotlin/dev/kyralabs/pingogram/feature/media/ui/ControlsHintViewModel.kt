package dev.kyralabs.pingogram.feature.media.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.repository.ViewerHintsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The media viewer's one-time hint (board 25, owner decision 2026-11-15): the first time on this phone the user hides
 * the viewer's controls ([onControlsHidden]), [hints] says to show "Tap to show controls", then never again. A view
 * model of its own beside [MediaViewerViewModel], so that one takes nothing more.
 */
@HiltViewModel
class ControlsHintViewModel
    @Inject
    constructor(
        private val repository: ViewerHintsRepository,
    ) : ViewModel() {
        private val channel = Channel<Unit>(Channel.CONFLATED)

        /** Each time the hint is to show: once on this phone. */
        val hints: Flow<Unit> = channel.receiveAsFlow()

        private var asked = false

        /** The user hid the viewer's controls. */
        fun onControlsHidden() {
            if (asked) return
            asked = true
            viewModelScope.launch {
                if (repository.controlsHintShown.first()) return@launch
                repository.setControlsHintShown()
                channel.send(Unit)
            }
        }
    }

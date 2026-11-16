package dev.kyralabs.pingogram.feature.chats.ui.focus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks the chat list to open its Focus switcher from outside it: the Quick Settings tile's long press (board 31), which
 * `:app`'s activity receives. In memory, like the share inbox: [FocusSwitchViewModel] takes the request once the chat
 * list shows, so it opens once.
 */
@Singleton
class FocusSwitcherRequests
    @Inject
    constructor() {
        private val mutableRequested = MutableStateFlow(false)

        /** Whether the switcher was asked for and has not opened yet. */
        val requested: StateFlow<Boolean> = mutableRequested.asStateFlow()

        fun request() {
            mutableRequested.value = true
        }

        /** Takes the request: true once for each [request]. */
        fun take(): Boolean = mutableRequested.compareAndSet(expect = true, update = false)
    }

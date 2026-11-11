package dev.kyralabs.pingogram.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.CustomEmoji
import dev.kyralabs.pingogram.core.domain.repository.CustomEmojiRepository
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The custom emoji of a profile's emoji status (the owner's board 22a): [show] loads one through
 * [CustomEmojiRepository], whose [customEmoji] the route hands to the profile's custom emoji renderer. Apart from
 * [ProfileViewModel], so a status changing or loading recomposes the title alone.
 */
@HiltViewModel
class EmojiStatusViewModel
    @Inject
    constructor(
        private val repository: CustomEmojiRepository,
    ) : ViewModel() {
        val customEmoji: StateFlow<ImmutableMap<Long, CustomEmoji>> get() = repository.customEmoji

        /** Loads custom emoji [id], once; 0 is no status. */
        fun show(id: Long) {
            if (id == 0L || id in repository.customEmoji.value) return
            viewModelScope.launch { repository.load(listOf(id)) }
        }
    }

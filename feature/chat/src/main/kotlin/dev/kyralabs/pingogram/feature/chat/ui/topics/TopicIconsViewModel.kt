package dev.kyralabs.pingogram.feature.chat.ui.topics

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
 * The custom emoji of the topics' own icons (td_api.tl `forumTopicIcon.custom_emoji_id`): [show] loads those not loaded
 * through [CustomEmojiRepository], whose [customEmoji] the route hands to its custom emoji renderer. Apart from
 * [ForumTopicsViewModel], so an icon loading recomposes the icons alone. Choosing an icon stays left out.
 */
@HiltViewModel
class TopicIconsViewModel
    @Inject
    constructor(
        private val repository: CustomEmojiRepository,
    ) : ViewModel() {
        val customEmoji: StateFlow<ImmutableMap<Long, CustomEmoji>> get() = repository.customEmoji

        /** Loads the custom emoji [ids] not loaded yet; 0 is no custom emoji. */
        fun show(ids: Collection<Long>) {
            val missing = ids.filter { it != 0L && it !in repository.customEmoji.value }
            if (missing.isNotEmpty()) viewModelScope.launch { repository.load(missing.distinct()) }
        }
    }

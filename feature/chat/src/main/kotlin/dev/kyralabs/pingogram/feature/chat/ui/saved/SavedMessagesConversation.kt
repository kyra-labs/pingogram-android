package dev.kyralabs.pingogram.feature.chat.ui.saved

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.ui.component.GrowingVisibility
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.transition.rememberSeekableSteps
import dev.kyralabs.pingogram.feature.chat.R

/**
 * Saved Messages with its tabs (`docs/DESIGN_SYSTEM.md` §6 "Saved Messages tabs") and its tags (§6 "Saved Messages
 * tags"): the [conversation] of the tab selected, "All" (the whole chat, at [messageId] if not null) or one of its
 * topics, and the tabs in its top bar ([LocalConversationTabs]) while there are two topics or more. A tab fades through
 * to the next on the effects springs, as the chat list's folders do. Each tab's conversation has a view model store of
 * its own, cleared once another tab shows, so a tab left keeps no history in memory; the tabs and the selection are
 * [SavedTabsViewModel]'s.
 *
 * On "All", the tags ([SavedTagsViewModel]) follow the tabs as chips; a selected tag shows the messages it filters
 * ([SavedTagScreen]) in the conversation's place, and a message tapped there opens the conversation at it.
 */
@Composable
internal fun SavedMessagesConversation(
    messageId: Long?,
    conversation: @Composable (topic: MessageTopic?, messageId: Long?) -> Unit,
    viewModel: SavedTabsViewModel = hiltViewModel(key = "saved-tabs"),
    tagsViewModel: SavedTagsViewModel = hiltViewModel(key = "saved-tags"),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tagsState by tagsViewModel.uiState.collectAsStateWithLifecycle()
    // The message a tag's list opened, which "All" shows; null for where the conversation was opened at.
    var openedMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    val onTagEvent = rememberTagEvents(tagsViewModel, viewModel) { openedMessageId = it }
    val showsTags = state.selectedTopicId == null
    val chips: @Composable () -> Unit = {
        SavedTagChips(
            state = tagsState,
            onSelect = tagsViewModel::select,
            onRename = { tag ->
                tagsViewModel.select(tag)
                tagsViewModel.startRename(tag)
            },
        )
    }
    val tabs = rememberTabsSlot(state, viewModel, chips.takeIf { showsTags && tagsState.tags.isNotEmpty() })
    val snackbars = remember { SnackbarHostState() }
    TagEffects(tagsViewModel, snackbars)
    val motionScheme = MaterialTheme.motionScheme
    // Back from a tag's messages follows the predictive back gesture, "All" fading in with its progress
    // (docs/DESIGN_SYSTEM.md §5.1, Q-08).
    val shown = TagView(tagsState.selected?.takeIf { showsTags })
    val steps =
        rememberSeekableSteps(
            current = shown,
            backTo = TagView(null).takeIf { shown.tag != null },
            key = { it.tag != null },
            onBack = { onTagEvent(SavedTagEvent.ShowAll) },
        )
    steps.transition.AnimatedContent(
        transitionSpec = {
            fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec())
        },
    ) { hasTag ->
        val tag = steps.stepOf(hasTag, shown).tag
        if (tag != null) {
            SavedTagScreen(tagsState, tag, chips, onTagEvent, snackbars = snackbars)
        } else {
            CompositionLocalProvider(LocalConversationTabs provides tabs) {
                AnimatedContent(
                    targetState = state.selectedTopicId,
                    transitionSpec = {
                        fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec())
                    },
                    label = "savedTabs",
                ) { selected ->
                    CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
                        val at = (openedMessageId ?: messageId).takeIf { selected == null }
                        conversation(selected?.let(MessageTopic::SavedMessages), at)
                    }
                }
            }
        }
    }
}

/** What the conversation's place shows: a tag's messages, or the conversation where [tag] is null. */
private data class TagView(
    val tag: SavedTagChip?,
)

/** Shows the tags' snackbars: a tag renamed, or why Telegram refused. */
@Composable
private fun TagEffects(
    viewModel: SavedTagsViewModel,
    snackbars: SnackbarHostState,
) {
    val resources = LocalResources.current
    LaunchedEffect(viewModel, snackbars) {
        viewModel.effects.collect { effect ->
            val text =
                when (effect) {
                    SavedTagsEffect.Renamed -> resources.getString(R.string.saved_tags_renamed)
                    is SavedTagsEffect.ShowError -> errorMessage(resources, effect.error, showDetails = false)
                }
            snackbars.showSnackbar(text)
        }
    }
}

/**
 * What the tag's screen does: [tags] changes, and a message opened goes to [onOpen] and shows "All" again, without a
 * tag.
 */
@Composable
private fun rememberTagEvents(
    tags: SavedTagsViewModel,
    tabs: SavedTabsViewModel,
    onOpen: (Long) -> Unit,
): (SavedTagEvent) -> Unit =
    remember(tags, tabs, onOpen) {
        { event ->
            when (event) {
                SavedTagEvent.ShowAll -> {
                    tags.select(null)
                }

                SavedTagEvent.LoadMore -> {
                    tags.loadMore()
                }

                SavedTagEvent.Retry -> {
                    tags.retry()
                }

                is SavedTagEvent.Rename -> {
                    tags.startRename(event.tag)
                }

                is SavedTagEvent.SaveLabel -> {
                    tags.rename(event.label)
                }

                SavedTagEvent.DismissRename -> {
                    tags.dismissRename()
                }

                is SavedTagEvent.Open -> {
                    onOpen(event.messageId)
                    tabs.select(null)
                    tags.select(null)
                }
            }
        }
    }

/** The top bar's slot: the topic tabs while there are any, then the tag [chips] where they show; null for neither. */
@Composable
private fun rememberTabsSlot(
    state: SavedTabsUiState,
    viewModel: SavedTabsViewModel,
    chips: (@Composable () -> Unit)?,
): (@Composable () -> Unit)? =
    if (state.tabs.isEmpty() && chips == null) {
        null
    } else {
        {
            Column {
                if (state.tabs.isNotEmpty()) SavedTabs(state, viewModel::select, viewModel::loadMore)
                // The tags' chips grow in once the tags load, and shrink away on a topic's tab.
                val lastChips = remember { arrayOfNulls<@Composable () -> Unit>(1) }
                if (chips != null) lastChips[0] = chips
                GrowingVisibility(visible = chips != null) { lastChips[0]?.invoke() }
            }
        }
    }

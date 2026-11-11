package dev.kyralabs.pingogram.feature.settings.ui.stickers.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.EmojiGroup
import dev.kyralabs.pingogram.core.domain.model.StickerDraft
import dev.kyralabs.pingogram.core.domain.model.StickerSetLimits
import dev.kyralabs.pingogram.core.domain.model.StickerSetNameCheck
import dev.kyralabs.pingogram.core.domain.repository.EmojiCatalogRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerSetCreationRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Making a sticker set (`docs/DESIGN_SYSTEM.md` §6 "New sticker set", the owner's boards 21a–c) as [uiState]:
 *
 * - **Name**: the title and the link name, which follows Telegram's suggestion for the title
 *   (`getSuggestedStickerSetName`, 500 ms after the last key) until the user types one, and is checked on the device
 *   first, then by Telegram 400 ms after the last key, the newest check cancelling the one before.
 * - **Stickers**: each picked photo made into a sticker on the device, with an emoji the user chooses (a smiling face
 *   until then, as Telegram requires one), moved earlier or later, removed; "Publish" sends the set.
 * - **Published**: the set's id, for its sheet and its link.
 *
 * The step, the texts, the stickers (their copies' paths and emoji) and the set published are kept in the
 * [SavedStateHandle]. The copies are discarded once the set is published, when one is removed, and when the page
 * closes without publishing.
 */
@Suppress("TooManyFunctions") // A function per event.
@HiltViewModel
class NewStickerSetViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val repository: StickerSetCreationRepository,
        private val emojiCatalog: EmojiCatalogRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow(restored())

        val uiState: StateFlow<NewStickerSetUiState> = state.asStateFlow()

        private val effectChannel = Channel<NewStickerSetEffect>(Channel.BUFFERED)

        /** One-off effects for the screen: the photo picker, closing, haptics, snackbars. */
        val effects: Flow<NewStickerSetEffect> = effectChannel.receiveAsFlow()

        private val mutableEmoji = MutableStateFlow<ImmutableList<EmojiGroup>>(persistentListOf())

        /** The emoji to choose from, by Unicode's groups: read the first time the emoji sheet opens. */
        val emoji: StateFlow<ImmutableList<EmojiGroup>> = mutableEmoji.asStateFlow()

        private var emojiLoad: Job? = null

        private var suggestion: Job? = null
        private var check: Job? = null

        /** Whether the user typed the link name, which then no longer follows the title. */
        private var nameTyped: Boolean = savedStateHandle[KEY_NAME_TYPED] ?: false

        init {
            // A name restored after process death is checked again, and an emoji sheet that was open reads its emoji.
            if (state.value.step == NewStickerSetStep.Name && state.value.name.isNotEmpty()) checkName(state.value.name)
            if (state.value.emojiFor != null) loadEmoji()
        }

        fun onEvent(event: NewStickerSetEvent) {
            when (event) {
                is NewStickerSetEvent.TitleChanged -> changeTitle(event.title)
                is NewStickerSetEvent.NameChanged -> typeName(event.name)
                NewStickerSetEvent.Next -> next()
                NewStickerSetEvent.Back -> back()
                NewStickerSetEvent.AddSticker -> if (state.value.canAddSticker) send(NewStickerSetEffect.PickPhoto)
                is NewStickerSetEvent.PhotoPicked -> prepare(event.uri)
                is NewStickerSetEvent.ChooseEmoji -> chooseEmojiFor(event.index)
                is NewStickerSetEvent.EmojiChosen -> setEmoji(event.emoji)
                NewStickerSetEvent.CloseEmoji -> change { it.copy(emojiFor = null) }
                is NewStickerSetEvent.OpenMenu -> openMenu(event.index)
                NewStickerSetEvent.CloseMenu -> change { it.copy(menuFor = null) }
                is NewStickerSetEvent.Move -> move(event.index, event.by)
                is NewStickerSetEvent.Remove -> remove(event.index)
                NewStickerSetEvent.Publish -> publish()
            }
        }

        override fun onCleared() {
            // Copies of a set never published are not needed once the page is gone for good.
            if (state.value.published == null) state.value.stickers.forEach { repository.discardSticker(it.path) }
        }

        private fun changeTitle(title: String) {
            val limited = title.take(StickerSetLimits.TITLE_LENGTH_MAX)
            change { it.copy(title = limited, error = null) }
            if (nameTyped) return
            suggestion?.cancel()
            suggestion =
                viewModelScope.launch {
                    delay(SUGGEST_DELAY_MILLIS)
                    if (limited.isBlank()) return@launch
                    val suggested = repository.suggestName(limited)
                    if (suggested is Outcome.Success && !nameTyped) {
                        change { it.copy(name = suggested.value) }
                        checkName(suggested.value)
                    }
                }
        }

        private fun typeName(name: String) {
            val trimmed = name.trim().take(StickerSetLimits.NAME_LENGTH_MAX)
            nameTyped = true
            savedStateHandle[KEY_NAME_TYPED] = true
            suggestion?.cancel()
            change { it.copy(name = trimmed) }
            checkName(trimmed)
        }

        private fun checkName(name: String) {
            check?.cancel()
            if (name.isEmpty()) {
                change { it.copy(nameCheck = NameCheck.None) }
                return
            }
            if (!StickerSetLimits.isNameWellFormed(name)) {
                change { it.copy(nameCheck = NameCheck.Invalid) }
                return
            }
            change { it.copy(nameCheck = NameCheck.Checking) }
            check =
                viewModelScope.launch {
                    delay(CHECK_DELAY_MILLIS)
                    val checked =
                        when (val outcome = repository.checkName(name)) {
                            is Outcome.Success -> {
                                when (outcome.value) {
                                    StickerSetNameCheck.Available -> NameCheck.Available
                                    StickerSetNameCheck.Occupied -> NameCheck.Occupied
                                    StickerSetNameCheck.Invalid -> NameCheck.Invalid
                                }
                            }

                            is Outcome.Failure -> {
                                NameCheck.Failed(outcome.error)
                            }
                        }
                    if (state.value.name == name) change { it.copy(nameCheck = checked) }
                }
        }

        private fun next() {
            if (state.value.step != NewStickerSetStep.Name || !state.value.canGoOn) return
            change { it.copy(step = NewStickerSetStep.Stickers) }
        }

        private fun back() {
            when (state.value.step) {
                NewStickerSetStep.Stickers -> {
                    if (!state.value.isPublishing) {
                        change {
                            it.copy(
                                step = NewStickerSetStep.Name,
                            )
                        }
                    }
                }

                NewStickerSetStep.Name, NewStickerSetStep.Published -> {
                    send(NewStickerSetEffect.Close)
                }
            }
        }

        private fun prepare(uri: String) {
            if (!state.value.canAddSticker) return
            change { it.copy(preparing = it.preparing + 1) }
            viewModelScope.launch {
                val prepared = repository.prepareSticker(uri)
                change { it.copy(preparing = it.preparing - 1) }
                when (prepared) {
                    is Outcome.Success -> {
                        change {
                            val stickers = it.stickers + StickerTile(prepared.value, DEFAULT_EMOJI)
                            it.copy(stickers = stickers.toImmutableList(), emojiFor = stickers.lastIndex)
                        }
                        loadEmoji()
                    }

                    is Outcome.Failure -> {
                        showError(prepared.error)
                    }
                }
            }
        }

        private fun chooseEmojiFor(index: Int) {
            if (index !in state.value.stickers.indices || state.value.isPublishing) return
            change { it.copy(emojiFor = index, menuFor = null) }
            loadEmoji()
        }

        private fun loadEmoji() {
            if (emojiLoad != null) return
            emojiLoad = viewModelScope.launch { mutableEmoji.value = emojiCatalog.catalog().toImmutableList() }
        }

        private fun setEmoji(emoji: String) {
            val index = state.value.emojiFor ?: return
            change { current ->
                val stickers = current.stickers.toMutableList()
                stickers.getOrNull(index)?.let { stickers[index] = it.copy(emoji = emoji) }
                current.copy(stickers = stickers.toImmutableList(), emojiFor = null)
            }
        }

        private fun openMenu(index: Int) {
            if (index !in state.value.stickers.indices || state.value.isPublishing) return
            change { it.copy(menuFor = index) }
        }

        private fun move(
            index: Int,
            by: Int,
        ) {
            val stickers = state.value.stickers.toMutableList()
            val target = index + by
            if (index !in stickers.indices || target !in stickers.indices || state.value.isPublishing) return
            stickers.add(target, stickers.removeAt(index))
            change { it.copy(stickers = stickers.toImmutableList(), menuFor = null) }
        }

        private fun remove(index: Int) {
            val stickers = state.value.stickers.toMutableList()
            if (index !in stickers.indices || state.value.isPublishing) return
            repository.discardSticker(stickers.removeAt(index).path)
            change { it.copy(stickers = stickers.toImmutableList(), menuFor = null) }
        }

        private fun publish() {
            val current = state.value
            if (current.step != NewStickerSetStep.Stickers || !current.canPublish) return
            change { it.copy(isPublishing = true, error = null) }
            viewModelScope.launch {
                val drafts = current.stickers.map { StickerDraft(it.path, it.emoji) }
                when (val created = repository.create(current.title, current.name, drafts)) {
                    is Outcome.Success -> {
                        current.stickers.forEach { repository.discardSticker(it.path) }
                        change {
                            it.copy(isPublishing = false, step = NewStickerSetStep.Published, published = created.value)
                        }
                        send(NewStickerSetEffect.Published)
                    }

                    is Outcome.Failure -> {
                        change { it.copy(isPublishing = false, error = created.error) }
                        showError(created.error)
                    }
                }
            }
        }

        private fun change(update: (NewStickerSetUiState) -> NewStickerSetUiState) {
            state.update(update)
            save(state.value)
        }

        private fun save(current: NewStickerSetUiState) {
            savedStateHandle[KEY_STEP] = current.step.name
            savedStateHandle[KEY_TITLE] = current.title
            savedStateHandle[KEY_NAME] = current.name
            savedStateHandle[KEY_PATHS] = ArrayList(current.stickers.map { it.path })
            savedStateHandle[KEY_EMOJI] = ArrayList(current.stickers.map { it.emoji })
            savedStateHandle[KEY_PUBLISHED] = current.published
            savedStateHandle[KEY_EMOJI_FOR] = current.emojiFor
        }

        private fun restored(): NewStickerSetUiState {
            val paths = savedStateHandle.get<ArrayList<String>>(KEY_PATHS).orEmpty()
            val emoji = savedStateHandle.get<ArrayList<String>>(KEY_EMOJI).orEmpty()
            val published = savedStateHandle.get<Long>(KEY_PUBLISHED)
            val step =
                savedStateHandle.get<String>(KEY_STEP)?.let { saved ->
                    NewStickerSetStep.entries.firstOrNull {
                        it.name ==
                            saved
                    }
                }
                    ?: NewStickerSetStep.Name
            return NewStickerSetUiState(
                // A publishing that process death interrupted shows the stickers again, to publish once more.
                step =
                    if (step == NewStickerSetStep.Published &&
                        published == null
                    ) {
                        NewStickerSetStep.Stickers
                    } else {
                        step
                    },
                title = savedStateHandle[KEY_TITLE] ?: "",
                name = savedStateHandle[KEY_NAME] ?: "",
                stickers = paths.zip(emoji) { path, chosen -> StickerTile(path, chosen) }.toImmutableList(),
                published = published,
                emojiFor = savedStateHandle.get<Int>(KEY_EMOJI_FOR)?.takeIf { it in paths.indices },
            )
        }

        private fun send(effect: NewStickerSetEffect) {
            effectChannel.trySend(effect)
        }

        private fun showError(error: DomainError) {
            if (error != DomainError.Silent) send(NewStickerSetEffect.ShowError(error))
        }

        private companion object {
            const val SUGGEST_DELAY_MILLIS = 500L
            const val CHECK_DELAY_MILLIS = 400L

            /** A sticker's emoji until the user chooses one: Telegram requires one. */
            const val DEFAULT_EMOJI = "🙂"

            const val KEY_STEP = "newStickerSet.step"
            const val KEY_TITLE = "newStickerSet.title"
            const val KEY_NAME = "newStickerSet.name"
            const val KEY_NAME_TYPED = "newStickerSet.nameTyped"
            const val KEY_PATHS = "newStickerSet.paths"
            const val KEY_EMOJI = "newStickerSet.emoji"
            const val KEY_PUBLISHED = "newStickerSet.published"
            const val KEY_EMOJI_FOR = "newStickerSet.emojiFor"
        }
    }

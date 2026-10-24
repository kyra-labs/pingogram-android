package dev.kyralabs.pingogram.feature.chat.ui.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.EmojiGroup
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import dev.kyralabs.pingogram.core.domain.repository.EmojiCatalogRepository
import dev.kyralabs.pingogram.core.domain.repository.InlineBotRepository
import dev.kyralabs.pingogram.core.domain.repository.RecentEmojiRepository
import dev.kyralabs.pingogram.core.domain.repository.SavedGifRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The composer's emoji and sticker panel (`REQUIREMENTS.md` FR-C5). Sending a sticker goes through the conversation
 * (`ChatEvent.SendSticker`, which takes the composer's reply along), and an emoji goes into the composer's text there.
 *
 * - **Opening** ([EmojiPanelEvent.Opened]): nothing loads until the panel first opens, then both tabs load at once,
 *   so switching between them never waits.
 * - **Emoji**: the bundled catalog ([EmojiCatalogRepository], without what the device cannot draw) after the recent
 *   emoji ([RecentEmojiRepository]), which a picked emoji joins first ([EmojiPanelEvent.EmojiPicked]).
 * - **Stickers**: the favorite and recent stickers and the installed sets ([StickerRepository]), each following its
 *   changes. A set's stickers load when its section comes near the screen ([EmojiPanelEvent.SetsVisible]), the first
 *   [PRELOADED_SETS] sets at once; meanwhile it shows as many placeholders as it has stickers. A set that fails to
 *   load is asked for again when it next comes into view. Without installed sets ([StickerTab.Error], "Try again":
 *   [EmojiPanelEvent.Retry]) nothing shows; favorites or recent stickers that fail are left out.
 * - **GIFs** ([PanelGifs]): the saved GIFs, and the search through Telegram's GIF bot, as typed in this chat.
 * - Reading stops [STOP_TIMEOUT_MILLIS] after the screen stops collecting [uiState] (the app went to the background);
 *   loaded sets stay.
 */
@HiltViewModel(assistedFactory = EmojiPanelViewModel.Factory::class)
class EmojiPanelViewModel
    @AssistedInject
    constructor(
        @Assisted chatId: Long,
        private val emojiCatalog: EmojiCatalogRepository,
        private val recentEmoji: RecentEmojiRepository,
        private val stickers: StickerRepository,
        savedGifs: SavedGifRepository,
        inlineBots: InlineBotRepository,
    ) : ViewModel() {
        /** Makes the panel of chat [chatId], whose GIF searches are asked as typed in it. */
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long): EmojiPanelViewModel
        }

        private val gifs = PanelGifs(viewModelScope, chatId, savedGifs, inlineBots)

        private val opened = MutableStateFlow(false)
        private val attempt = MutableStateFlow(0)
        private val loadedSets = MutableStateFlow<PersistentMap<Long, ImmutableList<Sticker>>>(persistentMapOf())

        /** Sets loaded or loading: asked for once each, unless the request failed. */
        private val requestedSets = mutableSetOf<Long>()

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<EmojiPanelUiState> =
            opened
                .flatMapLatest { isOpen ->
                    if (isOpen) {
                        combine(emojiTab(), stickerTab(), gifs.tab(), ::EmojiPanelUiState)
                    } else {
                        flowOf(EmojiPanelUiState())
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), EmojiPanelUiState())

        fun onEvent(event: EmojiPanelEvent) {
            when (event) {
                EmojiPanelEvent.Opened -> opened.value = true
                is EmojiPanelEvent.EmojiPicked -> viewModelScope.launch { recentEmoji.addRecent(event.emoji) }
                is EmojiPanelEvent.SetsVisible -> event.setIds.forEach(::loadSet)
                EmojiPanelEvent.Retry -> attempt.value++
                is EmojiPanelEvent.GifQueryChanged -> gifs.onQueryChanged(event.query)
                EmojiPanelEvent.MoreGifs -> gifs.loadMore()
                EmojiPanelEvent.RetryGifs -> gifs.retry()
                is EmojiPanelEvent.RemoveSavedGif -> gifs.remove(event.gif)
            }
        }

        private fun emojiTab(): Flow<EmojiTab> =
            combine(flow { emit(emojiCatalog.catalog()) }, recentEmoji.recentEmoji, ::emojiContent)
                .onStart { emit(EmojiTab.Loading) }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun stickerTab(): Flow<StickerTab> =
            attempt.flatMapLatest {
                combine(
                    stickers.installedSets().onEach { sets ->
                        sets.getOrNull()?.take(PRELOADED_SETS)?.forEach { loadSet(it.id) }
                    },
                    stickers.favoriteStickers(),
                    stickers.recentStickers(),
                    loadedSets,
                ) { sets, favorites, recent, loaded ->
                    when (sets) {
                        is Outcome.Failure -> {
                            StickerTab.Error(sets.error)
                        }

                        is Outcome.Success -> {
                            stickerContent(
                                sets.value,
                                favorites.getOrNull(),
                                recent.getOrNull(),
                                loaded,
                            )
                        }
                    }
                }.onStart { emit(StickerTab.Loading) }
            }

        private fun loadSet(setId: Long) {
            if (!requestedSets.add(setId)) return
            viewModelScope.launch {
                when (val result = stickers.stickerSet(setId)) {
                    is Outcome.Success -> loadedSets.update { it.put(setId, result.value.toImmutableList()) }
                    is Outcome.Failure -> requestedSets.remove(setId)
                }
            }
        }

        private companion object {
            /** Sets whose stickers load as soon as the list of sets is known: what the panel shows first. */
            const val PRELOADED_SETS = 3
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** The emoji tab: the [recent] emoji first, when there are any, then the catalog's non-empty [groups]. */
internal fun emojiContent(
    groups: List<EmojiGroup>,
    recent: List<String>,
): EmojiTab {
    val sections =
        buildList {
            if (recent.isNotEmpty()) add(EmojiSection(category = null, emoji = recent.toImmutableList()))
            groups.filter { it.emoji.isNotEmpty() }.forEach { add(EmojiSection(it.category, it.emoji)) }
        }
    return EmojiTab.Content(sections.toImmutableList())
}

/**
 * The stickers tab: [favorites] and [recent] stickers (null when they could not be read), then every one of [sets],
 * with its stickers once [loaded], placeholders before; [StickerTab.Empty] when there is nothing at all.
 */
internal fun stickerContent(
    sets: List<StickerSetSummary>,
    favorites: List<Sticker>?,
    recent: List<Sticker>?,
    loaded: Map<Long, ImmutableList<Sticker>>,
): StickerTab {
    val sections =
        buildList {
            if (!favorites.isNullOrEmpty()) {
                add(StickerSection("favorites", StickerSectionKind.Favorites, favorites.toImmutableList()))
            }
            if (!recent.isNullOrEmpty()) {
                add(
                    StickerSection("recent", StickerSectionKind.Recent, recent.toImmutableList()),
                )
            }
            sets.forEach { set ->
                val stickers = loaded[set.id]
                add(
                    StickerSection(
                        key = "set:${set.id}",
                        kind = StickerSectionKind.Set(set.id, set.title, set.cover),
                        stickers = stickers ?: NoStickers,
                        placeholders = if (stickers == null) set.size else 0,
                    ),
                )
            }
        }
    return if (sections.isEmpty()) StickerTab.Empty else StickerTab.Content(sections.toImmutableList())
}

private val NoStickers: ImmutableList<Sticker> = emptyList<Sticker>().toImmutableList()

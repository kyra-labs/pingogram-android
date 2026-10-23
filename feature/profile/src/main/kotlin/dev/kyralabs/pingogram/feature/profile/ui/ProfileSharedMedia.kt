package dev.kyralabs.pingogram.feature.profile.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.domain.model.SharedMediaPage
import dev.kyralabs.pingogram.core.domain.repository.SharedMediaRepository
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The shared media of the profile's private chat (`REQUIREMENTS.md` FR-P2), for [ProfileViewModel]: the tab shown
 * ([select]), and each tab's pages, as far as the user scrolled ([loadMore]).
 *
 * - **Pages** of `SharedMediaRepository.PAGE_SIZE` load one at a time per tab: the first when a tab first shows, the
 *   next when its list is 20 items from its end. A tab keeps what it loaded while another shows, so switching back
 *   shows it at once; switching away cancels the tab's page on its way (TDLib's answer is dropped).
 * - **Ends**: TDLib's `next_from_message_id` 0, or a page that adds nothing, ends a tab.
 * - **Failures**: the first page's fails the tab, with "Try again" ([retry]); a later one's shows at the end of the
 *   list, which tries again as it is reached.
 * - **Items** are mapped by [mapItems] (a [SharedMediaMapper] on the view model's clock and language) as the state
 *   is read; messages that show nothing (a link TDLib found that is no link Pingogram reads) are left out.
 *
 * Runs in [scope], the view model's, on the main thread. Starts once the chat is known ([start]).
 */
internal class ProfileSharedMedia(
    private val repository: SharedMediaRepository,
    private val scope: CoroutineScope,
    initial: SharedMediaKind,
    private val onSelected: (SharedMediaKind) -> Unit,
    private val mapItems: (SharedMediaKind, List<Message>) -> List<SharedMediaItem>,
) {
    private val chatId = MutableStateFlow<Long?>(null)
    private val selected = MutableStateFlow(initial)
    private val tabs = MutableStateFlow<PersistentMap<SharedMediaKind, Tab>>(persistentMapOf())
    private var loading: Job? = null
    private var loadingKind: SharedMediaKind? = null

    /** What the profile's shared media shows, then every change. */
    val state: Flow<SharedMediaUiState> =
        combine(chatId, selected, tabs) { chatId, selected, tabs ->
            SharedMediaUiState(
                selected = selected,
                chatId = chatId,
                content = chatId?.let { tabs[selected]?.content(selected) ?: SharedMediaContent.Loading },
            )
        }

    /** Searches chat [id] from now on; the tab shown loads its first page. Nothing if it searches one already. */
    fun start(id: Long) {
        if (chatId.value != null) return
        chatId.value = id
        loadFirst(selected.value)
    }

    fun select(kind: SharedMediaKind) {
        if (selected.value == kind) return
        selected.value = kind
        onSelected(kind)
        val cancelled = loadingKind
        if (cancelled != null && cancelled != kind) {
            loading?.cancel()
            loadingKind = null
            // A page cancelled on its way is asked for again when its tab shows again.
            tabs.update { it.updating(cancelled) { tab -> tab.copy(isLoading = false) } }
        }
        val tab = tabs.value[kind]
        val notLoaded =
            tab != null && tab.firstError == null && tab.messages.isEmpty() && !tab.isLoading && !tab.endReached
        if (tab == null || notLoaded) {
            loadFirst(kind)
        }
    }

    /** Loads the shown tab's next page, unless it is loading one or has no more. */
    fun loadMore() {
        val kind = selected.value
        val tab = tabs.value[kind] ?: return
        if (tab.isLoading || tab.endReached || tab.firstError != null) return
        load(kind, tab.nextFromMessageId)
    }

    /** Loads the shown tab's first page again after it failed. */
    fun retry() {
        val kind = selected.value
        if (tabs.value[kind]?.firstError != null) loadFirst(kind)
    }

    private fun loadFirst(kind: SharedMediaKind) {
        tabs.update { it.putting(kind, Tab()) }
        load(kind, fromMessageId = 0)
    }

    private fun load(
        kind: SharedMediaKind,
        fromMessageId: Long,
    ) {
        val chat = chatId.value ?: return
        tabs.update { it.updating(kind) { tab -> tab.copy(isLoading = true, moreError = null) } }
        loadingKind = kind
        loading =
            scope.launch {
                val page = repository.page(chat, kind, fromMessageId)
                loadingKind = null
                tabs.update { current ->
                    current.updating(kind) { tab -> tab.with(page, isFirst = fromMessageId == 0L) }
                }
            }
    }

    /**
     * What a tab loaded: its [messages] newest first, where the next page starts, and how far it got.
     *
     * @property firstError why the first page failed; null if it did not.
     * @property moreError why the last page after the first failed; null if none did.
     */
    private data class Tab(
        val messages: PersistentList<Message> = persistentListOf(),
        val nextFromMessageId: Long = 0,
        val isLoading: Boolean = false,
        val endReached: Boolean = false,
        val firstError: DomainError? = null,
        val moreError: DomainError? = null,
    ) {
        fun with(
            page: Outcome<SharedMediaPage>,
            isFirst: Boolean,
        ): Tab =
            when (page) {
                is Outcome.Success -> {
                    val known = messages.map { it.id }.toSet()
                    val added = page.value.messages.filterNot { it.id in known }
                    copy(
                        messages = messages.addingAll(added),
                        nextFromMessageId = page.value.nextFromMessageId,
                        isLoading = false,
                        endReached = page.value.nextFromMessageId == 0L || added.isEmpty(),
                    )
                }

                is Outcome.Failure -> {
                    if (isFirst) {
                        copy(isLoading = false, firstError = page.error)
                    } else {
                        copy(isLoading = false, moreError = page.error)
                    }
                }
            }
    }

    private fun Tab.content(kind: SharedMediaKind): SharedMediaContent {
        firstError?.let { return SharedMediaContent.Error(it) }
        val items = mapItems(kind, messages)
        return when {
            items.isEmpty() && isLoading && messages.isEmpty() -> SharedMediaContent.Loading

            items.isEmpty() && endReached -> SharedMediaContent.Empty

            // Messages that show nothing (no link after all) leave the list empty; it asks for more at once.
            else -> SharedMediaContent.Items(items.toImmutableList(), more())
        }
    }

    private fun Tab.more(): MoreMedia =
        when {
            endReached -> MoreMedia.None
            isLoading -> MoreMedia.Loading
            moreError != null -> MoreMedia.Failed(moreError)
            else -> MoreMedia.Available
        }

    private inline fun PersistentMap<SharedMediaKind, Tab>.updating(
        kind: SharedMediaKind,
        change: (Tab) -> Tab,
    ): PersistentMap<SharedMediaKind, Tab> = get(kind)?.let { putting(kind, change(it)) } ?: this
}

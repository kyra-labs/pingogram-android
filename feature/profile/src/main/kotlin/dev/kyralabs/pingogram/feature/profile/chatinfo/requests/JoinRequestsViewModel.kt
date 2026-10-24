package dev.kyralabs.pingogram.feature.profile.chatinfo.requests

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.JoinRequest
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.JoinRequestRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.JoinRequestsRoute
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The requests to join group or channel [chatId] (`REQUIREMENTS.md` FR-P3): pages of 50, newest first, each request
 * approved or dismissed on its own (the row leaving the list once Telegram answered), or all at once; dismissing all
 * asks first. Names and photos come from [UserRepository], which TDLib's answer announces.
 */
@Suppress("TooManyFunctions", "LongParameterList") // A function per action; the repositories, clock and language.
@HiltViewModel
class JoinRequestsViewModel internal constructor(
    private val chatId: Long,
    private val repository: JoinRequestRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: JoinRequestRepository,
        chatRepository: ChatRepository,
        userRepository: UserRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
    ) : this(
        savedStateHandle.toRoute<JoinRequestsRoute>().chatId,
        repository,
        chatRepository,
        userRepository,
        clock,
        timePatterns,
        Locale::getDefault,
    )

    private val mutableUiState = MutableStateFlow<JoinRequestsUiState>(JoinRequestsUiState.Loading)
    val uiState: StateFlow<JoinRequestsUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<JoinRequestsEffect>(Channel.BUFFERED)
    val effects: Flow<JoinRequestsEffect> = effectChannel.receiveAsFlow()

    /** The requests loaded, for the next page's offset. */
    private var loaded = emptyList<JoinRequest>()

    init {
        load()
    }

    fun onEvent(event: JoinRequestsEvent) {
        when (event) {
            is JoinRequestsEvent.Approve -> process(event.userId, approve = true)
            is JoinRequestsEvent.Dismiss -> process(event.userId, approve = false)
            JoinRequestsEvent.ApproveAll -> processAll(approve = true)
            JoinRequestsEvent.DismissAll -> updateContent { it.copy(dismissAllDialog = true) }
            JoinRequestsEvent.CancelDismissAll -> updateContent { it.copy(dismissAllDialog = false) }
            JoinRequestsEvent.ConfirmDismissAll -> processAll(approve = false)
            JoinRequestsEvent.LoadMore -> loadMore()
            JoinRequestsEvent.Retry -> if (uiState.value is JoinRequestsUiState.Error) load()
            is JoinRequestsEvent.Open -> effectChannel.trySend(JoinRequestsEffect.OpenProfile(event.userId))
        }
    }

    private fun load() {
        mutableUiState.value = JoinRequestsUiState.Loading
        viewModelScope.launch {
            when (val page = repository.requests(chatId)) {
                is Outcome.Success -> {
                    loaded = page.value.requests
                    val isChannel =
                        (chatRepository.chat(chatId).first()?.type as? ChatType.Supergroup)?.isChannel == true
                    mutableUiState.value =
                        JoinRequestsUiState.Content(items(loaded), page.value.totalCount, isChannel)
                }

                is Outcome.Failure -> {
                    mutableUiState.value = JoinRequestsUiState.Error(page.error)
                }
            }
        }
    }

    private fun loadMore() {
        val content = uiState.value as? JoinRequestsUiState.Content ?: return
        if (content.loadingMore || !content.hasMore || loaded.isEmpty()) return
        mutableUiState.value = content.copy(loadingMore = true, pageError = null)
        viewModelScope.launch {
            when (val page = repository.requests(chatId, after = loaded.last())) {
                is Outcome.Success -> {
                    val known = loaded.map { it.userId }.toSet()
                    loaded = loaded + page.value.requests.filter { it.userId !in known }
                    val items = items(loaded)
                    updateContent { it.copy(requests = items, totalCount = page.value.totalCount, loadingMore = false) }
                }

                is Outcome.Failure -> {
                    updateContent { it.copy(loadingMore = false, pageError = page.error) }
                }
            }
        }
    }

    private fun process(
        userId: Long,
        approve: Boolean,
    ) {
        val content = uiState.value as? JoinRequestsUiState.Content ?: return
        if (userId in content.busy || content.isProcessingAll) return
        mutableUiState.value = content.copy(busy = (content.busy + userId).toPersistentSet())
        viewModelScope.launch {
            val outcome = repository.process(chatId, userId, approve)
            val item = content.requests.firstOrNull { it.userId == userId }
            if (outcome is Outcome.Success) loaded = loaded.filter { it.userId != userId }
            updateContent { state ->
                state.copy(
                    requests =
                        if (outcome is Outcome.Success) {
                            state.requests.filter { it.userId != userId }.toImmutableList()
                        } else {
                            state.requests
                        },
                    totalCount =
                        if (outcome is Outcome.Success) {
                            (state.totalCount - 1).coerceAtLeast(
                                0,
                            )
                        } else {
                            state.totalCount
                        },
                    busy = (state.busy - userId).toPersistentSet(),
                )
            }
            when {
                outcome is Outcome.Failure -> showError(outcome.error)
                approve && item != null -> effectChannel.trySend(JoinRequestsEffect.Approved(item.name))
            }
        }
    }

    private fun processAll(approve: Boolean) {
        val content = uiState.value as? JoinRequestsUiState.Content ?: return
        if (content.isProcessingAll) return
        mutableUiState.value = content.copy(isProcessingAll = true, dismissAllDialog = false)
        viewModelScope.launch {
            when (val outcome = repository.processAll(chatId, approve)) {
                is Outcome.Success -> {
                    loaded = emptyList()
                    updateContent { it.copy(requests = persistentListOf(), totalCount = 0, isProcessingAll = false) }
                }

                is Outcome.Failure -> {
                    updateContent { it.copy(isProcessingAll = false) }
                    showError(outcome.error)
                }
            }
        }
    }

    private suspend fun items(requests: List<JoinRequest>) =
        requests
            .map { request ->
                val user = userRepository.user(request.userId).first()
                JoinRequestItem(
                    userId = request.userId,
                    name =
                        user?.takeUnless { it.isDeleted }?.fullName()
                            ?: UiText.resource(UiR.string.user_deleted_account),
                    photo = user?.profilePhoto,
                    bio = request.bio,
                    date = date(request.date),
                )
            }.toImmutableList()

    /** "Oct 30" in the user's language, or with the year when it is not this year's. */
    private fun date(epochSeconds: Long): String {
        val locale = locale()
        val zone = clock.zone()
        val date = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        val skeleton = if (date.year == clock.now().atZone(zone).year) "MMMd" else "yMMMd"
        val formatter =
            runCatching { DateTimeFormatter.ofPattern(timePatterns.bestPattern(locale, skeleton), locale) }
                .getOrElse { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
        return formatter.format(date)
    }

    private fun showError(error: DomainError) {
        if (error != DomainError.Silent) effectChannel.trySend(JoinRequestsEffect.ShowError(error))
    }

    private fun updateContent(transform: (JoinRequestsUiState.Content) -> JoinRequestsUiState.Content) =
        mutableUiState.update { if (it is JoinRequestsUiState.Content) transform(it) else it }
}

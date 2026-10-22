package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.InviteLink
import dev.kyralabs.pingogram.core.domain.repository.InviteLinkRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.InviteLinksRoute
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

/**
 * The invite links page of group or channel [chatId] (`REQUIREMENTS.md` FR-P3): the primary link and the other links
 * the user made ([InviteLinkRepository.links], the first page of each, active and revoked), and what can be done to
 * them, one link and one request at a time: copying, sharing, the QR code, editing (another page), revoking and
 * deleting after a confirmation. TDLib has no updates for links, so both lists are read again after every change and
 * when the page shows again after the editor ([InviteLinksEvent.Refresh]).
 */
@Suppress("TooManyFunctions") // A function per action of the page.
@HiltViewModel
class InviteLinksViewModel internal constructor(
    private val chatId: Long,
    private val repository: InviteLinkRepository,
    private val userRepository: UserRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: InviteLinkRepository,
        userRepository: UserRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
    ) : this(
        savedStateHandle.toRoute<InviteLinksRoute>().chatId,
        repository,
        userRepository,
        clock,
        timePatterns,
        Locale::getDefault,
    )

    private val mutableUiState = MutableStateFlow<InviteLinksUiState>(InviteLinksUiState.Loading)
    val uiState: StateFlow<InviteLinksUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<InviteLinksEffect>(Channel.BUFFERED)
    val effects: Flow<InviteLinksEffect> = effectChannel.receiveAsFlow()

    /** The links as read, by link, for what an event names. */
    private var known: Map<String, InviteLink> = emptyMap()

    init {
        viewModelScope.launch(Dispatchers.Main) { load(showLoading = true) }
    }

    fun onEvent(event: InviteLinksEvent) {
        when (event) {
            InviteLinksEvent.Retry -> {
                if (mutableUiState.value is InviteLinksUiState.Error) reload(showLoading = true)
            }

            InviteLinksEvent.Refresh -> {
                if (mutableUiState.value is InviteLinksUiState.Content) {
                    reload(
                        showLoading = false,
                    )
                }
            }

            InviteLinksEvent.Create -> {
                effectChannel.trySend(InviteLinksEffect.OpenEditor(null))
            }

            is InviteLinksEvent.Copy -> {
                effectChannel.trySend(InviteLinksEffect.Copy(event.link))
            }

            is InviteLinksEvent.Share -> {
                effectChannel.trySend(InviteLinksEffect.Share(event.link))
            }

            is InviteLinksEvent.ShowQrCode -> {
                setDialog(InviteLinksDialog.QrCode(event.link))
            }

            is InviteLinksEvent.Edit -> {
                effectChannel.trySend(InviteLinksEffect.OpenEditor(event.link))
            }

            is InviteLinksEvent.Revoke -> {
                revoke(event.link)
            }

            is InviteLinksEvent.Delete -> {
                setDialog(InviteLinksDialog.Delete(event.link))
            }

            InviteLinksEvent.DeleteAllRevoked -> {
                setDialog(InviteLinksDialog.DeleteAllRevoked)
            }

            InviteLinksEvent.Confirm -> {
                confirm()
            }

            InviteLinksEvent.DismissDialog -> {
                setDialog(null)
            }
        }
    }

    private fun reload(showLoading: Boolean) {
        viewModelScope.launch { load(showLoading) }
    }

    private suspend fun load(showLoading: Boolean) {
        if (showLoading) mutableUiState.value = InviteLinksUiState.Loading
        val me = userRepository.myUserId.filterNotNull().first()
        val (active, revoked) =
            coroutineScope {
                val active = async { repository.links(chatId, me, revoked = false) }
                val revoked = async { repository.links(chatId, me, revoked = true) }
                active.await() to revoked.await()
            }
        if (active is Outcome.Failure) return failToLoad(active.error, showLoading)
        if (revoked is Outcome.Failure) return failToLoad(revoked.error, showLoading)
        val activeLinks = (active as Outcome.Success).value.links
        val revokedLinks = (revoked as Outcome.Success).value.links
        known = (activeLinks + revokedLinks).associateBy { it.link }
        val now = clock.now().epochSecond
        val current = mutableUiState.value as? InviteLinksUiState.Content
        mutableUiState.value =
            InviteLinksUiState.Content(
                primary = activeLinks.firstOrNull { it.isPrimary }?.let { item(it, now) },
                links = activeLinks.filterNot { it.isPrimary }.map { item(it, now) }.toImmutableList(),
                revoked = revokedLinks.map { item(it, now) }.toImmutableList(),
                dialog = current?.dialog,
                busy = current?.busy ?: persistentListOf(),
            )
    }

    private fun failToLoad(
        error: DomainError,
        showLoading: Boolean,
    ) {
        if (showLoading || mutableUiState.value !is InviteLinksUiState.Content) {
            mutableUiState.value = InviteLinksUiState.Error(error)
        } else {
            showError(error)
        }
    }

    private fun revoke(link: String) {
        val found = known[link] ?: return
        setDialog(InviteLinksDialog.Revoke(link, found.isPrimary))
    }

    private fun confirm() {
        val content = mutableUiState.value as? InviteLinksUiState.Content ?: return
        val dialog = content.dialog
        setDialog(null)
        when (dialog) {
            is InviteLinksDialog.Revoke -> {
                request(dialog.link, R.string.chat_info_link_revoked) { repository.revoke(chatId, dialog.link) }
            }

            is InviteLinksDialog.Delete -> {
                request(dialog.link, R.string.chat_info_link_deleted) { repository.deleteRevoked(chatId, dialog.link) }
            }

            InviteLinksDialog.DeleteAllRevoked -> {
                request(ALL_REVOKED, R.string.chat_info_links_deleted) {
                    repository.deleteAllRevoked(chatId, userRepository.myUserId.filterNotNull().first())
                }
            }

            is InviteLinksDialog.QrCode, null -> {}
        }
    }

    /** Sends one request for [link] (once at a time), then reads the links again. */
    private fun request(
        link: String,
        @StringRes done: Int,
        call: suspend () -> Outcome<Unit>,
    ) {
        val content = mutableUiState.value as? InviteLinksUiState.Content ?: return
        if (link in content.busy) return
        setBusy(link, true)
        viewModelScope.launch {
            val result = call()
            setBusy(link, false)
            when (result) {
                is Outcome.Success -> {
                    effectChannel.trySend(InviteLinksEffect.Done(UiText.resource(done)))
                    load(showLoading = false)
                }

                is Outcome.Failure -> {
                    showError(result.error)
                }
            }
        }
    }

    private fun setBusy(
        link: String,
        busy: Boolean,
    ) {
        mutableUiState.update { state ->
            val content = state as? InviteLinksUiState.Content ?: return@update state
            val links = if (busy) content.busy + link else content.busy - link
            content.copy(busy = links.toImmutableList())
        }
    }

    private fun setDialog(dialog: InviteLinksDialog?) {
        mutableUiState.update { (it as? InviteLinksUiState.Content)?.copy(dialog = dialog) ?: it }
    }

    private fun showError(error: DomainError) {
        if (error != DomainError.Silent) effectChannel.trySend(InviteLinksEffect.ShowError(error))
    }

    /** A link's row: its name or address, and how many joined, when it expires, or why it no longer works. */
    private fun item(
        link: InviteLink,
        now: Long,
    ): InviteLinkItem {
        val joined = UiText.plural(R.plurals.chat_info_link_joined, link.memberCount, link.memberCount)
        val state =
            when {
                link.isRevoked -> {
                    UiText.resource(R.string.chat_info_link_state_revoked)
                }

                link.expirationDate in 1..now -> {
                    UiText.resource(R.string.chat_info_link_state_expired)
                }

                link.memberLimit > 0 && link.memberCount >= link.memberLimit -> {
                    UiText.resource(R.string.chat_info_link_state_full)
                }

                link.expirationDate > 0 -> {
                    UiText.resource(R.string.chat_info_link_expires, date(link.expirationDate))
                }

                link.pendingJoinRequestCount > 0 -> {
                    UiText.plural(
                        R.plurals.chat_info_link_requests,
                        link.pendingJoinRequestCount,
                        link.pendingJoinRequestCount,
                    )
                }

                else -> {
                    null
                }
            }
        return InviteLinkItem(
            link = link.link,
            title = link.name.ifBlank { link.link.removePrefix("https://") },
            detail = state?.let { UiText.resource(R.string.chat_info_link_detail, joined, it) } ?: joined,
            isActive = link.isActiveAt(now),
            canEdit = !link.isPrimary && !link.isRevoked && !link.isSubscription,
        )
    }

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

    private companion object {
        /** What [request] marks busy while it deletes every revoked link. */
        const val ALL_REVOKED = "all-revoked"
    }
}

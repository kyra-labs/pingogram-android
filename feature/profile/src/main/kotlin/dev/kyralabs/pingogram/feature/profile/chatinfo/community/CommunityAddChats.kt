package dev.kyralabs.pingogram.feature.profile.chatinfo.community

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.CommunityAddResult
import dev.kyralabs.pingogram.core.domain.model.CommunityChat
import dev.kyralabs.pingogram.core.domain.repository.CommunityRepository
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The sheet of [CommunityViewModel] that adds the user's chats to the community, or suggests them to its
 * administrators (`docs/DESIGN_SYSTEM.md` §6 "Community page"): the chats `getSuitableCommunityChats` names that are
 * not in the community yet, read each time the sheet opens, named through [itemOf]. Whether it is open, the chats
 * picked and "Visible to admins only" are kept in the [SavedStateHandle]. [send] adds the picked chats one after
 * another; Telegram turns each into a request where the user may not change the list, and the first refusal stops it
 * with the chats not yet sent still picked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CommunityAddChats(
    private val communityId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val repository: CommunityRepository,
    private val scope: CoroutineScope,
    private val itemOf: (CommunityChat) -> Flow<CommunityChatItem?>,
    private val onEffect: (CommunityEffect) -> Unit,
) {
    private val open = MutableStateFlow(savedStateHandle.get<Boolean>(KEY_OPEN) == true)
    private val picked = MutableStateFlow(savedStateHandle.get<LongArray>(KEY_PICKED)?.toSet().orEmpty())
    private val hidden = MutableStateFlow(savedStateHandle.get<Boolean>(KEY_HIDDEN) == true)
    private val sending = MutableStateFlow(false)
    private val attempts = MutableStateFlow(0)

    private val suitable: Flow<Outcome<List<CommunityChatItem>>?> =
        combine(open, attempts) { isOpen, _ -> isOpen }.flatMapLatest { isOpen ->
            if (!isOpen) {
                flowOf(null)
            } else {
                flow {
                    emit(null)
                    when (val ids = repository.suitableChats()) {
                        is Outcome.Failure -> {
                            emit(ids)
                        }

                        is Outcome.Success -> {
                            val chats =
                                ids.value.map {
                                    itemOf(
                                        CommunityChat(it, canViewHistory = true, isHidden = false),
                                    )
                                }
                            if (chats.isEmpty()) {
                                emit(Outcome.Success(emptyList()))
                            } else {
                                combine(chats) { Outcome.Success(it.filterNotNull()) }.collect { emit(it) }
                            }
                        }
                    }
                }
            }
        }

    /** The sheet as it shows, without the chats already in the community ([inCommunity]); null while closed. */
    fun sheet(inCommunity: Flow<Set<Long>>): Flow<AddChatsSheet?> =
        combine(open, suitable, inCommunity, picked, hidden) { isOpen, suitable, members, picked, hidden ->
            if (!isOpen) return@combine null
            AddChatsSheet(
                chats =
                    (suitable as? Outcome.Success)
                        ?.value
                        ?.filter { it.chatId !in members }
                        ?.toImmutableList(),
                error = (suitable as? Outcome.Failure)?.error,
                picked = picked.toImmutableSet(),
                isHidden = hidden,
            )
        }.combine(sending) { sheet, isSending -> sheet?.copy(isSending = isSending) }

    fun open() {
        setPicked(emptySet())
        setHidden(false)
        setOpen(true)
    }

    fun close() {
        if (sending.value) return
        setOpen(false)
        setPicked(emptySet())
        savedStateHandle.remove<Boolean>(KEY_HIDDEN)
    }

    fun retry() {
        attempts.value++
    }

    fun toggle(chatId: Long) {
        if (sending.value) return
        setPicked(if (chatId in picked.value) picked.value - chatId else picked.value + chatId)
    }

    fun setHidden(isHidden: Boolean) {
        if (sending.value) return
        hidden.value = isHidden
        savedStateHandle[KEY_HIDDEN] = isHidden
    }

    /** Adds or suggests the picked chats, in the order they were picked. */
    fun send() {
        val chats = picked.value.toList()
        if (chats.isEmpty() || sending.value) return
        sending.value = true
        scope.launch {
            var added = 0
            var requested = 0
            var failure: DomainError? = null
            for (chatId in chats) {
                when (val outcome = repository.addChat(communityId, chatId, hidden.value)) {
                    is Outcome.Success -> {
                        if (outcome.value == CommunityAddResult.Added) added++ else requested++
                        setPicked(picked.value - chatId)
                    }

                    is Outcome.Failure -> {
                        failure = outcome.error
                        break
                    }
                }
            }
            sending.value = false
            if (failure == null) close()
            if (added > 0) onEffect(CommunityEffect.CountNotice(R.plurals.community_chats_added, added))
            if (requested > 0) onEffect(CommunityEffect.CountNotice(R.plurals.community_chats_suggested, requested))
            failure?.let { if (it != DomainError.Silent) onEffect(CommunityEffect.ShowError(it)) }
        }
    }

    private fun setOpen(isOpen: Boolean) {
        open.value = isOpen
        if (isOpen) savedStateHandle[KEY_OPEN] = true else savedStateHandle.remove<Boolean>(KEY_OPEN)
    }

    private fun setPicked(chats: Set<Long>) {
        picked.value = chats
        savedStateHandle[KEY_PICKED] = chats.toLongArray()
    }

    private companion object {
        const val KEY_OPEN = "community.addChats"
        const val KEY_PICKED = "community.addChats.picked"
        const val KEY_HIDDEN = "community.addChats.hidden"
    }
}

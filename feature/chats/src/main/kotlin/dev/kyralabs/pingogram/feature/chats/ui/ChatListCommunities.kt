package dev.kyralabs.pingogram.feature.chats.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.Community
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.repository.CommunityRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The part of [ChatListViewModel] that shows a community's chats as one entry in the main list (td_api.tl
 * `community.are_chats_collapsed`; the owner's board 19b, `docs/DESIGN_SYSTEM.md` §6 "Community entry").
 *
 * - **Which**: the communities the user joined ([CommunityRepository.joined]) that collapse their chats, each with its
 *   chats and its mute from its full info ([CommunityRepository.details]), read only for those and only while the
 *   screen collects. TDLib keeps no place for a community in the chat list, so [group] puts each entry where its
 *   newest chat would be.
 * - **Expanded**: which entries show their chats below them is the device's own, kept in the [SavedStateHandle].
 * - **Separately**: [showSeparately] turns the collapsing off for the account (`toggleCommunityAreChatsCollapsed`);
 *   the chats come back through TDLib's update of the community, and a failure is a snackbar.
 *
 * Null [repository] (the archive) shows no entries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatListCommunities(
    private val repository: CommunityRepository?,
    private val savedStateHandle: SavedStateHandle,
    private val scope: CoroutineScope,
    private val effects: SendChannel<ChatListEffect>,
) {
    private val expanded = savedStateHandle.getStateFlow(KEY_EXPANDED, LongArray(0))

    /** The collapsing communities and which of them are expanded. */
    val state: Flow<CommunityGroups> =
        if (repository == null) {
            flowOf(CommunityGroups())
        } else {
            combine(groups(repository), expanded) { groups, ids -> CommunityGroups(groups, ids.toSet()) }
                .distinctUntilChanged()
        }

    /** The communities of [list]: those that collapse their chats on the main list, none elsewhere. */
    fun of(list: ChatListType): Flow<CommunityGroups> =
        if (list ==
            ChatListType.Main
        ) {
            state
        } else {
            flowOf(CommunityGroups())
        }

    fun onEvent(event: ChatListEvent) {
        when (event) {
            is ChatListEvent.ToggleCommunity -> toggle(event.communityId)
            is ChatListEvent.ShowCommunityChatsSeparately -> showSeparately(event.communityId)
            else -> Unit
        }
    }

    private fun toggle(communityId: Long) {
        val ids = expanded.value.toSet()
        savedStateHandle[KEY_EXPANDED] =
            (if (communityId in ids) ids - communityId else ids + communityId).toLongArray()
    }

    private fun showSeparately(communityId: Long) {
        val repository = repository ?: return
        scope.launch {
            val result = repository.setCollapsed(communityId, collapsed = false)
            if (result is Outcome.Failure) effects.trySend(ChatListEffect.ShowError(result.error))
        }
    }

    private fun groups(repository: CommunityRepository): Flow<List<CommunityGroup>> =
        repository
            .joined()
            .map { joined ->
                (joined as? Outcome.Success)?.value.orEmpty().filter { it.hasAccess && it.areChatsCollapsed }
            }.distinctUntilChanged()
            .flatMapLatest { communities ->
                if (communities.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(communities.map { groupOf(repository, it) }) { it.filterNotNull() }
                }
            }

    private fun groupOf(
        repository: CommunityRepository,
        community: Community,
    ): Flow<CommunityGroup?> =
        repository.details(community.id).map { details ->
            (details as? Outcome.Success)?.value?.let {
                CommunityGroup(
                    id = community.id,
                    name = community.name,
                    photo = community.photo,
                    chatIds = it.chats.mapTo(HashSet()) { chat -> chat.chatId },
                    isMuted = it.isMuted,
                )
            }
        }

    private companion object {
        const val KEY_EXPANDED = "chatList.communities.expanded"
    }
}

/** The communities that collapse their chats ([groups]) and those whose entry shows them ([expanded]). */
internal data class CommunityGroups(
    val groups: List<CommunityGroup> = emptyList(),
    val expanded: Set<Long> = emptySet(),
)

/** A community whose chats show as one entry: its [chatIds] and whether it is muted. */
internal data class CommunityGroup(
    val id: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val chatIds: Set<Long>,
    val isMuted: Boolean,
)

/**
 * [items] without the chats of [communities], and their entries, each before the row its newest chat would have
 * preceded. Pinned chats (the first [pinnedCount]) keep their places: the user pinned them on their own. A chat in
 * two communities goes to the first. A community without a chat among [items] has no entry.
 */
internal fun groupCommunities(
    items: ImmutableList<ChatListItemModel>,
    pinnedCount: Int,
    communities: CommunityGroups,
): Pair<ImmutableList<ChatListItemModel>, ImmutableList<CommunityEntry>> {
    if (communities.groups.isEmpty()) return items to persistentListOf()
    val communityOf = HashMap<Long, CommunityGroup>()
    communities.groups.forEach { group -> group.chatIds.forEach { communityOf.putIfAbsent(it, group) } }
    val rows = ArrayList<ChatListItemModel>(items.size)
    val members = LinkedHashMap<CommunityGroup, MutableList<ChatListItemModel>>()
    val places = HashMap<Long, Int>()
    items.forEachIndexed { index, item ->
        val group = if (index < pinnedCount) null else communityOf[item.id]
        if (group == null) {
            rows += item
        } else {
            members.getOrPut(group) { mutableListOf<ChatListItemModel>().also { places[group.id] = rows.size } } += item
        }
    }
    val entries =
        members.map { (group, chats) ->
            CommunityEntry(
                id = group.id,
                name = group.name,
                photo = group.photo,
                chats = chats.toImmutableList(),
                unreadCount = chats.sumOf { it.unreadCount },
                isMuted = group.isMuted,
                isExpanded = group.id in communities.expanded,
                before = places.getValue(group.id),
            )
        }
    return if (entries.isEmpty()) items to persistentListOf() else rows.toImmutableList() to entries.toImmutableList()
}

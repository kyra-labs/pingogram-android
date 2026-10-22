package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AddContactResult
import dev.kyralabs.pingogram.core.domain.model.CreatedGroup
import dev.kyralabs.pingogram.core.domain.model.EditableContact
import dev.kyralabs.pingogram.core.domain.model.MessageAutoDelete
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.repository.ChatCreationRepository
import dev.kyralabs.pingogram.core.domain.repository.ContactRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

// Fakes of the repositories that creating a group or a channel reads, for the view model tests.

/**
 * The contacts as [list] holds them (null: still listing): a failure ends the flow, as the repository's does.
 * [searches] maps a query to what TDLib's search finds.
 */
class FakeContactRepository : ContactRepository {
    val list = MutableStateFlow<Outcome<ImmutableList<User>>?>(null)
    var listings = 0
        private set
    val searches = mutableMapOf<String, List<Long>>()
    val searched = mutableListOf<String>()

    fun setContacts(vararg users: User) {
        list.value = Outcome.Success(users.toList().toImmutableList())
    }

    override fun contacts(): Flow<Outcome<ImmutableList<User>>> =
        flow {
            listings++
            list
                .filterNotNull()
                .transformWhile { outcome ->
                    emit(outcome)
                    outcome is Outcome.Success
                }.collect { emit(it) }
        }

    override suspend fun search(query: String): Outcome<ImmutableList<Long>> {
        searched += query
        return Outcome.Success(searches[query].orEmpty().toImmutableList())
    }

    override suspend fun addByPhoneNumber(
        phoneNumber: String,
        firstName: String,
        lastName: String,
    ): Outcome<AddContactResult> = unused()

    override suspend fun addByUsername(
        username: String,
        firstName: String,
        lastName: String,
    ): Outcome<Long> = unused()

    override suspend fun editableContact(userId: Long): Outcome<EditableContact> = unused()

    override suspend fun save(
        userId: Long,
        firstName: String,
        lastName: String,
        sharePhoneNumber: Boolean,
    ): Outcome<Unit> = unused()

    override suspend fun delete(userId: Long): Outcome<Unit> = unused()

    override suspend fun privateChatId(userId: Long): Outcome<Long> = unused()

    override suspend fun inviteLink(): String = unused()
}

/** What [FakeChatCreationRepository] was asked to create. */
data class GroupRequest(
    val title: String,
    val userIds: List<Long>,
    val autoDelete: MessageAutoDelete,
    val photoPath: String?,
)

data class ChannelRequest(
    val title: String,
    val description: String,
    val photoPath: String?,
)

/** Answers each call with what its handler gives, and records every call. */
class FakeChatCreationRepository : ChatCreationRepository {
    var onPreparePhoto: suspend (String) -> Outcome<String> = { uri -> Outcome.Success("/cache/uploads/$uri.jpg") }
    var onCreateGroup: suspend (GroupRequest) -> Outcome<CreatedGroup> = {
        Outcome.Success(CreatedGroup(GROUP_ID, persistentListOf()))
    }
    var onCreateChannel: suspend (ChannelRequest) -> Outcome<Long> = { Outcome.Success(CHANNEL_ID) }
    var onCheckUsername: suspend (String) -> Outcome<UsernameCheck> = { Outcome.Success(UsernameCheck.Available) }
    var onSetUsername: suspend (String) -> Outcome<Unit> = { Outcome.Success(Unit) }
    var onInviteLink: suspend (Long) -> Outcome<String> = { Outcome.Success(LINK) }
    var onAddMembers: suspend (List<Long>) -> Outcome<ImmutableList<Long>> = { Outcome.Success(persistentListOf()) }

    val discarded = mutableListOf<String>()
    val groups = mutableListOf<GroupRequest>()
    val channels = mutableListOf<ChannelRequest>()
    val checkedUsernames = mutableListOf<Pair<Long, String>>()
    val setUsernames = mutableListOf<Pair<Long, String>>()
    val inviteLinks = mutableListOf<Long>()
    val added = mutableListOf<Pair<Long, List<Long>>>()

    override suspend fun preparePhoto(uri: String): Outcome<String> = onPreparePhoto(uri)

    override fun discardPhoto(path: String) {
        discarded += path
    }

    override suspend fun createGroup(
        title: String,
        userIds: List<Long>,
        autoDelete: MessageAutoDelete,
        photoPath: String?,
    ): Outcome<CreatedGroup> {
        val request = GroupRequest(title, userIds, autoDelete, photoPath)
        groups += request
        return onCreateGroup(request)
    }

    override suspend fun createChannel(
        title: String,
        description: String,
        photoPath: String?,
    ): Outcome<Long> {
        val request = ChannelRequest(title, description, photoPath)
        channels += request
        return onCreateChannel(request)
    }

    override suspend fun checkUsername(
        chatId: Long,
        username: String,
    ): Outcome<UsernameCheck> {
        checkedUsernames += chatId to username
        return onCheckUsername(username)
    }

    override suspend fun setUsername(
        chatId: Long,
        username: String,
    ): Outcome<Unit> {
        setUsernames += chatId to username
        return onSetUsername(username)
    }

    override suspend fun inviteLink(chatId: Long): Outcome<String> {
        inviteLinks += chatId
        return onInviteLink(chatId)
    }

    override suspend fun addMembers(
        chatId: Long,
        userIds: List<Long>,
    ): Outcome<ImmutableList<Long>> {
        added += chatId to userIds
        return onAddMembers(userIds)
    }

    companion object {
        const val GROUP_ID = -5L
        const val CHANNEL_ID = -100L
        const val LINK = "https://t.me/+AbCdEf"
    }
}

/** Clears [viewModel] as Android does when its owner goes away, which calls its `onCleared`. */
fun clear(viewModel: ViewModel) {
    val store = ViewModelStore()
    val factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") // It is asked for the view model's own class.
            override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
        }
    ViewModelProvider(store, factory)[viewModel.javaClass]
    store.clear()
}

/** A failure for each kind of error the view models tell apart, as TDLib's are mapped. */
val COMMON_ERRORS = listOf(DomainError.Network, DomainError.Unauthorized)

private fun unused(): Nothing = throw UnsupportedOperationException("Not used by creating chats")

package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

// What creating a group and creating a channel share: the photo, the FLOOD_WAIT countdown and the dialog about the
// people Telegram did not add.

/**
 * The photo picked for a new chat ([state]), a part of the flow's view model running in its [scope]: a picked image
 * is copied by [repository] ([PickedPhotoRepository.preparePhoto]), its path kept in the [savedStateHandle] under
 * [key], so it survives process death. Another pick or [remove] deletes the copy before; [release] deletes it when
 * the flow ends without a chat having taken it ([markUsed]).
 */
internal class NewChatPhoto(
    private val scope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle,
    private val repository: PickedPhotoRepository,
    private val key: String,
) {
    private val mutableState =
        MutableStateFlow(savedStateHandle.get<String>(key)?.let { ChatPhoto.Ready(it) } ?: ChatPhoto.None)
    val state: StateFlow<ChatPhoto> = mutableState.asStateFlow()

    /** The copy's path, once there is one. */
    val path: String? get() = (state.value as? ChatPhoto.Ready)?.path

    private var job: Job? = null
    private var used = false

    /** Copies the image at [uri]; [onError] hears why it cannot be a photo. */
    fun pick(
        uri: String,
        onError: (DomainError) -> Unit,
    ) {
        if (used) return
        job?.cancel()
        discard()
        mutableState.value = ChatPhoto.Preparing
        job =
            scope.launch {
                when (val prepared = repository.preparePhoto(uri)) {
                    is Outcome.Success -> {
                        savedStateHandle[key] = prepared.value
                        mutableState.value = ChatPhoto.Ready(prepared.value)
                    }

                    is Outcome.Failure -> {
                        mutableState.value = ChatPhoto.None
                        onError(prepared.error)
                    }
                }
            }
    }

    fun remove() {
        if (used) return
        job?.cancel()
        discard()
        mutableState.value = ChatPhoto.None
    }

    /** A chat took the copy, which the repository deletes once uploaded. */
    fun markUsed() {
        used = true
        savedStateHandle[key] = null
    }

    /** The flow ends: the copy goes unless a chat took it. */
    fun release() {
        if (!used) discard()
    }

    private fun discard() {
        path?.let(repository::discardPhoto)
        savedStateHandle[key] = null
    }
}

/**
 * Counts [seconds] down to nothing, one second at a time, handing each step to [update] as a [FloodWait], then null.
 */
internal fun CoroutineScope.countDown(
    seconds: Long,
    update: (FloodWait?) -> Unit,
): Job =
    launch {
        for (left in seconds downTo 1) {
            update(FloodWait(left, seconds))
            delay(1.seconds)
        }
        update(null)
    }

/**
 * The dialog about [userIds], whom Telegram did not add to chat [chatId], named as [userRepository] knows them; null
 * when everyone was added.
 */
internal suspend fun notAddedDialog(
    chatId: Long,
    userIds: List<Long>,
    userRepository: UserRepository,
): NotAddedDialog? {
    if (userIds.isEmpty()) return null
    val users = userRepository.users.first()
    val names =
        userIds.mapNotNull { id ->
            users[id]?.let { user ->
                listOf(user.firstName, user.lastName).filter(String::isNotBlank).joinToString(" ")
            }
        }
    return NotAddedDialog(chatId, userIds.size, names.filter(String::isNotBlank).toImmutableList())
}

/** At most [max] Unicode characters (code points) of this text, as TDLib counts a title. */
internal fun String.limitedToCodePoints(max: Int): String =
    if (codePointCount(0, length) <= max) this else substring(0, offsetByCodePoints(0, max))

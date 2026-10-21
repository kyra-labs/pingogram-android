package dev.kyralabs.pingogram.feature.media.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onSuccess
import dev.kyralabs.pingogram.core.domain.model.MessageProperties
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Delete of the viewer's current item (`REQUIREMENTS.md` FR-C6 "delete (for me / for all)", FR-C10), for
 * [MediaViewerViewModel]: whether the toolbar offers it ([state]), its confirmation, and the deletion; and from the
 * same answer of TDLib whether it offers Forward ([canForward], [allowsForward]). Main thread only.
 *
 * - **Allowed** where TDLib says the message can be deleted (`getMessageProperties`' `can_be_deleted_only_for_self`
 *   and `can_be_deleted_for_all_users`, [properties]), asked once per item for the viewer's life; until it answers,
 *   and if it fails, Delete does not show. Forward shows where TDLib says the message can be forwarded
 *   (`can_be_forwarded`, never for protected content), on the same terms.
 * - **Confirmation**: Delete ([request]) shows it with what TDLib allows ([DeleteScope]); it goes when answered
 *   ([confirm], [dismiss]) or when the page turns.
 * - **Deleting** ([delete]) is one request; TDLib then reports the deletion, which drops the item from the viewer. A
 *   failure is a snackbar ([onFailure]); once it succeeds, [onDeleted] decides whether the viewer closes.
 */
internal class MediaDeletion(
    private val properties: suspend (messageId: Long) -> Outcome<MessageProperties>,
    private val delete: suspend (messageId: Long, forEveryone: Boolean) -> Outcome<Unit>,
    private val scope: CoroutineScope,
    private val onFailure: (DomainError) -> Unit,
    private val onDeleted: (messageId: Long) -> Unit,
) {
    /** The current item's message id, as [focus] last set it. */
    private var currentId: Long? = null

    /** TDLib's answers, by message id; the items asked about. */
    private val answers = MutableStateFlow<PersistentMap<Long, MessageProperties>>(persistentMapOf())
    private val asking = mutableSetOf<Long>()

    private val confirmation = MutableStateFlow<DeleteConfirmation?>(null)

    /** Whether Delete shows for the item of message [currentId] (null while there is none), and its confirmation. */
    fun state(currentId: Flow<Long?>): Flow<Deletion> =
        combine(answers, confirmation, currentId) { answers, confirmation, id ->
            Deletion(
                canDelete = id != null && answers[id]?.deleteScope != null,
                confirmation = confirmation?.takeIf { it.messageId == id },
            )
        }.distinctUntilChanged()

    /** Whether Forward shows for the item of message [currentId]: TDLib said it can be forwarded. */
    fun canForward(currentId: Flow<Long?>): Flow<Boolean> =
        combine(answers, currentId) { answers, id -> id != null && answers[id]?.canBeForwarded == true }
            .distinctUntilChanged()

    /** Whether TDLib said message [messageId] can be forwarded, which Forward needs. */
    fun allowsForward(messageId: Long): Boolean = answers.value[messageId]?.canBeForwarded == true

    /**
     * The current item is message [messageId] now (null while there is none): TDLib is asked whether it can be
     * deleted, and the confirmation of another item goes.
     */
    fun focus(messageId: Long?) {
        if (messageId != currentId) confirmation.update { it?.takeIf { shown -> shown.messageId == messageId } }
        currentId = messageId
        if (messageId != null) ask(messageId)
    }

    /** Delete was tapped: the confirmation shows, where the current item can be deleted. */
    fun request() {
        val messageId = currentId ?: return
        val scope = answers.value[messageId]?.deleteScope ?: return
        confirmation.value = DeleteConfirmation(messageId, scope)
    }

    /** The confirmation was answered with Delete: for everyone where only that is possible, or the user chose it. */
    fun confirm(forEveryone: Boolean) {
        val shown = confirmation.value ?: return
        confirmation.value = null
        val forAll =
            when (shown.scope) {
                DeleteScope.ForMe -> false
                DeleteScope.ForEveryone -> true
                DeleteScope.Choice -> forEveryone
            }
        scope.launch {
            when (val result = delete(shown.messageId, forAll)) {
                is Outcome.Success -> onDeleted(shown.messageId)
                is Outcome.Failure -> onFailure(result.error)
            }
        }
    }

    /** The confirmation was cancelled. */
    fun dismiss() {
        confirmation.value = null
    }

    private fun ask(messageId: Long) {
        if (messageId in answers.value || !asking.add(messageId)) return
        scope.launch {
            properties(messageId).onSuccess { properties ->
                answers.update { it.putting(messageId, properties) }
            }
            asking.remove(messageId)
        }
    }

    /** Whether Delete shows, and the confirmation of the current item while it shows. */
    data class Deletion(
        val canDelete: Boolean = false,
        val confirmation: DeleteConfirmation? = null,
    )
}

/** For whom the message can be deleted; null if for nobody. */
private val MessageProperties.deleteScope: DeleteScope?
    get() =
        when {
            canBeDeletedOnlyForSelf && canBeDeletedForAllUsers -> DeleteScope.Choice
            canBeDeletedForAllUsers -> DeleteScope.ForEveryone
            canBeDeletedOnlyForSelf -> DeleteScope.ForMe
            else -> null
        }

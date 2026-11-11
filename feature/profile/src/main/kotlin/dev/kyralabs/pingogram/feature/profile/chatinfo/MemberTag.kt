package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MemberTagLimits
import dev.kyralabs.pingogram.core.domain.repository.GroupAdministrationRepository
import dev.kyralabs.pingogram.feature.profile.ui.limitedToCodePoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The "Set tag" sheet of a member (`docs/DESIGN_SYSTEM.md` §6 "Member tags", the owner's boards 18a and 18b), while it
 * is open.
 *
 * @property member the member's row, for their avatar and name.
 * @property isAdministrator the member is the owner or an administrator, whose tags show in the accent color.
 * @property isOwn the member is the current user, setting their own tag.
 * @property text the tag as typed, at most [MemberTagLimits.LENGTH_MAX] characters.
 * @property saved the tag the member has: "Save" stays disabled while [text] is the same, and "Remove tag" shows only
 *   while it is not empty.
 * @property isSaving Telegram has the new tag and has not answered yet.
 * @property error why the last change failed, shown under the field; null without one.
 */
@Immutable
data class MemberTagEditor(
    val member: MemberItem,
    val isAdministrator: Boolean,
    val isOwn: Boolean,
    val text: String,
    val saved: String,
    val isSaving: Boolean = false,
    val error: DomainError? = null,
) {
    /** Whether "Save" may be tapped: the tag changed, and nothing is on its way. */
    val canSave: Boolean get() = !isSaving && text.trim() != saved

    /** Whether "Remove tag" shows: the member has a tag. */
    val canRemove: Boolean get() = saved.isNotEmpty()

    /** How many characters the tag has, as its counter shows them. */
    val length: Int get() = text.codePointCount(0, text.length)

    override fun toString() = "MemberTagEditor(member=$member, isSaving=$isSaving, error=$error, …)"
}

/**
 * What [MemberTagging] keeps of the open sheet: the member it is for, what is typed, the tag they have, and the state
 * of a change on its way.
 */
internal data class TagDraft(
    val userId: Long,
    val text: String,
    val saved: String,
    val isSaving: Boolean = false,
    val error: DomainError? = null,
) {
    override fun toString() = "TagDraft(userId=$userId, isSaving=$isSaving, error=$error, …)"
}

/**
 * Setting members' tags (`setChatMemberTag`, through [GroupAdministrationRepository.setMemberTag]) in group [chatId]
 * for [ChatMembersViewModel]: the [draft] of the open sheet.
 *
 * - **Opening** ([open]) starts from the tag the member has; **typing** ([edit]) keeps at most
 *   [MemberTagLimits.LENGTH_MAX] characters. Who the sheet is for and what is typed are kept in the
 *   [savedStateHandle], so they survive process death ([restore] reopens it once the member is loaded again).
 * - **Saving** ([save]) sends the tag once per tap, only where it changed; **removing** ([remove]) sends an empty one.
 *   Once Telegram took it, [onChanged] gets the member and their new tag (the page shows it in the row, with a
 *   snackbar) and the sheet closes; a failure stays in the sheet as its error, the text kept.
 * - **Closing** ([dismiss]) forgets what was typed; a change already sent is not taken back.
 *
 * Runs in [scope], the view model's.
 */
internal class MemberTagging(
    private val repository: GroupAdministrationRepository,
    private val chatId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val scope: CoroutineScope,
    private val onChanged: (userId: Long, tag: String) -> Unit,
) {
    private val mutableDraft = MutableStateFlow<TagDraft?>(null)

    /** The open sheet; null while none is. */
    val draft: StateFlow<TagDraft?> = mutableDraft.asStateFlow()

    private var saveJob: Job? = null

    /** Opens the sheet of member [userId], on the tag [saved] they have. */
    fun open(
        userId: Long,
        saved: String,
    ) {
        if (mutableDraft.value != null) return
        savedStateHandle[KEY_USER] = userId
        savedStateHandle[KEY_TEXT] = saved
        mutableDraft.value = TagDraft(userId, saved, saved)
    }

    /** The member whose sheet was open before process death, waiting for [restore]; null if none was. */
    fun savedUserId(): Long? = savedStateHandle.get<Long>(KEY_USER)

    /** Reopens the sheet that was open before process death, with what was typed, once the member is loaded again. */
    fun restore(saved: String) {
        if (mutableDraft.value != null) return
        val userId = savedUserId() ?: return
        val typed = savedStateHandle.get<String>(KEY_TEXT) ?: saved
        mutableDraft.value = TagDraft(userId, typed, saved)
    }

    fun edit(value: String) {
        val draft = mutableDraft.value ?: return
        if (draft.isSaving) return
        val text = value.limitedToCodePoints(MemberTagLimits.LENGTH_MAX)
        savedStateHandle[KEY_TEXT] = text
        mutableDraft.value = draft.copy(text = text, error = null)
    }

    fun save() {
        val draft = mutableDraft.value ?: return
        if (draft.isSaving || draft.text.trim() == draft.saved) return
        send(draft, draft.text.trim())
    }

    fun remove() {
        val draft = mutableDraft.value ?: return
        if (draft.isSaving || draft.saved.isEmpty()) return
        send(draft, "")
    }

    fun dismiss() {
        saveJob?.cancel()
        close()
    }

    private fun send(
        draft: TagDraft,
        tag: String,
    ) {
        mutableDraft.value = draft.copy(isSaving = true, error = null)
        saveJob =
            scope.launch {
                when (val sent = repository.setMemberTag(chatId, draft.userId, tag)) {
                    is Outcome.Success -> {
                        close()
                        onChanged(draft.userId, tag)
                    }

                    is Outcome.Failure -> {
                        mutableDraft.update { it?.copy(isSaving = false, error = sent.error) }
                    }
                }
            }
    }

    private fun close() {
        savedStateHandle.remove<Long>(KEY_USER)
        savedStateHandle.remove<String>(KEY_TEXT)
        mutableDraft.value = null
    }

    private companion object {
        const val KEY_USER = "chatMembers.tagUser"
        const val KEY_TEXT = "chatMembers.tagText"
    }
}

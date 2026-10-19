package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

/**
 * What the form of a new poll holds (`REQUIREMENTS.md` FR-C5): the question, 2–10 answer options, and how people
 * vote. Like the composer's text, its owner, the conversation's view model, keeps it across rotations, and the text
 * fields edit its [TextFieldState]s directly; everything is snapshot state, so the form and a `snapshotFlow` that saves
 * it ([draft]) follow every change. Main thread only.
 *
 * Anonymous voting is on by default, as in Telegram. A quiz has exactly one correct option ([correctOption]) and one
 * answer per voter, so multiple answers do not apply to it.
 */
@Stable
class PollEditor(
    initial: PollDraft = PollDraft(),
) {
    val question = TextFieldState(initial.question)

    private var nextKey = 0L

    private val fields = mutableStateListOf<PollOptionField>()

    /** The answer options, in their order; at least [NewPoll.MIN_OPTIONS], at most [NewPoll.MAX_OPTIONS]. */
    val options: List<PollOptionField> get() = fields

    var isAnonymous by mutableStateOf(initial.isAnonymous)

    var allowsMultipleAnswers by mutableStateOf(initial.allowsMultipleAnswers)

    var isQuiz by mutableStateOf(initial.isQuiz)

    /** The key of the quiz's correct option; null while none is chosen. */
    var correctOption by mutableStateOf<Long?>(null)

    init {
        fill(initial)
    }

    val canAddOption: Boolean get() = fields.size < NewPoll.MAX_OPTIONS

    val canRemoveOption: Boolean get() = fields.size > NewPoll.MIN_OPTIONS

    /** Adds an empty option at the end, if there is room, and returns its key; null otherwise. */
    fun addOption(): Long? {
        if (!canAddOption) return null
        return newField("").also { fields += it }.key
    }

    /** Removes option [key], while more than the minimum are left. */
    fun removeOption(key: Long) {
        if (!canRemoveOption) return
        fields.removeAll { it.key == key }
        if (correctOption == key) correctOption = null
    }

    /** Whether option [key] repeats an option above it, which Telegram refuses. */
    fun isDuplicate(key: Long): Boolean {
        val index = fields.indexOfFirst { it.key == key }
        val text = fields.getOrNull(index)?.normalized ?: return false
        return text.isNotEmpty() && fields.take(index).any { it.normalized == text }
    }

    /**
     * The poll to send; null while it is not complete: a question, at least two filled-in options, none repeated, and
     * for a quiz a filled-in correct option. Empty options are left out; [forceAnonymous] (channels) overrides
     * [isAnonymous].
     */
    fun poll(forceAnonymous: Boolean = false): NewPoll? {
        val question =
            question.text
                .toString()
                .trim()
                .take(NewPoll.MAX_QUESTION_LENGTH)
        val filled = fields.filter { it.normalized.isNotEmpty() }
        val texts =
            filled.map {
                it.text.text
                    .toString()
                    .trim()
                    .take(NewPoll.MAX_OPTION_LENGTH)
            }
        val correctIndex = filled.indexOfFirst { it.key == correctOption }.takeIf { it >= 0 }
        val complete =
            question.isNotEmpty() &&
                texts.size >= NewPoll.MIN_OPTIONS &&
                fields.none { isDuplicate(it.key) } &&
                (!isQuiz || correctIndex != null)
        if (!complete) return null
        return NewPoll(
            question = question,
            options = texts.take(NewPoll.MAX_OPTIONS).toImmutableList(),
            isAnonymous = forceAnonymous || isAnonymous,
            allowsMultipleAnswers = !isQuiz && allowsMultipleAnswers,
            correctOptionIndex = if (isQuiz) correctIndex else null,
        )
    }

    /** What the form holds now, for process death. */
    val draft: PollDraft
        get() =
            PollDraft(
                question = question.text.toString(),
                options = fields.map { it.text.text.toString() },
                isAnonymous = isAnonymous,
                allowsMultipleAnswers = allowsMultipleAnswers,
                isQuiz = isQuiz,
                correctOptionIndex = fields.indexOfFirst { it.key == correctOption }.takeIf { it >= 0 },
            )

    /** Empties the form for the next poll. */
    fun clear() {
        question.clearText()
        fill(PollDraft())
    }

    private fun fill(draft: PollDraft) {
        fields.clear()
        val texts = draft.options.take(NewPoll.MAX_OPTIONS)
        fields += texts.map(::newField)
        while (fields.size < NewPoll.MIN_OPTIONS) fields += newField("")
        isAnonymous = draft.isAnonymous
        allowsMultipleAnswers = draft.allowsMultipleAnswers
        isQuiz = draft.isQuiz
        correctOption = draft.correctOptionIndex?.let { fields.getOrNull(it)?.key }
    }

    private fun newField(text: String) = PollOptionField(nextKey++, TextFieldState(text))
}

/** One answer option of the [PollEditor]: its text field, by a [key] that stays while the form lives. */
@Stable
class PollOptionField(
    val key: Long,
    val text: TextFieldState,
) {
    /** The text as options are compared: trimmed, case ignored. */
    internal val normalized: String get() =
        text.text
            .toString()
            .trim()
            .lowercase(Locale.ROOT)
}

/** What a [PollEditor] holds, as plain values: for process death, and for previews and tests. */
data class PollDraft(
    val question: String = "",
    val options: List<String> = emptyList(),
    val isAnonymous: Boolean = true,
    val allowsMultipleAnswers: Boolean = false,
    val isQuiz: Boolean = false,
    val correctOptionIndex: Int? = null,
) {
    override fun toString() = "PollDraft(options=${options.size}, isQuiz=$isQuiz, …)"
}

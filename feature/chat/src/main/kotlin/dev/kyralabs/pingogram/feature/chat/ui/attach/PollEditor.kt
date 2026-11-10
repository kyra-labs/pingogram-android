package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.NewPollOption
import dev.kyralabs.pingogram.core.domain.model.PollLimits
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

/**
 * What the form of a new poll holds (`REQUIREMENTS.md` FR-C5; `docs/DESIGN_SYSTEM.md` §6 "New poll"): the question, a
 * photo and a description, from two answer options up to Telegram's limit ([limits]), each with an optional link, and
 * how people vote and when it closes. Like the composer's text, its owner, the conversation's view model, keeps it
 * across rotations, and the text fields edit its [TextFieldState]s directly; everything is snapshot state, so the form
 * and a `snapshotFlow` that saves it ([draft]) follow every change. Main thread only.
 *
 * Anonymous voting and changing the vote are on by default, as in Telegram. A quiz has one or more correct options
 * ([correctOptions]) and as many answers per voter, and its votes are final, so multiple answers, changing the vote and
 * adding answers do not apply to it.
 */
@Stable
class PollEditor(
    initial: PollDraft = PollDraft(),
) {
    val question = TextFieldState(initial.question)

    val description = TextFieldState(initial.description)

    /** What a quiz shows once answered; empty for none. */
    val explanation = TextFieldState(initial.explanation)

    private var nextKey = 0L

    private val fields = mutableStateListOf<PollOptionField>()

    /** The answer options, in their order; at least [NewPoll.MIN_OPTIONS], at most [PollLimits.maxOptions]. */
    val options: List<PollOptionField> get() = fields

    /** What Telegram allows; [PollLimits]' defaults until the view model read them. */
    var limits by mutableStateOf(PollLimits())

    /** The poll's photo, prepared; null for none. */
    var photo by mutableStateOf(initial.photo)

    /** A picked photo is being prepared, to take [photo]'s place. */
    var isPreparingPhoto by mutableStateOf(false)

    var isAnonymous by mutableStateOf(initial.isAnonymous)

    var allowsMultipleAnswers by mutableStateOf(initial.allowsMultipleAnswers)

    var allowsRevoting by mutableStateOf(initial.allowsRevoting)

    var hidesResults by mutableStateOf(initial.hidesResults)

    var shufflesOptions by mutableStateOf(initial.shufflesOptions)

    var allowsAddingOptions by mutableStateOf(initial.allowsAddingOptions)

    var isQuiz by mutableStateOf(initial.isQuiz)

    /** When the poll closes by itself. */
    var closing by mutableStateOf(initial.closing)

    /** The moment of [PollClosing.Custom] (Unix time); null until one was chosen. */
    var customCloseDate by mutableStateOf(initial.customCloseDate)

    /**
     * What a bot's keyboard button that asks for this poll requires (`KeyboardButtonType.RequestPoll`): a quiz
     * (true), a regular poll (false), or either, the user's choice (null). [isQuiz] cannot change while one is
     * required.
     */
    var requiredQuiz by mutableStateOf(initial.requiredQuiz)
        private set

    private val correctKeys = mutableStateListOf<Long>()

    /** The keys of the quiz's correct options. */
    val correctOptions: List<Long> get() = correctKeys

    init {
        fill(initial)
    }

    /** Requires a quiz ([quiz] true), a regular poll (false) or neither (null), and sets [isQuiz] to match. */
    fun require(quiz: Boolean?) {
        requiredQuiz = quiz
        if (quiz != null) isQuiz = quiz
    }

    val canAddOption: Boolean get() = fields.size < limits.maxOptions

    val canRemoveOption: Boolean get() = fields.size > NewPoll.MIN_OPTIONS

    /** Adds an empty option at the end, if there is room, and returns its key; null otherwise. */
    fun addOption(): Long? {
        if (!canAddOption) return null
        return newField("", null).also { fields += it }.key
    }

    /** Removes option [key], while more than the minimum are left. */
    fun removeOption(key: Long) {
        if (!canRemoveOption) return
        fields.removeAll { it.key == key }
        correctKeys.remove(key)
    }

    /** Moves the option at [from] to [to] (indexes in [options]); nothing for an index out of range. */
    fun moveOption(
        from: Int,
        to: Int,
    ) {
        if (from == to || from !in fields.indices || to !in fields.indices) return
        fields.add(to, fields.removeAt(from))
    }

    /** Marks option [key] as a correct answer of the quiz, or not ([correct]). */
    fun setCorrect(
        key: Long,
        correct: Boolean,
    ) {
        if (correct && key !in correctKeys) correctKeys += key
        if (!correct) correctKeys.remove(key)
    }

    /** Whether option [key] repeats an option above it, which Telegram refuses. */
    fun isDuplicate(key: Long): Boolean {
        val index = fields.indexOfFirst { it.key == key }
        val text = fields.getOrNull(index)?.normalized ?: return false
        return text.isNotEmpty() && fields.take(index).any { it.normalized == text }
    }

    /** Whether the poll may let members add answers: a public regular poll outside channels. */
    fun canAllowAddingOptions(inChannel: Boolean): Boolean = !isQuiz && !isAnonymous && !inChannel

    /** Whether the poll may close [period] seconds after it is sent, within Telegram's limit. */
    fun canClose(period: PollClosing): Boolean =
        when (period) {
            PollClosing.Never -> true
            PollClosing.Custom -> limits.maxOpenPeriod > 0
            else -> period.seconds in 1..limits.maxOpenPeriod
        }

    /**
     * The poll to send at [now] (Unix time); null while it is not complete: a question, at least two filled-in
     * options, none repeated, for a quiz at least one filled-in correct option, and a chosen moment in the future for a
     * custom closing time. Empty options are left out; [forceAnonymous] (channels) overrides [isAnonymous].
     */
    fun poll(
        forceAnonymous: Boolean = false,
        now: Long = 0,
    ): NewPoll? {
        val question =
            question.text
                .toString()
                .trim()
                .take(NewPoll.MAX_QUESTION_LENGTH)
        val filled = fields.filter { it.normalized.isNotEmpty() }
        val correct = filled.indices.filter { filled[it].key in correctKeys }
        val closes = closesAt(now)
        val complete =
            question.isNotEmpty() &&
                filled.size >= NewPoll.MIN_OPTIONS &&
                fields.none { isDuplicate(it.key) } &&
                (!isQuiz || correct.isNotEmpty()) &&
                closes != null
        if (!complete) return null
        val anonymous = forceAnonymous || isAnonymous
        return NewPoll(
            question = question,
            options = filled.take(limits.maxOptions).map { it.toNewOption() }.toImmutableList(),
            isAnonymous = anonymous,
            allowsMultipleAnswers = !isQuiz && allowsMultipleAnswers,
            correctOptionIndexes = (if (isQuiz) correct else emptyList()).toImmutableList(),
            description = description.text.toString().trim(),
            photo = photo,
            explanation =
                if (isQuiz) {
                    explanation.text
                        .toString()
                        .trim()
                        .take(NewPoll.MAX_EXPLANATION_LENGTH)
                } else {
                    ""
                },
            allowsRevoting = !isQuiz && allowsRevoting,
            hidesResults = hidesResults,
            shufflesOptions = shufflesOptions,
            allowsAddingOptions = canAllowAddingOptions(forceAnonymous) && allowsAddingOptions,
            openPeriod = closes.openPeriod,
            closeDate = closes.closeDate,
        )
    }

    /** When the poll closes: in seconds after it is sent, or at a moment; null for a custom moment not in the future. */
    private fun closesAt(now: Long): Closes? =
        when (closing) {
            PollClosing.Never -> Closes()
            PollClosing.Custom -> customCloseDate?.takeIf { it > now }?.let { Closes(closeDate = it) }
            else -> Closes(openPeriod = closing.seconds)
        }

    private data class Closes(
        val openPeriod: Int = 0,
        val closeDate: Long = 0,
    )

    /** What the form holds now, for process death. */
    val draft: PollDraft
        get() =
            PollDraft(
                question = question.text.toString(),
                options = fields.map { it.text.text.toString() },
                links = fields.map { field -> field.link?.text?.toString() },
                description = description.text.toString(),
                explanation = explanation.text.toString(),
                photo = photo,
                isAnonymous = isAnonymous,
                allowsMultipleAnswers = allowsMultipleAnswers,
                allowsRevoting = allowsRevoting,
                hidesResults = hidesResults,
                shufflesOptions = shufflesOptions,
                allowsAddingOptions = allowsAddingOptions,
                isQuiz = isQuiz,
                requiredQuiz = requiredQuiz,
                correctOptionIndexes = fields.indices.filter { fields[it].key in correctKeys },
                closing = closing,
                customCloseDate = customCloseDate,
            )

    /** Empties the form for the next poll; the photo's copy is the caller's to discard. */
    fun clear() {
        question.clearText()
        description.clearText()
        explanation.clearText()
        fill(PollDraft())
    }

    private fun fill(draft: PollDraft) {
        fields.clear()
        draft.options.forEachIndexed { index, text -> fields += newField(text, draft.links.getOrNull(index)) }
        while (fields.size < NewPoll.MIN_OPTIONS) fields += newField("", null)
        photo = draft.photo
        isAnonymous = draft.isAnonymous
        allowsMultipleAnswers = draft.allowsMultipleAnswers
        allowsRevoting = draft.allowsRevoting
        hidesResults = draft.hidesResults
        shufflesOptions = draft.shufflesOptions
        allowsAddingOptions = draft.allowsAddingOptions
        isQuiz = draft.isQuiz
        requiredQuiz = draft.requiredQuiz
        closing = draft.closing
        customCloseDate = draft.customCloseDate
        correctKeys.clear()
        correctKeys += draft.correctOptionIndexes.mapNotNull { fields.getOrNull(it)?.key }
    }

    private fun newField(
        text: String,
        link: String?,
    ) = PollOptionField(nextKey++, TextFieldState(text), link)
}

/**
 * One answer option of the [PollEditor]: its text field, and its link's field while it has one ([link]), by a [key]
 * that stays while the form lives.
 */
@Stable
class PollOptionField(
    val key: Long,
    val text: TextFieldState,
    initialLink: String? = null,
) {
    /** The link's field; null while the option has no link. */
    var link by mutableStateOf(initialLink?.let(::TextFieldState))
        private set

    /** Gives the option an empty link to type, or takes its link away. */
    fun toggleLink() {
        link = if (link == null) TextFieldState() else null
    }

    /** The text as options are compared: trimmed, case ignored. */
    internal val normalized: String get() =
        text.text
            .toString()
            .trim()
            .lowercase(Locale.ROOT)

    internal fun toNewOption() =
        NewPollOption(
            text =
                text.text
                    .toString()
                    .trim()
                    .take(NewPoll.MAX_OPTION_LENGTH),
            link =
                link
                    ?.text
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
        )
}

/** When a new poll closes by itself: never, an hour or a day after it is sent, or at a moment the user picks. */
enum class PollClosing(
    val seconds: Int,
) {
    Never(0),
    Hour(SECONDS_PER_HOUR),
    Day(SECONDS_PER_DAY),
    Custom(0),
}

private const val SECONDS_PER_HOUR = 3_600
private const val SECONDS_PER_DAY = 86_400

/** What a [PollEditor] holds, as plain values: for process death, and for previews and tests. */
data class PollDraft(
    val question: String = "",
    val options: List<String> = emptyList(),
    /** Each option's link, by the same index; null for none. */
    val links: List<String?> = emptyList(),
    val description: String = "",
    val explanation: String = "",
    val photo: Attachment.Photo? = null,
    val isAnonymous: Boolean = true,
    val allowsMultipleAnswers: Boolean = false,
    val allowsRevoting: Boolean = true,
    val hidesResults: Boolean = false,
    val shufflesOptions: Boolean = false,
    val allowsAddingOptions: Boolean = false,
    val isQuiz: Boolean = false,
    val correctOptionIndexes: List<Int> = emptyList(),
    val requiredQuiz: Boolean? = null,
    val closing: PollClosing = PollClosing.Never,
    val customCloseDate: Long? = null,
) {
    override fun toString() = "PollDraft(options=${options.size}, isQuiz=$isQuiz, closing=$closing, …)"
}

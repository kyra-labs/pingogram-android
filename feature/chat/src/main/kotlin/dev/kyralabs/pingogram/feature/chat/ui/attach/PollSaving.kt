package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.Attachment

// The poll form in a SavedStateHandle, which keeps only Bundle types, for process death: its texts, its switches as
// one array, its photo as AttachSaving.kt writes an attachment, and the options' links by index ("" for none).

/** Keeps the poll form: whether it is open, and what it holds. */
internal fun SavedStateHandle.savePoll(
    open: Boolean,
    draft: PollDraft,
) {
    this[KEY_OPEN] = open
    this[KEY_TEXTS] = arrayListOf(draft.question, draft.description, draft.explanation)
    this[KEY_OPTIONS] = ArrayList(draft.options)
    this[KEY_LINKS] = draft.links.mapTo(ArrayList()) { it ?: NO_LINK }
    this[KEY_FLAGS] =
        booleanArrayOf(
            draft.isAnonymous,
            draft.allowsMultipleAnswers,
            draft.isQuiz,
            draft.allowsRevoting,
            draft.hidesResults,
            draft.shufflesOptions,
            draft.allowsAddingOptions,
        )
    this[KEY_CORRECT] = draft.correctOptionIndexes.toIntArray()
    this[KEY_REQUIRED_QUIZ] = draft.requiredQuiz
    this[KEY_PHOTO] = draft.photo?.saved()
    this[KEY_CLOSING] = draft.closing.name
    this[KEY_CLOSE_DATE] = draft.customCloseDate
    this[KEY_VOTERS] = draft.voters.name
    this[KEY_COUNTRIES] = ArrayList(draft.countries)
}

/** Whether the poll form was open. */
internal fun SavedStateHandle.restoredPollOpen(): Boolean = get<Boolean>(KEY_OPEN) == true

/** What the poll form held, or an empty form. */
internal fun SavedStateHandle.restoredPoll(): PollDraft {
    val flags = get<BooleanArray>(KEY_FLAGS)?.takeIf { it.size == FLAGS } ?: return PollDraft()
    val texts = get<ArrayList<String>>(KEY_TEXTS).orEmpty()
    return PollDraft(
        question = texts.getOrNull(0).orEmpty(),
        description = texts.getOrNull(1).orEmpty(),
        explanation = texts.getOrNull(2).orEmpty(),
        options = get<ArrayList<String>>(KEY_OPTIONS).orEmpty(),
        links = get<ArrayList<String>>(KEY_LINKS).orEmpty().map { it.takeIf { link -> link != NO_LINK } },
        isAnonymous = flags[0],
        allowsMultipleAnswers = flags[1],
        isQuiz = flags[2],
        allowsRevoting = flags[3],
        hidesResults = flags[4],
        shufflesOptions = flags[5],
        allowsAddingOptions = flags[6],
        correctOptionIndexes = get<IntArray>(KEY_CORRECT)?.toList().orEmpty(),
        requiredQuiz = get<Boolean>(KEY_REQUIRED_QUIZ),
        photo = get<String>(KEY_PHOTO)?.let(::restoredAttachment) as? Attachment.Photo,
        closing = PollClosing.entries.firstOrNull { it.name == get<String>(KEY_CLOSING) } ?: PollClosing.Never,
        customCloseDate = get<Long>(KEY_CLOSE_DATE),
        voters = PollVoters.entries.firstOrNull { it.name == get<String>(KEY_VOTERS) } ?: PollVoters.Everyone,
        countries = get<ArrayList<String>>(KEY_COUNTRIES).orEmpty(),
    )
}

private const val FLAGS = 7

/** A link left out: an option may hold an empty link while it is typed, so "" cannot say "none". */
private const val NO_LINK = "\u0000"

private const val KEY_OPEN = "chat.pollOpen"
private const val KEY_TEXTS = "chat.pollTexts"
private const val KEY_OPTIONS = "chat.pollOptions"
private const val KEY_LINKS = "chat.pollLinks"
private const val KEY_FLAGS = "chat.pollFlags"
private const val KEY_CORRECT = "chat.pollCorrect"
private const val KEY_REQUIRED_QUIZ = "chat.pollRequiredQuiz"
private const val KEY_PHOTO = "chat.pollPhoto"
private const val KEY_CLOSING = "chat.pollClosing"
private const val KEY_CLOSE_DATE = "chat.pollCloseDate"
private const val KEY_VOTERS = "chat.pollVoters"
private const val KEY_COUNTRIES = "chat.pollCountries"

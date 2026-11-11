package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.NewPollOption
import org.drinkless.tdlib.TdApi

/**
 * `inputMessagePoll` of this poll: plain question, description and options (only Premium users may add custom emoji),
 * the photo as a local file, each answer's link as `inputPollMediaLink`. A quiz takes as many answers per voter as it
 * has correct ones, and its votes are final; a regular poll keeps the user's choices.
 */
internal fun NewPoll.toInputMessagePoll(): TdApi.InputMessagePoll {
    val type =
        if (isQuiz) {
            TdApi.InputPollTypeQuiz(correctOptionIndexes.toIntArray(), plainText(explanation), null)
        } else {
            TdApi.InputPollTypeRegular(allowsAddingOptions && !isAnonymous)
        }
    val photo =
        photo?.let {
            TdApi.InputPollMediaPhoto(
                TdApi.InputPhoto(TdApi.InputFileLocal(it.path), null, null, intArrayOf(), it.width, it.height),
            )
        }
    return TdApi.InputMessagePoll(
        plainText(question),
        options
            .map {
                TdApi.InputPollOption(
                    plainText(it.text),
                    it.link?.let(TdApi::InputPollMediaLink),
                )
            }.toTypedArray(),
        description.takeIf { it.isNotEmpty() }?.let(::plainText),
        photo,
        isAnonymous,
        if (isQuiz) correctOptionIndexes.size > 1 else allowsMultipleAnswers,
        !isQuiz && allowsRevoting,
        false,
        emptyArray(),
        shufflesOptions,
        hidesResults,
        type,
        openPeriod,
        closeDate.toInt(),
        false,
    )
}

/** `inputPollOption` of an answer added to a poll after it was sent. */
internal fun NewPollOption.toInputPollOption(): TdApi.InputPollOption =
    TdApi.InputPollOption(plainText(text), link?.let(TdApi::InputPollMediaLink))

private fun plainText(text: String) = TdApi.FormattedText(text, emptyArray())

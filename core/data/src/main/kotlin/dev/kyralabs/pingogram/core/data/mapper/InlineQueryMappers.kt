package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.InlineQueryResults
import dev.kyralabs.pingogram.core.domain.model.InlineResult
import dev.kyralabs.pingogram.core.domain.model.InlineResultsButton
import dev.kyralabs.pingogram.core.domain.model.InlineResultsButtonType
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

/**
 * A page of an inline bot's results (td_api.tl `inlineQueryResults`); results of a kind a newer TDLib added, and
 * stickers of a format Pingogram cannot show, are left out.
 */
internal fun TdApi.InlineQueryResults.toInlineQueryResults(): InlineQueryResults =
    InlineQueryResults(
        queryId = inlineQueryId,
        button = button?.toButton(),
        results = results.mapNotNull { it.toInlineResult() }.toImmutableList(),
        nextOffset = nextOffset.orEmpty(),
    )

private fun TdApi.InlineQueryResultsButton.toButton(): InlineResultsButton? {
    val buttonType =
        when (val type = type) {
            is TdApi.InlineQueryResultsButtonTypeStartBot -> InlineResultsButtonType.StartBot(type.parameter.orEmpty())
            is TdApi.InlineQueryResultsButtonTypeWebApp -> InlineResultsButtonType.WebApp
            else -> return null
        }
    return InlineResultsButton(text.orEmpty(), buttonType)
}

@Suppress("CyclomaticComplexMethod") // One branch per kind of result td_api.tl has.
private fun TdApi.InlineQueryResult.toInlineResult(): InlineResult? =
    when (this) {
        is TdApi.InlineQueryResultPhoto -> {
            InlineResult.Photo(id, photo.toPhoto(), title.orEmpty(), description.orEmpty())
        }

        is TdApi.InlineQueryResultAnimation -> {
            InlineResult.Animation(id, animation.toAnimation(), title.orEmpty())
        }

        is TdApi.InlineQueryResultSticker -> {
            sticker.toSticker()?.let { InlineResult.Sticker(id, it) }
        }

        is TdApi.InlineQueryResultVideo -> {
            InlineResult.Video(id, video.toVideo(), title.orEmpty(), description.orEmpty())
        }

        is TdApi.InlineQueryResultArticle -> {
            InlineResult.Article(id, url.orEmpty(), title.orEmpty(), description.orEmpty(), thumbnail?.toThumbnail())
        }

        is TdApi.InlineQueryResultContact -> {
            InlineResult.Contact(id, contact.toContact(), thumbnail?.toThumbnail())
        }

        is TdApi.InlineQueryResultLocation -> {
            InlineResult.Location(id, location.toLocation(), title.orEmpty(), thumbnail?.toThumbnail())
        }

        is TdApi.InlineQueryResultVenue -> {
            InlineResult.Venue(id, venue.toVenue(), thumbnail?.toThumbnail())
        }

        is TdApi.InlineQueryResultAudio -> {
            InlineResult.Audio(id, audio.toAudio())
        }

        is TdApi.InlineQueryResultDocument -> {
            InlineResult.Document(id, document.toDocument(), title.orEmpty(), description.orEmpty())
        }

        is TdApi.InlineQueryResultVoiceNote -> {
            InlineResult.VoiceNote(id, voiceNote.toVoiceNote(), title.orEmpty())
        }

        is TdApi.InlineQueryResultGame -> {
            InlineResult.Game(id, game.title.orEmpty(), game.description.orEmpty(), game.photo?.toPhoto())
        }

        else -> {
            null
        }
    }

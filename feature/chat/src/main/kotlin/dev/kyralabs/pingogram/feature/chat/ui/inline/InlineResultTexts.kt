package dev.kyralabs.pingogram.feature.chat.ui.inline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.InlineResult
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.SendOptionsMenu
import dev.kyralabs.pingogram.feature.chat.ui.content.MediaLabel
import dev.kyralabs.pingogram.feature.chat.ui.content.MinithumbnailPlaceholder
import dev.kyralabs.pingogram.feature.chat.ui.content.StickerImage
import dev.kyralabs.pingogram.feature.chat.ui.content.TdlibImage
import dev.kyralabs.pingogram.feature.chat.ui.content.asPicture
import dev.kyralabs.pingogram.feature.chat.ui.content.fadingOutAs
import dev.kyralabs.pingogram.feature.chat.ui.content.fitInSquare
import dev.kyralabs.pingogram.feature.chat.ui.content.mediaDurationText
import dev.kyralabs.pingogram.feature.chat.ui.content.rememberImageAlpha
import dev.kyralabs.pingogram.feature.chat.ui.content.selectPhotoSize
import dev.kyralabs.pingogram.core.ui.R as UiR

/** What a row says: its [title], the line under it ([supporting]), left to right where it is a number ([isNumeric]). */
internal class InlineResultTexts(
    val title: String,
    val supporting: String,
    val isNumeric: Boolean = false,
)

@Composable
internal fun rowTexts(result: InlineResult): InlineResultTexts =
    when (result) {
        is InlineResult.Article -> {
            InlineResultTexts(result.title, result.description.ifBlank { result.url })
        }

        is InlineResult.Contact -> {
            val contact = result.contact
            InlineResultTexts("${contact.firstName} ${contact.lastName}".trim(), contact.phoneNumber, isNumeric = true)
        }

        is InlineResult.Location -> {
            InlineResultTexts(result.title.ifBlank { stringResource(UiR.string.preview_location) }, "")
        }

        is InlineResult.Venue -> {
            InlineResultTexts(result.venue.title, result.venue.address)
        }

        is InlineResult.Audio -> {
            val audio = result.audio
            InlineResultTexts(audio.title.ifBlank { audio.fileName }, audio.performer)
        }

        is InlineResult.Document -> {
            InlineResultTexts(result.title.ifBlank { result.document.fileName }, result.description)
        }

        is InlineResult.VoiceNote -> {
            InlineResultTexts(
                result.title.ifBlank { stringResource(UiR.string.preview_voice_message) },
                mediaDurationText(result.voiceNote.duration),
                isNumeric = true,
            )
        }

        is InlineResult.Game -> {
            InlineResultTexts(result.title, result.description)
        }

        else -> {
            InlineResultTexts("", "")
        }
    }

/** What screen readers hear after a result's kind: its title, or what names it best. */
@Composable
internal fun InlineResult.spokenTitle(): String =
    when (this) {
        is InlineResult.Photo -> title.ifBlank { description }
        is InlineResult.Animation -> title
        is InlineResult.Sticker -> sticker.emoji
        is InlineResult.Video -> title.ifBlank { description }
        else -> rowTexts(this).let { listOf(it.title, it.supporting) }.filter { it.isNotBlank() }.joinToString(", ")
    }

/** The kind of result, as screen readers hear it first. */
internal fun InlineResult.kindLabel(): Int =
    when (this) {
        is InlineResult.Photo -> UiR.string.preview_photo
        is InlineResult.Animation -> UiR.string.preview_gif
        is InlineResult.Sticker -> UiR.string.preview_sticker
        is InlineResult.Video -> UiR.string.preview_video
        is InlineResult.Article -> R.string.inline_result_article
        is InlineResult.Contact -> UiR.string.preview_contact
        is InlineResult.Location -> UiR.string.preview_location
        is InlineResult.Venue -> R.string.inline_result_venue
        is InlineResult.Audio -> UiR.string.preview_audio
        is InlineResult.Document -> UiR.string.preview_file
        is InlineResult.VoiceNote -> UiR.string.preview_voice_message
        is InlineResult.Game -> UiR.string.preview_game
    }

/** The icon of a row without a picture of its own. */
internal fun InlineResult.kindIcon(): Int =
    when (this) {
        is InlineResult.Contact -> UiR.drawable.ic_person_filled
        is InlineResult.Location, is InlineResult.Venue -> UiR.drawable.ic_location_on_filled
        is InlineResult.Audio -> UiR.drawable.ic_audio_file_filled
        is InlineResult.Document -> UiR.drawable.ic_description_filled
        is InlineResult.VoiceNote -> UiR.drawable.ic_mic_filled
        is InlineResult.Game -> UiR.drawable.ic_sports_esports_filled
        else -> UiR.drawable.ic_link
    }

/** The bot's picture of a row's result, where it sent one. */
internal fun InlineResult.rowThumbnail(): Thumbnail? =
    when (this) {
        is InlineResult.Article -> thumbnail
        is InlineResult.Contact -> thumbnail
        is InlineResult.Location -> thumbnail
        is InlineResult.Venue -> thumbnail
        is InlineResult.Audio -> audio.albumCoverThumbnail
        is InlineResult.Document -> document.thumbnail
        else -> null
    }

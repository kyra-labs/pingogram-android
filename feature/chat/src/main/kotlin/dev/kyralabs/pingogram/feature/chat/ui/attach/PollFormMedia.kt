package dev.kyralabs.pingogram.feature.chat.ui.attach

import dev.kyralabs.pingogram.core.ui.component.StateCrossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.fileSizeText
import java.io.File
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The poll's photo (board 5a): "Add media" (a tonal button with the add-a-photo symbol) while there is none, a
 * [LoadingIndicator] while a picked photo is prepared, then the photo's thumbnail (`contentIcon`, `medium` corners)
 * beside "Photo · 1.2 MB", "Change media" and a remove button. Polls take one photo or video; Pingogram sends photos.
 */
internal fun LazyListScope.pollMediaItem(
    editor: PollEditor,
    actions: PollFormActions,
) {
    item(key = MEDIA) { FormItem {
        val photo = editor.photo
        val modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.large)
        // Adding, preparing and the picked photo crossfade in place.
        StateCrossfade(
            targetState = editor.isPreparingPhoto to photo,
            contentKey = { (preparing, picked) ->
                when {
                    preparing -> 0
                    picked != null -> 1
                    else -> 2
                }
            },
            label = "pollMedia",
        ) { (preparing, picked) ->
            when {
                preparing -> PreparingPhoto(modifier)
                picked != null -> PickedPhoto(picked, actions, modifier)
                else -> AddMediaButton(actions.onPickPhoto, modifier)
            }
        }
    }}
}

@Composable
private fun AddMediaButton(
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Row(modifier) {
        FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(UiR.drawable.ic_add_a_photo),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Text(
                text = stringResource(R.string.poll_add_media),
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LoadingIndicator
@Composable
private fun PreparingPhoto(modifier: Modifier) {
    val label = stringResource(R.string.poll_media_preparing)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoadingIndicator(Modifier.size(MaterialTheme.sizes.contentIcon))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PickedPhoto(
    photo: Attachment.Photo,
    actions: PollFormActions,
    modifier: Modifier,
) {
    val context = LocalPlatformContext.current
    val side = with(LocalDensity.current) { MaterialTheme.sizes.contentIcon.roundToPx() }
    val request =
        remember(photo.path, side) {
            ImageRequest
                .Builder(context)
                .data(File(photo.path))
                .size(side)
                .build()
        }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(MaterialTheme.sizes.contentIcon).clip(MaterialTheme.shapes.medium),
        )
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(stringResource(R.string.poll_media_photo), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = fileSizeText(photo.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextButton(onClick = actions.onPickPhoto, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.poll_change_media))
        }
        IconButton(onClick = actions.onRemovePhoto, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(UiR.drawable.ic_close), stringResource(R.string.poll_remove_media))
        }
    }
}

private const val MEDIA = "media"

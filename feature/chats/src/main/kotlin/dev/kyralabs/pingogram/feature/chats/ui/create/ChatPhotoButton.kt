package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chats.R
import java.io.File
import dev.kyralabs.pingogram.core.ui.R as UiR

// The photo of a new group or channel (docs/DESIGN_SYSTEM.md §6, "New group").

/**
 * The new chat's photo: a [MaterialTheme.sizes] `avatarLarge` circle in `primaryContainer` with the add-a-photo
 * symbol, or the picked photo with a camera badge in `primary` at its bottom end; a tap opens the photo picker
 * ([onPick]). While the photo is copied a contained loading indicator shows in it; with a photo, "Remove photo"
 * ([onRemove]) follows it. Screen readers hear one button, [description].
 */
@Composable
internal fun ChatPhotoButton(
    photo: ChatPhoto,
    description: String,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val sizes = MaterialTheme.sizes
    val colors = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(sizes.avatarLarge),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = onPick,
                modifier =
                    Modifier.size(sizes.avatarLarge).semantics {
                        contentDescription = description
                        role = Role.Button
                    },
                enabled = enabled,
                shape = CircleShape,
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) { PhotoContent(photo) }
            }
            if (photo is ChatPhoto.Ready) CameraBadge(Modifier.align(Alignment.BottomEnd))
        }
        if (photo is ChatPhoto.Ready) {
            TextButton(onClick = onRemove, enabled = enabled, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.create_photo_remove), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The camera badge on a picked photo, in `primary` with a `surface` ring: tapping the photo changes it. */
@Composable
private fun CameraBadge(modifier: Modifier = Modifier) {
    val sizes = MaterialTheme.sizes
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .size(sizes.avatarBadge)
                .border(sizes.avatarRing, colors.surface, CircleShape)
                .padding(sizes.avatarRing)
                .background(colors.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_photo_camera_filled),
            contentDescription = null,
            modifier = Modifier.size(sizes.badgeIcon),
            tint = colors.onPrimary,
        )
    }
}

/** What the photo's circle shows for [photo]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PhotoContent(photo: ChatPhoto) {
    when (photo) {
        ChatPhoto.None -> {
            Icon(
                painter = painterResource(R.drawable.ic_add_a_photo),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.avatarLarge / PHOTO_ICON_FRACTION),
            )
        }

        ChatPhoto.Preparing -> {
            ContainedLoadingIndicator()
        }

        is ChatPhoto.Ready -> {
            PickedPhoto(photo.path)
        }
    }
}

/** The copy at [path] as the circle's picture, decoded at the circle's size, crossfading in. */
@Composable
private fun PickedPhoto(path: String) {
    val context = LocalContext.current
    val request =
        remember(context, path) {
            ImageRequest
                .Builder(context)
                .data(File(path))
                .crossfade(true)
                .build()
        }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = Modifier.fillMaxSize().clip(CircleShape),
        contentScale = ContentScale.Crop,
    )
}

/** The add-a-photo symbol takes a third of the circle. */
private const val PHOTO_ICON_FRACTION = 3

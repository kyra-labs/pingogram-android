package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * The user's profile photos (`docs/DESIGN_SYSTEM.md` §6 "Profile header"): a `titleSmallEmphasized` heading, then a
 * [HorizontalUncontainedCarousel] of square photos, `sizes.profilePhotoItem` each, masked to `extraLarge` corners, the
 * first at the window margin ([margin]). Each loads the smallest of its sizes that fills it, faded in over a tonal
 * placeholder, and is a picture for screen readers ("Profile photo 2 of 5"). The next page loads as the carousel
 * nears the last photo loaded ([ProfileEvent.LoadMorePhotos]). Shown only with more than one photo: the first is the
 * avatar already.
 */
@Composable
internal fun ProfilePhotosCarousel(
    photos: ProfilePhotos,
    onEvent: (ProfileEvent) -> Unit,
    margin: Dp,
    modifier: Modifier = Modifier,
) {
    if (photos.totalCount <= 1 || photos.items.size <= 1) return
    val items = photos.items
    val state = rememberCarouselState { items.size }
    val itemSize = MaterialTheme.sizes.profilePhotoItem
    val itemSizePx = with(LocalDensity.current) { itemSize.roundToPx() }
    val resources = LocalResources.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(state, items.size, photos.totalCount) {
        if (items.size >= photos.totalCount) return@LaunchedEffect
        snapshotFlow { state.currentItem >= items.size - LOAD_MORE_THRESHOLD }
            .distinctUntilChanged()
            .filter { it }
            .collect { currentOnEvent(ProfileEvent.LoadMorePhotos) }
    }
    Column(modifier) {
        ProfileSectionHeader(stringResource(R.string.profile_section_photos), Modifier.padding(horizontal = margin))
        HorizontalUncontainedCarousel(
            state = state,
            itemWidth = itemSize,
            modifier = Modifier.fillMaxWidth().height(itemSize),
            itemSpacing = MaterialTheme.spacing.extraSmall,
            contentPadding = PaddingValues(horizontal = margin),
        ) { index ->
            val item = items[index]
            val fileId = remember(item, itemSizePx) { item.photo.fileIdFor(itemSizePx) }
            val description =
                remember(index, photos.totalCount) {
                    resources.getString(R.string.profile_photo_position, index + 1, photos.totalCount)
                }
            ProfileImage(
                fileId = fileId,
                sizePx = itemSizePx,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .maskClip(MaterialTheme.shapes.extraLarge)
                        .semantics { contentDescription = description },
            )
        }
    }
}

/** How near the last photo loaded the next page is asked for. */
private const val LOAD_MORE_THRESHOLD = 4

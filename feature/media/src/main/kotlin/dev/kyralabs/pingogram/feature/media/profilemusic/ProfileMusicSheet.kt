package dev.kyralabs.pingogram.feature.media.profilemusic

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.animateItem
import dev.kyralabs.pingogram.core.ui.component.rememberItemMotion
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.feature.media.ui.formatDuration
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The playlist (board 13b), a [ModalBottomSheet] from the card's cover: "Profile music" over how many songs there are,
 * on the user's own profile "Add music"; why the last change failed in `error`, as a polite live region; then the
 * songs, a tap playing one. The playing one shows the equalizer symbol and its title in `primary`. On the user's own
 * profile each song ends in an options button (an expressive menu: "Move up", "Move down", "Remove" in `error`), whose
 * items are also the row's custom actions for screen readers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileMusicSheet(
    content: ProfileMusicUiState.Content,
    pickerMissing: Boolean,
    onEvent: (ProfileMusicEvent) -> Unit,
    onAdd: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(ProfileMusicEvent.CloseSheet) },
        sheetState =
            rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        ProfileMusicSheetContent(content, pickerMissing, onEvent, onAdd)
    }
}

/** What the playlist sheet holds, drawn without the sheet's window in previews and goldens. */
@Composable
internal fun ProfileMusicSheetContent(
    content: ProfileMusicUiState.Content,
    pickerMissing: Boolean,
    onEvent: (ProfileMusicEvent) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(bottom = MaterialTheme.spacing.large)) {
        SheetHeader(content, onAdd)
        val error = content.message
        if (error != null || pickerMissing) {
            Text(
                text = if (error != null) errorText(error) else stringResource(R.string.profile_music_no_picker),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val motion = rememberItemMotion()
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small),
        ) {
            itemsIndexed(
                items = content.tracks,
                key = { _, track -> track.fileId },
                contentType = { _, _ -> TRACK },
            ) { index, track ->
                TrackRow(
                    track = track,
                    index = index,
                    content = content,
                    onEvent = onEvent,
                    modifier = Modifier.animateItem(this, motion),
                )
            }
        }
    }
}

/** "Profile music" over the count; "Add music" at the end on the user's own profile. */
@Composable
private fun SheetHeader(
    content: ProfileMusicUiState.Content,
    onAdd: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.padding(
                start = MaterialTheme.spacing.large,
                end = MaterialTheme.spacing.medium,
                bottom = MaterialTheme.spacing.small,
            ),
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(
                text = stringResource(R.string.profile_music_header),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            val count = content.totalCount
            Text(
                text =
                    if (content.isOwn) {
                        pluralStringResource(R.plurals.profile_music_own_count, count, count)
                    } else {
                        pluralStringResource(R.plurals.profile_music_count, count, count)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (content.isOwn) AddMusicButton(content.adding, onAdd)
    }
}

/**
 * One song: its cover, title (the equalizer symbol and `primary` while it is the shown one) over its artist and
 * length, and on the user's own profile the options button. A tap plays it; it is selected for screen readers while
 * shown, and on the user's own profile offers its options as custom actions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrackRow(
    track: MusicTrack,
    index: Int,
    content: ProfileMusicUiState.Content,
    onEvent: (ProfileMusicEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCurrent = index == content.current
    val playLabel = stringResource(R.string.profile_music_play_song, track.title.bidiIsolated())
    val options = trackOptions(index, content.tracks.size)
    val optionLabels = options.map { stringResource(it.label) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MaterialTheme.sizes.avatarRow + MaterialTheme.spacing.large)
                .clip(MaterialTheme.shapes.large)
                .clickable(role = Role.Button, onClickLabel = playLabel) { onEvent(ProfileMusicEvent.Play(index)) }
                .semantics {
                    selected = isCurrent
                    if (content.isOwn) {
                        customActions =
                            options.mapIndexed { i, option ->
                                CustomAccessibilityAction(optionLabels[i]) {
                                    onEvent(option.event)
                                    true
                                }
                            }
                    }
                }.padding(
                    start = MaterialTheme.spacing.small,
                    top = MaterialTheme.spacing.small,
                    bottom = MaterialTheme.spacing.small,
                ),
    ) {
        TrackCover(track, MaterialTheme.sizes.avatarSmall)
        TrackTexts(track, isCurrent = isCurrent, isPlaying = isCurrent && content.isPlaying, Modifier.weight(1f))
        if (content.isOwn) TrackOptions(track, options, enabled = content.busy == null, onEvent)
    }
}

/**
 * A song's title (`primary`, after the equalizer symbol while [isPlaying], when [isCurrent]) over its artist and
 * length.
 */
@Composable
private fun TrackTexts(
    track: MusicTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isPlaying) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_graphic_eq),
                    contentDescription = stringResource(R.string.profile_music_playing),
                    tint = colors.primary,
                    modifier =
                        Modifier
                            .padding(end = MaterialTheme.spacing.extraSmall)
                            .size(MaterialTheme.sizes.inlineIconSmall),
                )
            }
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall.userContent,
                color = if (isCurrent) colors.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val length = formatDuration(track.durationSeconds, locale)
        Text(
            text =
                if (track.performer.isBlank()) {
                    length
                } else {
                    stringResource(R.string.profile_music_track_line, track.performer.bidiIsolated(), length)
                },
            style = MaterialTheme.typography.bodySmall.userContent,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The options button of a song of the user's own profile and its expressive menu. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrackOptions(
    track: MusicTrack,
    options: List<TrackOption>,
    enabled: Boolean,
    onEvent: (ProfileMusicEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(UiR.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.profile_music_options, track.title.bidiIsolated()),
            )
        }
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            val moves = options.filterNot { it.destructive }
            val removal = options.filter { it.destructive }
            val pick: (ProfileMusicEvent) -> Unit = {
                expanded = false
                onEvent(it)
            }
            OptionGroup(moves, groupIndex = 0, groupCount = 2, onPick = pick)
            OptionGroup(removal, groupIndex = 1, groupCount = 2, onPick = pick)
        }
    }
}

/** A group of the options menu, [groupIndex] of [groupCount]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptionGroup(
    options: List<TrackOption>,
    groupIndex: Int,
    groupCount: Int,
    onPick: (ProfileMusicEvent) -> Unit,
) {
    if (options.isEmpty()) return
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, groupCount)) {
        options.forEachIndexed { i, option ->
            val color = if (option.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            DropdownMenuItem(
                onClick = { onPick(option.event) },
                text = { Text(stringResource(option.label), color = color) },
                shape = MenuDefaults.itemShape(i, options.size).shape,
                leadingIcon = { Icon(painterResource(option.icon), contentDescription = null, tint = color) },
            )
        }
    }
}

/** An option of a song of the user's own profile. */
private class TrackOption(
    val label: Int,
    @param:DrawableRes val icon: Int,
    val event: ProfileMusicEvent,
    val destructive: Boolean = false,
)

/** What song [index] of [count] offers: moving where it can, and removing. */
private fun trackOptions(
    index: Int,
    count: Int,
): List<TrackOption> =
    buildList {
        if (index >
            0
        ) {
            add(
                TrackOption(
                    R.string.profile_music_move_up,
                    R.drawable.ic_keyboard_arrow_up,
                    ProfileMusicEvent.MoveUp(index),
                ),
            )
        }
        if (index < count - 1) {
            add(
                TrackOption(
                    R.string.profile_music_move_down,
                    UiR.drawable.ic_keyboard_arrow_down,
                    ProfileMusicEvent.MoveDown(index),
                ),
            )
        }
        add(
            TrackOption(
                R.string.profile_music_remove,
                UiR.drawable.ic_delete,
                ProfileMusicEvent.Remove(index),
                destructive = true,
            ),
        )
    }

private const val TRACK = "track"

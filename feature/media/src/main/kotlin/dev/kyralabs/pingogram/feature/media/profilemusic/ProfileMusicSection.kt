package dev.kyralabs.pingogram.feature.media.profilemusic

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.feature.media.storyalbum.AlbumImage
import dev.kyralabs.pingogram.feature.media.ui.formatDuration
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A profile's music (boards 13a, 13b) for the profile of user [userId] to show ([isOwn]: the user's own), through a
 * slot `:app` fills, as features share no code. Nothing shows while the songs load, for another user without songs, or
 * when they fail; the user's own profile shows its music even when empty, with "Add music".
 *
 * Its own view model ([ProfileMusicViewModel], keyed by the user) plays the songs while the profile is started and
 * pauses them when it stops.
 */
@Composable
fun ProfileMusicSection(
    userId: Long,
    isOwn: Boolean,
    modifier: Modifier = Modifier,
    viewModel: ProfileMusicViewModel = hiltViewModel(key = "profileMusic-$userId"),
) {
    LaunchedEffect(viewModel, userId, isOwn) { viewModel.start(userId, isOwn) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val content = state as? ProfileMusicUiState.Content ?: return
    var pickerMissing by rememberSaveable { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.onEvent(ProfileMusicEvent.Add(uri.toString()))
        }
    val pickSong = {
        pickerMissing =
            try {
                picker.launch(arrayOf(AUDIO_TYPES))
                false
            } catch (_: ActivityNotFoundException) {
                true
            }
    }
    ProfileMusicContent(content, viewModel.progress, viewModel::onEvent, pickSong, modifier)
    if (content.sheetOpen) {
        ProfileMusicSheet(content, pickerMissing, viewModel::onEvent, pickSong)
    }
}

/**
 * The card a profile shows (board 13a): the shown song's cover, which opens the playlist, its title over its artist
 * and place, play or pause and the next song, then how far it played. The user's own profile without songs shows a
 * card that offers "Add music".
 */
@Composable
internal fun ProfileMusicContent(
    content: ProfileMusicUiState.Content,
    progress: State<Float>,
    onEvent: (ProfileMusicEvent) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = content.currentTrack
    Surface(
        modifier = modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.small),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            if (track == null) {
                EmptyOwnMusic(content.adding, onAdd)
            } else {
                PlayerRow(content, track, onEvent)
                SeekRow(track, progress, enabled = content.isPlaying || progress.value > 0f, onEvent)
                if (content.cannotPlay) {
                    Text(
                        text = stringResource(R.string.profile_music_cannot_play),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** The shown song's cover, title, artist and place, and the play and next buttons. */
@Composable
private fun PlayerRow(
    content: ProfileMusicUiState.Content,
    track: MusicTrack,
    onEvent: (ProfileMusicEvent) -> Unit,
) {
    val openPlaylist = stringResource(R.string.profile_music_open_playlist)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        TrackCover(
            track = track,
            size = MaterialTheme.sizes.avatarRow,
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button, onClickLabel = openPlaylist) {
                        onEvent(ProfileMusicEvent.OpenSheet)
                    }.semantics { contentDescription = openPlaylist },
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmallEmphasized.userContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = placeLine(track, content.current, content.totalCount),
                style = MaterialTheme.typography.bodySmall.userContent,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PlayButton(content.isPlaying) { onEvent(ProfileMusicEvent.TogglePlay) }
        if (content.tracks.size > 1) {
            FilledTonalIconButton(
                onClick = { onEvent(ProfileMusicEvent.Next) },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_next_filled),
                    contentDescription = stringResource(R.string.profile_music_next),
                )
            }
        }
    }
}

/** Play or pause, a filled square that rounds while pressed. */
@Composable
private fun PlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    FilledIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painter =
                painterResource(
                    if (isPlaying) UiR.drawable.ic_pause_filled else UiR.drawable.ic_play_arrow_filled,
                ),
            contentDescription =
                stringResource(if (isPlaying) R.string.profile_music_pause else R.string.profile_music_play),
        )
    }
}

/**
 * How far the shown song played, between its position and its length: an expressive [Slider] whose state follows
 * [progress] while the user does not hold it, so a tick redraws the slider alone, and the position's text changes once
 * a second; letting go seeks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekRow(
    track: MusicTrack,
    progress: State<Float>,
    enabled: Boolean,
    onEvent: (ProfileMusicEvent) -> Unit,
) {
    val sliderState = remember(track.fileId) { SliderState(value = progress.value) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val touching by rememberUpdatedState(sliderState.isDragging || pressed)
    LaunchedEffect(sliderState, progress) {
        snapshotFlow { progress.value }.collect { fraction -> if (!touching) sliderState.value = fraction }
    }
    val seconds by remember(sliderState, track.durationSeconds) {
        derivedStateOf { (sliderState.value * track.durationSeconds).toInt() }
    }
    val locale = LocalConfiguration.current.locales[0]
    val position = stringResource(R.string.profile_music_position)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        TimeText(formatDuration(seconds, locale))
        Slider(
            state = sliderState,
            onValueChange = { sliderState.value = it },
            enabled = enabled,
            modifier = Modifier.weight(1f).semantics { contentDescription = position },
            onValueChangeFinished = { onEvent(ProfileMusicEvent.Seek(sliderState.value)) },
            interactionSource = interactionSource,
        )
        TimeText(formatDuration(track.durationSeconds, locale))
    }
}

/** A time beside the slider: `labelSmall` with tabular figures, so it does not jitter as it counts. */
@Composable
private fun TimeText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.numericContent,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The user's own profile without songs: what profile music is, and "Add music". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnScope.EmptyOwnMusic(
    adding: Boolean,
    onAdd: () -> Unit,
) {
    Text(
        text = stringResource(R.string.profile_music_header),
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(R.string.profile_music_empty_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AddMusicButton(adding, onAdd, Modifier.align(Alignment.End))
}

/** "Add music", a tonal button with the add symbol; a loading indicator in its place while a song is added. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AddMusicButton(
    adding: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(onClick = onAdd, enabled = !adding, shapes = ButtonDefaults.shapes(), modifier = modifier) {
        if (adding) {
            LoadingIndicator(Modifier.size(ButtonDefaults.IconSize))
        } else {
            Icon(
                painterResource(UiR.drawable.ic_add),
                contentDescription = null,
                Modifier.size(ButtonDefaults.IconSize),
            )
        }
        Text(
            text = stringResource(R.string.profile_music_add),
            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
        )
    }
}

/** A song's album cover at [size] with `medium` corners; the music symbol on `tertiaryContainer` without one. */
@Composable
internal fun TrackCover(
    track: MusicTrack,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val cover = track.cover
    if (cover != null) {
        AlbumImage(cover, sizePx, modifier.size(size).clip(MaterialTheme.shapes.medium))
    } else {
        Surface(
            modifier = modifier.size(size),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_music_note),
                contentDescription = null,
                modifier = Modifier.padding(MaterialTheme.spacing.small),
            )
        }
    }
}

/** "Lena Fischer · 1 of 3", or "1 of 3" for a song without an artist. */
@Composable
private fun placeLine(
    track: MusicTrack,
    index: Int,
    totalCount: Int,
): String =
    if (track.performer.isBlank()) {
        stringResource(R.string.profile_music_place, index + 1, totalCount)
    } else {
        stringResource(R.string.profile_music_by_place, track.performer.bidiIsolated(), index + 1, totalCount)
    }

/** What the file picker offers for a song. */
private const val AUDIO_TYPES = "audio/*"

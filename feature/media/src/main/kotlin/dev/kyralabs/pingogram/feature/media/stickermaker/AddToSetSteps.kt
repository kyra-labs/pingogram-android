package dev.kyralabs.pingogram.feature.media.stickermaker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import dev.kyralabs.pingogram.core.domain.model.StickerSetNameCheck
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.sticker.StickerCover
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import java.io.File
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Choosing (board 14b): the sticker beside "Add to sticker set" in `titleLargeEmphasized` and why it takes an emoji
 * in `bodyMedium` `onSurfaceVariant`; the emoji ([EmojiChoices]); the user's own sets and "New sticker set"
 * ([SetChoices]); then "Add to Office cats" (or "Next" for a new set).
 */
@Composable
internal fun ChooseStep(
    sheet: AddToSetState,
    onEvent: (AddToSetEvent) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StickerPreview(sheet.stickerPath)
        Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
            Text(
                stringResource(R.string.make_sticker_add_to_set),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            Text(
                text = stringResource(R.string.make_sticker_add_to_set_why),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    EmojiChoices(sheet.emoji, onEvent)
    SetChoices(sheet, onEvent)
    val chosen = sheet.chosenSet
    SheetAction(
        sheet = sheet,
        label =
            chosen?.let { stringResource(R.string.make_sticker_add_to, it.title) }
                ?: stringResource(R.string.make_sticker_next),
        enabled = sheet.choice != null,
        onClick = { onEvent(AddToSetEvent.Confirm) },
    )
}

/** The sticker as written, on a `surfaceContainerHigh` tile with `largeIncreased` corners; decorative. */
@Composable
private fun StickerPreview(path: String) {
    AsyncImage(
        model = File(path),
        contentDescription = null,
        modifier =
            Modifier
                .size(MaterialTheme.sizes.avatarLarge)
                .clip(MaterialTheme.shapes.largeIncreased)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

/**
 * The sticker's emoji, one radio group: the quick ones as `IconToggleButton`s at the medium size, round and morphing
 * to a square when chosen (`toggleableShapes()`, chosen in `primary`), the one chosen in the full sheet first where it
 * is none of them, then "More emoji", which opens it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmojiChoices(
    emoji: String,
    onEvent: (AddToSetEvent) -> Unit,
) {
    val shown =
        if (emoji in
            AddToSetState.QUICK_EMOJI
        ) {
            AddToSetState.QUICK_EMOJI
        } else {
            listOf(emoji) + AddToSetState.QUICK_EMOJI.dropLast(1)
        }
    val colors =
        IconButtonDefaults.filledTonalIconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        )
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        shown.forEach { choice ->
            IconToggleButton(
                checked = choice == emoji,
                onCheckedChange = { onEvent(AddToSetEvent.Emoji(choice)) },
                modifier =
                    Modifier
                        .size(IconButtonDefaults.mediumContainerSize())
                        .semantics {
                            role = Role.RadioButton
                            selected = choice == emoji
                        },
                shapes = IconButtonDefaults.toggleableShapes(),
                colors = colors,
            ) { Text(choice, style = MaterialTheme.typography.headlineSmall) }
        }
        IconButton(
            onClick = { onEvent(AddToSetEvent.MoreEmoji) },
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                painterResource(UiR.drawable.ic_add),
                contentDescription = stringResource(R.string.make_sticker_more_emoji),
            )
        }
    }
}

/**
 * The sets, one radio group of segmented rows (`ListItemDefaults.segmentedShapes` on `surfaceContainerHigh`): each
 * set's cover, title (user content) over "12 stickers" and its radio button at the end, then "New sticker set" (the add
 * symbol in a `surfaceContainerHighest` circle) over "Name it on the next step". While they load a delayed indicator;
 * a failure says why with "Try again".
 */
@Composable
private fun SetChoices(
    sheet: AddToSetState,
    onEvent: (AddToSetEvent) -> Unit,
) {
    when (val sets = sheet.sets) {
        OwnedSets.Loading -> {
            val loading = stringResource(R.string.make_sticker_sets_loading)
            Box(Modifier.fillMaxWidth().heightIn(min = MaterialTheme.sizes.avatarLarge), Alignment.Center) {
                DelayedLoadingIndicator(Modifier.semantics { contentDescription = loading })
            }
        }

        is OwnedSets.Failed -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = errorText(sets.error),
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { onEvent(AddToSetEvent.RetrySets) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.make_sticker_retry))
                }
            }
        }

        is OwnedSets.Loaded -> {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                val count = sets.sets.size + 1
                sets.sets.forEachIndexed { index, set ->
                    SetRow(
                        selected = sheet.choice == set.name,
                        index = index,
                        count = count,
                        title = set.title,
                        supporting = pluralStringResource(R.plurals.make_sticker_set_size, set.size, set.size),
                        leading = { StickerCover(set.cover, MaterialTheme.sizes.avatarSmall) },
                        onClick = { onEvent(AddToSetEvent.Choose(set.name)) },
                    )
                }
                SetRow(
                    selected = sheet.choice == AddToSetState.NEW_SET,
                    index = count - 1,
                    count = count,
                    title = stringResource(R.string.make_sticker_new_set),
                    supporting = stringResource(R.string.make_sticker_new_set_next),
                    leading = { NewSetIcon() },
                    onClick = { onEvent(AddToSetEvent.Choose(AddToSetState.NEW_SET)) },
                )
            }
        }
    }
}

@Suppress("LongParameterList") // What the row shows, where it is in the group, and what it does.
@Composable
private fun SetRow(
    selected: Boolean,
    index: Int,
    count: Int,
    title: String,
    supporting: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = Modifier.semantics { role = Role.RadioButton },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        leadingContent = leading,
        supportingContent = { Text(supporting) },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
    ) {
        Text(
            title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.userContent,
        )
    }
}

@Composable
private fun NewSetIcon() {
    Box(
        Modifier
            .size(MaterialTheme.sizes.avatarSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(UiR.drawable.ic_add), contentDescription = null) }
}

/**
 * A new set: "New sticker set" in `titleLargeEmphasized` (a heading), what it is in `bodyMedium` `onSurfaceVariant`,
 * its name and its link (after "t.me/addstickers/", left to right) in filled `TextField`s, what Telegram says of the
 * link (a polite live region), then "Create set" and "Back".
 */
@Composable
internal fun NewSetStep(
    sheet: AddToSetState,
    onEvent: (AddToSetEvent) -> Unit,
) {
    val draft = sheet.newSet
    Text(
        text = stringResource(R.string.make_sticker_new_set),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLargeEmphasized,
    )
    Text(
        text = stringResource(R.string.make_sticker_new_set_about),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
    TextField(
        value = draft.title,
        onValueChange = { onEvent(AddToSetEvent.Title(it)) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !sheet.busy,
        textStyle = MaterialTheme.typography.bodyLarge.userContent,
        label = { Text(stringResource(R.string.make_sticker_set_title)) },
        singleLine = true,
    )
    TextField(
        value = draft.name,
        onValueChange = { onEvent(AddToSetEvent.Name(it)) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !sheet.busy,
        textStyle = MaterialTheme.typography.bodyLarge.numericContent,
        label = { Text(stringResource(R.string.make_sticker_set_link)) },
        prefix = {
            Text(
                stringResource(R.string.make_sticker_link_prefix),
                style = MaterialTheme.typography.bodyLarge.numericContent,
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
    )
    NameCheck(draft)
    SheetAction(
        sheet = sheet,
        label = stringResource(R.string.make_sticker_create_set),
        enabled = draft.title.isNotBlank() && draft.check == StickerSetNameCheck.Available,
        onClick = { onEvent(AddToSetEvent.Create) },
    )
    TextButton(
        onClick = { onEvent(AddToSetEvent.Back) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !sheet.busy,
        shapes = ButtonDefaults.shapes(),
    ) { Text(stringResource(R.string.make_sticker_back)) }
}

/** What Telegram says of the link: checking in `onSurfaceVariant`, free in `primary`, taken or malformed in `error`. */
@Composable
private fun NameCheck(draft: NewSetDraft) {
    if (draft.name.isEmpty() && draft.title.isBlank()) return
    val (text, color) =
        when (draft.check) {
            null -> {
                stringResource(R.string.make_sticker_link_checking) to MaterialTheme.colorScheme.onSurfaceVariant
            }

            StickerSetNameCheck.Available -> {
                stringResource(R.string.make_sticker_link_free) to
                    MaterialTheme.colorScheme.primary
            }

            StickerSetNameCheck.Occupied -> {
                stringResource(R.string.make_sticker_link_taken) to
                    MaterialTheme.colorScheme.error
            }

            StickerSetNameCheck.Invalid -> {
                stringResource(R.string.make_sticker_link_invalid) to
                    MaterialTheme.colorScheme.error
            }
        }
    Text(
        text = text,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = color,
        style = MaterialTheme.typography.labelLargeEmphasized,
    )
}

/**
 * Telegram has the sticker: a check on a `MaterialShapes.Sunny` badge in `primaryContainer`, "Added to Office cats"
 * in `headlineSmallEmphasized` (a heading) over the set's link (left to right) in `bodyMedium` `onSurfaceVariant`,
 * then "Share link" (`FilledTonalButton`) and "Done" (a default `Button`) sharing the row.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DoneStep(
    sheet: AddToSetState,
    onEvent: (AddToSetEvent) -> Unit,
) {
    val set = sheet.added ?: return
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(MaterialTheme.sizes.headlineBadge)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Sunny.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(UiR.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.make_sticker_added, set.title),
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmallEmphasized.userContent,
        )
        Text(
            text = stringResource(R.string.make_sticker_link_prefix) + set.name,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.numericContent,
        )
    }
    SheetButtons {
        FilledTonalButton(
            onClick = { onEvent(AddToSetEvent.Share) },
            modifier = Modifier.weight(1f),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(
                painterResource(UiR.drawable.ic_share),
                contentDescription = null,
                Modifier.size(ButtonDefaults.IconSize),
            )
            Text(stringResource(R.string.make_sticker_share), Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
        Button(
            onClick = { onEvent(AddToSetEvent.Done) },
            modifier = Modifier.weight(1f),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.make_sticker_done), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

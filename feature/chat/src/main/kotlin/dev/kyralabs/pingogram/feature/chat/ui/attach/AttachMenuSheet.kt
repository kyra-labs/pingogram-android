package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The composer's attach menu (`REQUIREMENTS.md` FR-C5; `docs/DESIGN_SYSTEM.md` §6 "Attach menu"): a [ModalBottomSheet]
 * with a [ButtonGroup] of the [options], each a filled tonal icon button in the Expressive shapes that morph while
 * pressed, its label below, the pressed one growing on the group's spring ([ButtonGroup]'s `animateWidth`). Where they
 * do not fit (narrow windows, large font scales), the last ones go into the group's overflow menu, never truncated.
 *
 * Choosing one hides the sheet on its spring, then [onPick] gets it. Dragging the sheet down, tapping the
 * scrim and the back gesture (Material's predictive back) dismiss it ([onDismiss]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachMenuSheet(
    options: ImmutableList<AttachOption>,
    onPick: (AttachOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val currentOnPick by rememberUpdatedState(onPick)
    val choose: (AttachOption) -> Unit =
        remember(sheetState, scope) {
            { option -> scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnPick(option) } }
        }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AttachOptions(
            options = options,
            onPick = choose,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.large)
                    .padding(bottom = MaterialTheme.spacing.extraLarge),
        )
    }
}

/** The [options] as a [ButtonGroup], centred; the ones that do not fit go into its overflow menu. */
@Composable
internal fun AttachOptions(
    options: ImmutableList<AttachOption>,
    onPick: (AttachOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val moreLabel = stringResource(R.string.attach_more)
    Box(modifier, contentAlignment = Alignment.Center) {
        ButtonGroup(
            overflowIndicator = { menuState ->
                FilledTonalIconButton(
                    onClick = { menuState.show() },
                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(painterResource(UiR.drawable.ic_more_vert), contentDescription = moreLabel)
                }
            },
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            options.forEach { option ->
                customItem(
                    buttonGroupContent = {
                        val interactionSource = remember { MutableInteractionSource() }
                        AttachTile(
                            option = option,
                            onClick = { onPick(option) },
                            interactionSource = interactionSource,
                            modifier = Modifier.animateWidth(interactionSource),
                        )
                    },
                    menuContent = { menuState ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.label)) },
                            onClick = {
                                menuState.dismiss()
                                onPick(option)
                            },
                            leadingIcon = { Icon(painterResource(option.icon), contentDescription = null) },
                        )
                    },
                )
            }
        }
    }
}

/**
 * One option: a filled tonal icon button of the medium size, as wide as its label at least, which it fills as the
 * group widens it, and the label below in `labelLarge`. Screen readers hear the button by the label, once.
 */
@Composable
private fun AttachTile(
    option: AttachOption,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(option.label)
    val buttonSize = IconButtonDefaults.mediumContainerSize()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier =
                Modifier
                    .widthIn(min = buttonSize.width)
                    .fillMaxWidth()
                    .height(buttonSize.height),
            shapes = IconButtonDefaults.shapes(),
            interactionSource = interactionSource,
        ) {
            Icon(
                painter = painterResource(option.icon),
                contentDescription = label,
                modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
            )
        }
        Text(
            text = label,
            modifier = Modifier.clearAndSetSemantics {},
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private val AttachOption.label: Int
    @StringRes get() =
        when (this) {
            AttachOption.Gallery -> R.string.attach_gallery
            AttachOption.Camera -> R.string.attach_camera
            AttachOption.File -> R.string.attach_file
            AttachOption.Location -> R.string.attach_location
            AttachOption.Contact -> R.string.attach_contact
            AttachOption.Poll -> R.string.attach_poll
        }

private val AttachOption.icon: Int
    @DrawableRes get() =
        when (this) {
            AttachOption.Gallery -> UiR.drawable.ic_photo_library_filled
            AttachOption.Camera -> UiR.drawable.ic_photo_camera_filled
            AttachOption.File -> UiR.drawable.ic_description_filled
            AttachOption.Location -> UiR.drawable.ic_location_on_filled
            AttachOption.Contact -> UiR.drawable.ic_person_filled
            AttachOption.Poll -> UiR.drawable.ic_poll_filled
        }

@PingogramPreviews
@Composable
private fun AttachOptionsPreview() {
    PingogramPreview {
        AttachOptions(
            options = AttachOption.entries.toImmutableList(),
            onPick = {},
            modifier = Modifier.padding(MaterialTheme.spacing.large),
        )
    }
}

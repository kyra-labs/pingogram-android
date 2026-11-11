package dev.kyralabs.pingogram.feature.media.stickermaker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R

/**
 * The sheet that adds the sticker just made to a set (board 14b): a `ModalBottomSheet` that opens expanded and
 * scrolls, at most `contentMaxWidth`. Its steps crossfade on the effects springs (`SizeTransform` on
 * `defaultSpatialSpec`): choosing the emoji and the set ([ChooseStep]), a new set's name and link ([NewSetStep]), and
 * once Telegram has it ([DoneStep]). The scrim, dragging and back close it and show the editor again, except while
 * Telegram answers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToSetSheet(
    sheet: AddToSetState,
    onEvent: (MakeStickerEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val sheetState =
        rememberBottomSheetState(
            SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            confirmValueChange = { it != SheetValue.Hidden || !sheet.busy },
        )
    ModalBottomSheet(
        onDismissRequest = { currentOnEvent(MakeStickerEvent.Sheet(AddToSetEvent.Dismiss)) },
        sheetState = sheetState,
    ) {
        AddToSetContent(sheet, onEvent = { onEvent(MakeStickerEvent.Sheet(it)) })
    }
}

/** The sheet's content: [AddToSetSheet]. */
@Composable
internal fun AddToSetContent(
    sheet: AddToSetState,
    onEvent: (AddToSetEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        AnimatedContent(
            targetState = sheet.step,
            modifier =
                Modifier
                    .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(sizeAnimationSpec = { _, _ -> motion.defaultSpatialSpec() }))
            },
            label = "addToSetStep",
        ) { step ->
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = MaterialTheme.spacing.large)
                        .padding(bottom = MaterialTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                when (step) {
                    AddToSetStep.Choose -> ChooseStep(sheet, onEvent)
                    AddToSetStep.NewSet -> NewSetStep(sheet, onEvent)
                    AddToSetStep.Done -> DoneStep(sheet, onEvent)
                }
            }
        }
    }
}

/**
 * Under the step's fields: why the last change failed in `bodyMedium` `error` (a polite live region), a
 * `LinearWavyProgressIndicator` while Telegram answers, then the step's default `Button` across the sheet at
 * Material's medium height ([label]), a `LoadingIndicator` in its label's place meanwhile.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SheetAction(
    sheet: AddToSetState,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    sheet.error?.let { error ->
        Text(
            text = errorText(error),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (sheet.busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
    val waiting = stringResource(R.string.make_sticker_adding)
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
        enabled = enabled && !sheet.busy,
        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    ) {
        if (sheet.busy) {
            LoadingIndicator(
                Modifier
                    .size(MaterialTheme.sizes.inlineProgressIndicator)
                    .semantics { contentDescription = waiting },
            )
        } else {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

/** A row of the sheet's buttons, from the start. */
@Composable
internal fun SheetButtons(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

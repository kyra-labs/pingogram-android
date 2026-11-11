package dev.kyralabs.pingogram.feature.settings.ui.stickers.create

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.domain.model.EmojiGroup
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.account.StepText
import kotlinx.collections.immutable.ImmutableList
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Making a sticker set (`docs/DESIGN_SYSTEM.md` §6 "New sticker set", the owner's boards 21a–c), a settings page whose
 * steps crossfade on the effects springs: the name and link ([NameStep]), the stickers ([StickersStep]) and the set
 * published ([PublishedStep]). Back returns from the stickers to the name; [onShare] shares the set's link,
 * [onOpenSet] opens its sheet.
 */
@Suppress("LongParameterList") // The state, the emoji to choose from, its events and the three ways out.
@Composable
internal fun NewStickerSetScreen(
    state: NewStickerSetUiState,
    emoji: ImmutableList<EmojiGroup>,
    onEvent: (NewStickerSetEvent) -> Unit,
    onShare: (link: String) -> Unit,
    onOpenSet: (setId: Long) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    BackHandler(enabled = state.step == NewStickerSetStep.Stickers) { onEvent(NewStickerSetEvent.Back) }
    val motionScheme = MaterialTheme.motionScheme
    SettingsPageScaffold(
        title =
            if (state.step == NewStickerSetStep.Stickers) {
                state.title
            } else {
                stringResource(R.string.settings_new_sticker_set)
            },
        onBack = { onEvent(NewStickerSetEvent.Back) },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                    fadeOut(motionScheme.fastEffectsSpec()) using
                    SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() }
            },
            label = "NewStickerSetStep",
        ) { step ->
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                when (step) {
                    NewStickerSetStep.Name -> NameStep(state, onEvent)
                    NewStickerSetStep.Stickers -> StickersStep(state, onEvent)
                    NewStickerSetStep.Published -> PublishedStep(state, onEvent, onShare, onOpenSet)
                }
            }
        }
    }
    val emojiFor = state.emojiFor
    if (emojiFor != null && state.step == NewStickerSetStep.Stickers) {
        EmojiChoiceSheet(index = emojiFor, groups = emoji, onEvent = onEvent)
    }
}

/**
 * The set's title and link: "Step 1 of 2", what they are for, the title and the link name (after
 * "t.me/addstickers/", left to right) in rounded tonal fields, what Telegram says of the link ([NameStatus]) and its
 * rules, then "Next" at the end.
 */
@Composable
private fun ColumnScope.NameStep(
    state: NewStickerSetUiState,
    onEvent: (NewStickerSetEvent) -> Unit,
) {
    StepText(stringResource(R.string.settings_new_sticker_set_step_name))
    StepText(stringResource(R.string.settings_new_sticker_set_name_text))
    TextField(
        value = state.title,
        onValueChange = { onEvent(NewStickerSetEvent.TitleChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge.userContent,
        label = { Text(stringResource(R.string.settings_new_sticker_set_title_label)) },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
    TextField(
        value = state.name,
        onValueChange = { onEvent(NewStickerSetEvent.NameChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge.numericContent,
        label = { Text(stringResource(R.string.settings_new_sticker_set_link_label)) },
        prefix = { Text(stringResource(R.string.settings_new_sticker_set_link_prefix)) },
        isError = state.nameCheck.isError,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
    NameStatus(state)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(
            onClick = { onEvent(NewStickerSetEvent.Next) },
            enabled = state.canGoOn,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(
                stringResource(R.string.settings_new_sticker_set_next),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

private val NameCheck.isError: Boolean
    get() = this is NameCheck.Occupied || this is NameCheck.Invalid || this is NameCheck.Failed

/** What Telegram says of the link, a polite live region (a check before "… is available"), over its rules. */
@Composable
private fun NameStatus(state: NewStickerSetUiState) {
    val colors = MaterialTheme.colorScheme
    val check = state.nameCheck
    val text =
        when (check) {
            NameCheck.None -> {
                null
            }

            NameCheck.Checking -> {
                stringResource(R.string.settings_new_sticker_set_checking)
            }

            NameCheck.Available -> {
                stringResource(
                    R.string.settings_new_sticker_set_available,
                    state.name.bidiIsolated(),
                )
            }

            NameCheck.Occupied -> {
                stringResource(R.string.settings_new_sticker_set_occupied)
            }

            NameCheck.Invalid -> {
                stringResource(R.string.settings_new_sticker_set_invalid)
            }

            is NameCheck.Failed -> {
                errorText(check.error)
            }
        }
    Column(
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        if (text != null) StatusLine(text, check)
        Text(
            text = stringResource(R.string.settings_new_sticker_set_link_rules),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** What Telegram says of the link, with a check or an error symbol, a polite live region. */
@Composable
private fun StatusLine(
    text: String,
    check: NameCheck,
) {
    val colors = MaterialTheme.colorScheme
    val color =
        when {
            check.isError -> colors.error
            check == NameCheck.Available -> colors.primary
            else -> colors.onSurfaceVariant
        }
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (check == NameCheck.Available || check.isError) {
            Icon(
                painter = painterResource(if (check.isError) UiR.drawable.ic_error else UiR.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
                tint = color,
            )
        }
        Text(text, color = color, style = MaterialTheme.typography.labelLargeEmphasized)
    }
}

/**
 * The set published: a check on a `MaterialShapes.Sunny` badge in `primaryContainer`, "Office cats is live" in
 * `headlineSmallEmphasized` (a heading), its sticker count and link, its first stickers, then "Share link" (a default
 * [Button]), "Open set" (a [FilledTonalButton]) and "Done".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnScope.PublishedStep(
    state: NewStickerSetUiState,
    onEvent: (NewStickerSetEvent) -> Unit,
    onShare: (link: String) -> Unit,
    onOpenSet: (setId: Long) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Box(
            modifier =
                Modifier
                    .size(MaterialTheme.sizes.avatarLarge)
                    .clip(MaterialShapes.Sunny.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.headlineBadge / 2),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.settings_new_sticker_set_published_title, state.title.bidiIsolated()),
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmallEmphasized.userContent,
        )
        Text(
            text =
                pluralStringResource(
                    R.plurals.settings_new_sticker_set_published_text,
                    state.stickers.size,
                    numbers.format(state.stickers.size),
                    "t.me/addstickers/${state.name}".bidiIsolated(),
                ),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        StickerStrip(state.stickers)
    }
    PublishedActions(state, onEvent, onShare, onOpenSet)
}

/** "Share link", "Open set" and "Done", at the end of the column. */
@Composable
private fun PublishedActions(
    state: NewStickerSetUiState,
    onEvent: (NewStickerSetEvent) -> Unit,
    onShare: (link: String) -> Unit,
    onOpenSet: (setId: Long) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.medium),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Button(onClick = { onShare(state.link) }, shapes = ButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(UiR.drawable.ic_share),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Text(
                text = stringResource(R.string.settings_new_sticker_set_share),
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            TextButton(onClick = { onEvent(NewStickerSetEvent.Back) }, shapes = ButtonDefaults.shapes()) {
                Text(
                    stringResource(R.string.settings_new_sticker_set_done),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            state.published?.let { setId ->
                FilledTonalButton(onClick = { onOpenSet(setId) }, shapes = ButtonDefaults.shapes()) {
                    Text(
                        stringResource(R.string.settings_new_sticker_set_open),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

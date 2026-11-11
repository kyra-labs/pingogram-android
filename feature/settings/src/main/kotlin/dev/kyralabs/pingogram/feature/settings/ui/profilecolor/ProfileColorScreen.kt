package dev.kyralabs.pingogram.feature.settings.ui.profilecolor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.AccentColorOption
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.SenderHue
import dev.kyralabs.pingogram.core.ui.theme.SenderHues
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.rememberMorphShape
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsFooter
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * "Your color" (`docs/DESIGN_SYSTEM.md` §6 "Your color", the owner's board 22b), a settings page with "Save" in its top
 * bar: a preview of the chosen color ([ColorPreview]), the built-in colors to pick ([ColorChip], one radio group), then
 * the colors that come with Telegram Premium, as information ([PremiumColors]).
 */
@Composable
internal fun ProfileColorScreen(
    state: ProfileColorUiState,
    onEvent: (ProfileColorEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val content = state as? ProfileColorUiState.Content
    SettingsPageScaffold(
        title = stringResource(R.string.settings_profile_color),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = {
            Button(
                onClick = { onEvent(ProfileColorEvent.Save) },
                enabled = content?.canSave == true,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(end = MaterialTheme.spacing.small),
            ) {
                Text(
                    stringResource(R.string.settings_profile_color_save),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
    ) {
        if (content == null) {
            SettingsLoading(stringResource(R.string.settings_profile_color_loading))
        } else {
            ColorPreview(content)
            NameColors(content, onEvent)
            if (content.premiumColors.isNotEmpty()) PremiumColors(content)
        }
    }
}

/** The hue a color is drawn in: the built-in color it is drawn like, in this theme's sender hues. */
@Composable
private fun hueOf(
    builtInId: Int,
    userId: Long,
): SenderHue = MaterialTheme.pingogramColors.senderHues[SenderHues.indexOf(builtInId, userId)]

/**
 * A preview on the chosen hue's tinted fill with `extraLarge` corners: the user's avatar and name, then a sample
 * incoming bubble with their name in the hue's ink, as a group shows it; one description for screen readers.
 */
@Composable
private fun ColorPreview(state: ProfileColorUiState.Content) {
    val hue = hueOf(state.chosen, state.userId)
    val spacing = MaterialTheme.spacing
    val description = stringResource(R.string.settings_profile_color_preview, state.name)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(hue.tinted.container)
                .padding(spacing.large)
                .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(photo = state.photo, name = state.name, id = state.userId, size = MaterialTheme.sizes.avatarMedium)
            Text(
                text = state.name,
                color = hue.tinted.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLargeEmphasized.userContent,
            )
        }
        Column(
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = spacing.medium, vertical = spacing.small),
        ) {
            Text(
                text = state.name,
                color = hue.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLargeEmphasized.userContent,
            )
            Text(
                text = stringResource(R.string.settings_profile_color_sample),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** "Name color": the built-in colors as chips in a segment, one radio group, a `SegmentTick` per pick. */
@Composable
private fun NameColors(
    state: ProfileColorUiState.Content,
    onEvent: (ProfileColorEvent) -> Unit,
) {
    SettingsGroup(
        title = stringResource(R.string.settings_profile_color_name),
        footer = stringResource(R.string.settings_profile_color_footer),
    ) {
        item("colors") { shapes ->
            SettingsSegment(shapes) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    state.colors.forEach { option ->
                        ColorChip(
                            option = option,
                            userId = state.userId,
                            selected = option.id == state.chosen,
                            enabled = !state.isSaving,
                            onClick = { onEvent(ProfileColorEvent.Choose(option.id)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A built-in color: a `sizes.themeChip` circle in the hue's vivid fill that morphs into `Cookie9Sided` with a check in
 * its text color once chosen, named for screen readers ("Blue").
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColorChip(
    option: AccentColorOption,
    userId: Long,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val hue = hueOf(option.builtInId, userId)
    val shape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, morphed = selected)
    val haptics = LocalHapticFeedback.current
    val name = stringResource(colorName(option.builtInId))
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.themeChip)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                }.semantics { contentDescription = name }
                .clip(shape)
                .background(hue.vivid.container),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(UiR.drawable.ic_check),
                contentDescription = null,
                tint = hue.vivid.content,
            )
        }
    }
}

/**
 * The colors that come with Telegram Premium, as information, never an offer: a lock and "Premium colors", the colors
 * as dimmed circles with a lock badge (each "Premium color" for screen readers), and what they are in `bodySmall`.
 */
@Composable
private fun PremiumColors(state: ProfileColorUiState.Content) {
    val label = stringResource(R.string.settings_profile_color_premium_one)
    SettingsGroup(
        title = stringResource(R.string.settings_profile_color_premium),
        footer = stringResource(R.string.settings_profile_color_premium_footer),
    ) {
        item("premium") { shapes ->
            SettingsSegment(shapes) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    state.premiumColors.forEach { option ->
                        val hue = hueOf(option.builtInId, state.userId)
                        Box(Modifier.size(MaterialTheme.sizes.themeChip).semantics { contentDescription = label }) {
                            Box(
                                Modifier
                                    .size(MaterialTheme.sizes.themeChip)
                                    .alpha(DIMMED)
                                    .background(hue.vivid.container, CircleShape),
                            )
                            Icon(
                                painter = painterResource(UiR.drawable.ic_lock),
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(MaterialTheme.sizes.avatarBadge)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                                        .padding(MaterialTheme.spacing.extraSmall),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The name of built-in color [builtInId]: Telegram's seven, in its order. */
private fun colorName(builtInId: Int): Int = ColorNames.getOrElse(builtInId) { ColorNames.last() }

private val ColorNames =
    listOf(
        R.string.settings_profile_color_red,
        R.string.settings_profile_color_orange,
        R.string.settings_profile_color_violet,
        R.string.settings_profile_color_green,
        R.string.settings_profile_color_cyan,
        R.string.settings_profile_color_blue,
        R.string.settings_profile_color_pink,
    )

/** How much of a Premium color shows: Material's disabled content opacity. */
private const val DIMMED = 0.38f

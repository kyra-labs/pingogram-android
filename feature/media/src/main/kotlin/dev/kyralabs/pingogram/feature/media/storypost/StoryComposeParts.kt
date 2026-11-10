package dev.kyralabs.pingogram.feature.media.storypost

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import dev.kyralabs.pingogram.core.domain.model.StoryPostAbility
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/** A formatting the caption's bar sets, with its name and symbol. */
private enum class CaptionFormat(
    val type: TextEntityType?,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    Bold(TextEntityType.Bold, R.string.new_story_bold, UiR.drawable.ic_format_bold),
    Italic(TextEntityType.Italic, R.string.new_story_italic, UiR.drawable.ic_format_italic),

    /** A link: its address is asked for first. */
    Link(null, R.string.new_story_link, UiR.drawable.ic_link),
    Spoiler(TextEntityType.Spoiler, R.string.new_story_spoiler, UiR.drawable.ic_visibility_off),
}

/**
 * The caption's formatting bar: bold, italic, link and spoiler as connected `ToggleButton`s, checked where the
 * selection has them everywhere, toggling them over the selection (`ToggleOn` / `ToggleOff`); "Link" asks for the
 * address in a dialog. Screen readers hear a group, "Formatting", of toggles.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FormattingBar(caption: CaptionText) {
    val haptics = LocalHapticFeedback.current
    var linking by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.new_story_formatting)
    Row(
        Modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        CaptionFormat.entries.forEachIndexed { index, format ->
            val type = format.type
            val checked = if (type == null) caption.linkAt() != null else caption.has(type)
            ToggleButton(
                checked = checked,
                onCheckedChange = { on ->
                    haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    if (type == null) linking = true else caption.toggle(type)
                },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        CaptionFormat.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
            ) {
                Icon(painterResource(format.icon), stringResource(format.label))
            }
        }
    }
    if (linking) {
        LinkDialog(
            current = caption.linkAt(),
            onApply = { url ->
                caption.setLink(url)
                linking = false
            },
            onDismiss = { linking = false },
        )
    }
}

/** Asks for a link's address: "Apply", "Remove" where the selection is linked, and "Cancel". */
@Composable
private fun LinkDialog(
    current: String?,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val address = rememberTextFieldState(current.orEmpty())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_story_link_title)) },
        text = {
            TextField(
                state = address,
                label = { Text(stringResource(R.string.new_story_link_address)) },
                textStyle = MaterialTheme.typography.bodyLarge.numericContent,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = TextFieldDefaults.roundedShape,
                colors = TextFieldDefaults.tonalColors(),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(address.text.toString()) },
            ) { Text(stringResource(R.string.new_story_link_apply)) }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onApply("") }) { Text(stringResource(R.string.new_story_link_remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.new_story_link_cancel)) }
            }
        },
    )
}

/**
 * Who the story is for, an `AssistChip` on `secondaryContainer` with the audience's symbol that opens an expressive
 * menu of the three, and how long it stays, a label on `surfaceContainerHigh`: other periods need Telegram Premium.
 */
@Composable
internal fun AudienceRow(
    compose: StoryComposeState,
    onEvent: (NewStoryEvent) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val audience = compose.audience
    val name = stringResource(audience.label)
    val description = stringResource(R.string.new_story_audience_a11y, name)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AssistChip(
                onClick = { open = true },
                label = { Text(name) },
                leadingIcon = {
                    Icon(
                        painterResource(audience.icon),
                        null,
                        Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                trailingIcon = {
                    Icon(
                        painterResource(UiR.drawable.ic_keyboard_arrow_down),
                        null,
                        Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                colors =
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        trailingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                border = null,
                modifier = Modifier.semantics { contentDescription = description },
            )
            AudienceMenu(open, audience, onDismiss = { open = false }) { chosen ->
                open = false
                onEvent(NewStoryEvent.SetAudience(chosen))
            }
        }
        StaysLabel()
    }
}

/** The three audiences in an expressive menu, the chosen one marked and selected for screen readers. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AudienceMenu(
    expanded: Boolean,
    chosen: StoryAudience,
    onDismiss: () -> Unit,
    onChoose: (StoryAudience) -> Unit,
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
            StoryAudience.entries.forEachIndexed { index, audience ->
                DropdownMenuItem(
                    onClick = { onChoose(audience) },
                    text = { Text(stringResource(audience.label)) },
                    shape = MenuDefaults.itemShape(index, StoryAudience.entries.size).shape,
                    leadingIcon = { Icon(painterResource(audience.icon), contentDescription = null) },
                    trailingContent = {
                        if (audience == chosen) Icon(painterResource(UiR.drawable.ic_check), contentDescription = null)
                    },
                    modifier = Modifier.semantics { selected = audience == chosen },
                )
            }
        }
    }
}

/** "Stays for 24 h": the one period Telegram gives everyone, so nothing to choose. */
@Composable
private fun StaysLabel() {
    val text = stringResource(R.string.new_story_stays)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
        modifier = Modifier.clearAndSetSemantics { contentDescription = text },
    ) {
        Row(
            Modifier.padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(UiR.drawable.ic_schedule), null, Modifier.size(AssistChipDefaults.IconSize))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** "Shown to 14 close friends · 1 of 4 items". */
@Composable
internal fun summaryText(
    state: NewStoryUiState,
    compose: StoryComposeState,
): String {
    val audience =
        when (compose.audience) {
            StoryAudience.Everyone -> {
                stringResource(R.string.new_story_shown_to_everyone)
            }

            StoryAudience.Contacts -> {
                stringResource(R.string.new_story_shown_to_contacts)
            }

            StoryAudience.CloseFriends -> {
                compose.closeFriends?.let { pluralStringResource(R.plurals.new_story_shown_to_close_friends, it, it) }
                    ?: stringResource(R.string.new_story_shown_to_close_friends_unknown)
            }
        }
    val count = state.items.size
    val position = pluralStringResource(R.plurals.new_story_item_of, count, state.items.indexOf(state.shown) + 1, count)
    return stringResource(R.string.new_story_summary, audience, position)
}

/**
 * Why Telegram does not let the user post now, in their words; "Checking…" while it is asked; null when they may.
 * Never an offer of Telegram Premium (`CLAUDE.md` rule 3).
 */
@Composable
internal fun blockedText(compose: StoryComposeState): String? =
    when (val ability = compose.ability) {
        null -> {
            stringResource(R.string.new_story_checking)
        }

        is StoryPostAbility.Allowed -> {
            null
        }

        StoryPostAbility.TooManyActive -> {
            stringResource(R.string.new_story_limit_active)
        }

        is StoryPostAbility.WeeklyLimit -> {
            stringResource(R.string.new_story_limit_weekly, durationText(ability.retryAfterSeconds.toLong()))
        }

        is StoryPostAbility.MonthlyLimit -> {
            stringResource(R.string.new_story_limit_monthly, durationText(ability.retryAfterSeconds.toLong()))
        }

        StoryPostAbility.PremiumNeeded -> {
            stringResource(R.string.new_story_limit_premium)
        }

        StoryPostAbility.LiveStoryActive -> {
            stringResource(R.string.new_story_limit_live)
        }

        StoryPostAbility.BoostNeeded, StoryPostAbility.NotAllowed -> {
            stringResource(R.string.new_story_limit_other)
        }
    }

/** The audience's name and symbol. */
private val StoryAudience.label: Int
    get() =
        when (this) {
            StoryAudience.Everyone -> R.string.new_story_audience_everyone
            StoryAudience.Contacts -> R.string.new_story_audience_contacts
            StoryAudience.CloseFriends -> R.string.new_story_audience_close_friends
        }

private val StoryAudience.icon: Int
    get() =
        when (this) {
            StoryAudience.Everyone -> UiR.drawable.ic_public
            StoryAudience.Contacts -> UiR.drawable.ic_contacts
            StoryAudience.CloseFriends -> UiR.drawable.ic_favorite_filled
        }

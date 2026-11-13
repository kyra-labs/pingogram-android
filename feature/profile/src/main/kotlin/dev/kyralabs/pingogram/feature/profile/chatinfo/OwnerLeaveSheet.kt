package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The owner's way out of their group or channel (`docs/DESIGN_SYSTEM.md` §6 "Leaving as the owner"): a
 * `ModalBottomSheet` that opens expanded, [OwnerLeaveContent]. While Telegram answers it stays; otherwise back,
 * dragging and the scrim close it without leaving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OwnerLeaveBottomSheet(
    sheet: OwnerLeaveSheet,
    title: String,
    password: TextFieldState,
    onEvent: (OwnerLeaveEvent) -> Unit,
) {
    val isBusy by rememberUpdatedState(sheet.isBusy)
    val sheetState =
        rememberBottomSheetState(
            SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            confirmValueChange = { it != SheetValue.Hidden || !isBusy },
        )
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(OwnerLeaveEvent.Dismiss) }, sheetState = sheetState) {
        OwnerLeaveContent(sheet, title, password, onEvent)
    }
}

/**
 * What the sheet shows, its two steps crossfading on the effects springs as the sheet resizes on `defaultSpatialSpec`:
 * - **Choosing**: "Leave Book club?" in `titleLargeEmphasized` (a heading) over why in `bodyMedium`
 *   `onSurfaceVariant`; "Next owner" in `titleSmallEmphasized` `primary` (a heading) over one radio group of rows inset
 *   as the chat list's: "Let Telegram choose" (the group symbol in a `secondaryContainer` circle) over who Telegram
 *   picks and when, then each administrator (40 dp avatar, the name as user content); the chosen row `large` in
 *   `secondaryContainer`, the group symbol's circle then `primary`; a note that only administrators can be chosen;
 *   then "Cancel" and "Leave" ("Transfer and leave" with someone chosen) in `error` / `onError`.
 * - **The password**: "Make Ada the owner?", what changes, the 2-step verification password in a filled
 *   [SecureTextField] with why it was refused below it (a polite live region, a `Reject` haptic), then "Back" and
 *   "Transfer and leave".
 *
 * A `LinearWavyProgressIndicator` shows while Telegram answers, the buttons waiting. [focusPassword] false leaves the
 * password's field without the focus, for goldens without a blinking cursor.
 */
@Composable
internal fun OwnerLeaveContent(
    sheet: OwnerLeaveSheet,
    title: String,
    password: TextFieldState,
    onEvent: (OwnerLeaveEvent) -> Unit,
    modifier: Modifier = Modifier,
    focusPassword: Boolean = true,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = sheet.step,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
        },
        label = "ownerLeaveStep",
    ) { step ->
        val spacing = MaterialTheme.spacing
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.large)
                    .padding(bottom = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            when (step) {
                OwnerLeaveStep.Choose -> ChooseStep(sheet, title, onEvent)
                OwnerLeaveStep.Password -> PasswordStep(sheet, password, onEvent, focusPassword)
            }
            SavingIndicator(sheet.isBusy)
            SheetButtons(sheet, password, onEvent)
        }
    }
}

@Composable
private fun ChooseStep(
    sheet: OwnerLeaveSheet,
    title: String,
    onEvent: (OwnerLeaveEvent) -> Unit,
) {
    val isChannel = sheet.kind == GroupKind.Channel
    Heading(
        stringResource(R.string.chat_info_owner_leave_title, title.bidiIsolated()).bidiIsolated(),
        stringResource(
            if (isChannel) R.string.chat_info_owner_leave_text_channel else R.string.chat_info_owner_leave_text_group,
        ),
    )
    Text(
        text = stringResource(R.string.chat_info_owner_leave_next),
        modifier = Modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmallEmphasized,
    )
    Column(Modifier.selectableGroup()) {
        AutoRow(sheet, onEvent)
        val candidates = sheet.candidates
        if (candidates == null) {
            val description = stringResource(R.string.chat_info_loading)
            Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), contentAlignment = Alignment.Center) {
                DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
            }
        } else {
            candidates.forEach { candidate ->
                val userId = (candidate.sender as? MessageSender.User)?.userId ?: return@forEach
                ChoiceRow(
                    selected = sheet.chosen == userId,
                    enabled = !sheet.isBusy,
                    onClick = { onEvent(OwnerLeaveEvent.Choose(userId)) },
                    headline = candidate.name.resolve(),
                    supporting = candidate.role?.resolve(),
                    isUserContent = true,
                ) {
                    Avatar(
                        photo = candidate.photo,
                        name = candidate.name.resolve(),
                        id = candidate.avatarId,
                        size = MaterialTheme.sizes.avatarSmall,
                        placeholderIcon = candidate.placeholderIcon,
                    )
                }
            }
        }
    }
    Text(
        text =
            stringResource(
                if (sheet.candidates?.isEmpty() == true) {
                    R.string.chat_info_owner_leave_no_admins
                } else {
                    R.string.chat_info_owner_leave_admins_note
                },
            ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** "Let Telegram choose", over who Telegram makes the owner and when. */
@Composable
private fun AutoRow(
    sheet: OwnerLeaveSheet,
    onEvent: (OwnerLeaveEvent) -> Unit,
) {
    val now = sheet.kind == GroupKind.BasicGroup
    val heir = sheet.heir?.resolve()
    val supporting =
        when {
            heir == null && now -> stringResource(R.string.chat_info_owner_leave_auto_now_unknown)
            heir == null -> stringResource(R.string.chat_info_owner_leave_auto_later_unknown)
            now -> stringResource(R.string.chat_info_owner_leave_auto_now, heir.bidiIsolated())
            else -> stringResource(R.string.chat_info_owner_leave_auto_later, heir.bidiIsolated())
        }
    val selected = sheet.chosen == 0L
    val colors = MaterialTheme.colorScheme
    ChoiceRow(
        selected = selected,
        enabled = !sheet.isBusy,
        onClick = { onEvent(OwnerLeaveEvent.Choose(0L)) },
        headline = stringResource(R.string.chat_info_owner_leave_auto),
        supporting = supporting,
        isUserContent = false,
    ) {
        Box(
            modifier =
                Modifier
                    .size(MaterialTheme.sizes.avatarSmall)
                    .background(if (selected) colors.primary else colors.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(UiR.drawable.ic_group),
                contentDescription = null,
                tint = if (selected) colors.onPrimary else colors.onSecondaryContainer,
            )
        }
    }
}

/** A row of the radio group: its picture, its text, and the radio button at its end; `large` when chosen. */
@Suppress("LongParameterList") // A row's parts.
@Composable
private fun ChoiceRow(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    headline: String,
    supporting: String?,
    isUserContent: Boolean,
    leading: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyLarge
    val textStyle = if (isUserContent) body.userContent else body
    ListItem(
        headlineContent = { Text(headline, maxLines = 2, overflow = TextOverflow.Ellipsis, style = textStyle) },
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.large)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = leading,
        trailingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        colors =
            ListItemDefaults.colors(
                containerColor = if (selected) colors.secondaryContainer else Color.Transparent,
            ),
    )
}

@Composable
private fun PasswordStep(
    sheet: OwnerLeaveSheet,
    password: TextFieldState,
    onEvent: (OwnerLeaveEvent) -> Unit,
    focus: Boolean,
) {
    val name = (sheet.chosenItem?.name?.resolve() ?: "").bidiIsolated()
    val haptics = LocalHapticFeedback.current
    val requester = remember { FocusRequester() }
    // The password is what the step asks for: its field takes the focus, and the keyboard comes up.
    LaunchedEffect(requester) { if (focus) requester.requestFocus() }
    LaunchedEffect(sheet.error) {
        if (sheet.error != null) haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }
    Heading(
        stringResource(R.string.chat_info_owner_leave_password_title, name).bidiIsolated(),
        stringResource(
            if (sheet.kind == GroupKind.Channel) {
                R.string.chat_info_owner_leave_password_text_channel
            } else {
                R.string.chat_info_owner_leave_password_text_group
            },
            name,
        ),
    )
    SecureTextField(
        state = password,
        modifier = Modifier.fillMaxWidth().focusRequester(requester),
        enabled = !sheet.isBusy,
        label = { Text(stringResource(R.string.chat_info_transfer_password)) },
        isError = sheet.error != null,
        supportingText =
            sheet.error?.let { error ->
                { Text(errorText(error), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        onKeyboardAction = { onEvent(OwnerLeaveEvent.Confirm) },
    )
}

/** The step's title, a heading, over what it means. */
@Composable
private fun Heading(
    title: String,
    text: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** "Cancel" or "Back" at the start, the destructive action at the end. */
@Composable
private fun SheetButtons(
    sheet: OwnerLeaveSheet,
    password: TextFieldState,
    onEvent: (OwnerLeaveEvent) -> Unit,
) {
    val isPassword = sheet.step == OwnerLeaveStep.Password
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { onEvent(if (isPassword) OwnerLeaveEvent.Back else OwnerLeaveEvent.Dismiss) },
            enabled = !sheet.isBusy,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(
                stringResource(
                    if (isPassword) R.string.chat_info_owner_leave_back else R.string.profile_dialog_dismiss,
                ),
            )
        }
        val colors = MaterialTheme.colorScheme
        Button(
            onClick = { onEvent(if (isPassword) OwnerLeaveEvent.Confirm else OwnerLeaveEvent.Continue) },
            enabled =
                !sheet.isBusy && (!isPassword || password.text.isNotEmpty()) &&
                    (sheet.chosen == 0L || sheet.chosenItem != null),
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
        ) {
            Text(
                text =
                    stringResource(
                        if (sheet.chosen == 0L) {
                            R.string.chat_info_action_leave
                        } else {
                            R.string.chat_info_owner_leave_transfer
                        },
                    ),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

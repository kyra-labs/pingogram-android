package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.time.BirthdateDialog
import dev.kyralabs.pingogram.core.ui.time.birthdateText
import dev.kyralabs.pingogram.feature.settings.R

// Edit profile's birthday (FR-S1, Account): the date, who may see it, and the dialog that picks it.

/**
 * The [birthdate] in a row ending in an "Add" or "Change" [TextButton] that opens `core:ui`'s [BirthdateDialog] (the
 * dialog's "Remove" removes it), then, with a birthday, "Who can see it" with [privacy]'s choice, which opens the
 * privacy setting ([onOpenPrivacy]). The change is saved with the rest of the page ([onPick] only edits it).
 */
@Composable
internal fun BirthdaySection(
    birthdate: Birthdate?,
    privacy: PrivacyRules?,
    enabled: Boolean,
    onPick: (Birthdate?) -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    FieldGroup(title = stringResource(R.string.settings_section_profile_birthday)) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            val count = if (birthdate == null) 1 else 2
            BirthdateRow(birthdate, enabled, count, onEdit = { picking = true })
            if (birthdate != null) {
                SettingsRow(
                    title = stringResource(R.string.settings_edit_profile_birthday_privacy),
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = count),
                    supportingContent = privacy?.let { { Text(privacySummary(it)) } },
                    onClick = onOpenPrivacy,
                )
            }
        }
    }
    if (picking) {
        BirthdateDialog(
            birthdate = birthdate,
            onDismiss = { picking = false },
            onPick = {
                picking = false
                onPick(it)
            },
            onRemove = {
                picking = false
                onPick(null)
            },
        )
    }
}

/**
 * The [birthdate] ("No birthday" without one) with "Add" or "Change" ([onEdit]) at the end; the first of [count] rows.
 */
@Composable
private fun BirthdateRow(
    birthdate: Birthdate?,
    enabled: Boolean,
    count: Int,
    onEdit: () -> Unit,
) {
    val text = birthdate?.let { birthdateText(it) }
    val label =
        if (text == null) {
            stringResource(R.string.settings_edit_profile_birthday_add_label)
        } else {
            stringResource(R.string.settings_edit_profile_birthday_change_label, text.bidiIsolated())
        }
    val action =
        if (text ==
            null
        ) {
            R.string.settings_edit_profile_birthday_add
        } else {
            R.string.settings_edit_profile_birthday_change
        }
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
        colors = settingsRowColors(),
        trailingContent = {
            TextButton(
                onClick = onEdit,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = label },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(action))
            }
        },
    ) {
        if (text == null) {
            Text(
                stringResource(R.string.settings_edit_profile_birthday_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(text, style = MaterialTheme.typography.bodyLarge.userContent)
        }
    }
}

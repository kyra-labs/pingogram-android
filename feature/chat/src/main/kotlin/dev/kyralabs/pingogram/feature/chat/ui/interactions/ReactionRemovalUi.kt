package dev.kyralabs.pingogram.feature.chat.ui.interactions

import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * At the end of a reaction's row where the user may remove others' reactions: a more-options `IconButton`
 * (`IconButtonDefaults.shapes()`, "Options for Ada") opening an expressive `DropdownMenuPopup` of "Remove reaction" and
 * "Remove all recent reactions", both in `error`, which ask first ([onRemove] with whether all go).
 */
@Composable
internal fun RemoveReactionsButton(
    name: String,
    onRemove: (all: Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(UiR.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.interactions_options_for, name.bidiIsolated()),
            )
        }
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                listOf(false, true).forEachIndexed { index, all ->
                    DropdownMenuItem(
                        onClick = {
                            open = false
                            onRemove(all)
                        },
                        text = {
                            Text(
                                stringResource(
                                    if (all) R.string.interactions_remove_all else R.string.interactions_remove_one,
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        shape = MenuDefaults.itemShape(index, 2).shape,
                        leadingIcon = {
                            Icon(
                                painterResource(UiR.drawable.ic_delete),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * The question before removing reactions, a destructive `AlertDialog`: "Remove Ada's reaction?" or "Remove all of
 * Ada's recent reactions?", what it does, a `LinearWavyProgressIndicator` while Telegram answers, "Cancel" and
 * "Remove" in `error`.
 */
@Composable
internal fun RemoveReactionsDialog(
    dialog: ReactionRemovalDialog,
    onEvent: (InteractionsEvent) -> Unit,
) {
    val name = dialog.name.resolve().bidiIsolated()
    AlertDialog(
        onDismissRequest = { onEvent(InteractionsEvent.DismissRemove) },
        icon = { Icon(painterResource(UiR.drawable.ic_delete), contentDescription = null) },
        title = {
            Text(
                stringResource(
                    if (dialog.all) R.string.interactions_remove_all_title else R.string.interactions_remove_one_title,
                    name,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                Text(stringResource(dialog.textRes))
                if (dialog.isRemoving) {
                    LinearWavyProgressIndicator(
                        Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.medium),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(InteractionsEvent.ConfirmRemove) },
                enabled = !dialog.isRemoving,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.interactions_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(InteractionsEvent.DismissRemove) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.interactions_remove_cancel))
            }
        },
    )
}

/** What removing does, for the question's text. */
private val ReactionRemovalDialog.textRes: Int
    get() = if (all) R.string.interactions_remove_all_text else R.string.interactions_remove_one_text

/** The snackbar of [effect], where it is one. */
internal fun removalNotice(
    resources: Resources,
    effect: InteractionsEffect,
): String? =
    when (effect) {
        is InteractionsEffect.ReactionsRemoved -> {
            resources.getString(
                if (effect.all) R.string.interactions_removed_all else R.string.interactions_removed_one,
            )
        }

        is InteractionsEffect.RemovalFailed -> {
            errorMessage(resources, effect.error, showDetails = false)
        }

        else -> {
            null
        }
    }

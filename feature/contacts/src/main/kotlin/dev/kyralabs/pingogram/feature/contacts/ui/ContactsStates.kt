package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// What the Contacts tab shows instead of its contacts (ContactsScreen): loading, none, an error, no search results.

/** No contact matches the query: a line that says so, in line with the rows' content. */
@Composable
internal fun NoResults(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.contacts_no_results),
        modifier = modifier.padding(MaterialTheme.spacing.large),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** The contacts loading: the loading indicator after a moment, centered and named for screen readers. */
@Composable
internal fun ContactsLoading(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.contacts_loading)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

/** No contacts: an empty state whose next step is adding one ([onAddContact]). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NoContacts(
    onAddContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        illustration = MaterialShapes.Clover8Leaf,
        icon = UiR.drawable.ic_contacts_filled,
        title = stringResource(R.string.contacts_empty_title),
        body = stringResource(R.string.contacts_empty_body),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        action = {
            Button(onClick = onAddContact, shapes = ButtonDefaults.shapes()) {
                Text(
                    text = stringResource(R.string.contacts_add),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
    )
}

/** Listing the contacts failed: its error in a soft-shaped empty state, with "Try again" ([onRetry]). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ContactsError(
    content: ContactsContent.Error,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        // A soft shape: errors are no alarm (`docs/DESIGN_SYSTEM.md` §4).
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.contacts_error_title),
        body = errorText(content.error),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(
                    text = stringResource(R.string.contacts_retry),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
    )
}

package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The Contacts tab until the contacts list lands (`docs/ROADMAP.md` M4): an empty state. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ContactsScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier) { innerPadding ->
        EmptyState(
            illustration = MaterialShapes.Clover8Leaf,
            icon = UiR.drawable.ic_contacts_filled,
            title = stringResource(R.string.contacts_placeholder_title),
            body = stringResource(R.string.contacts_placeholder_body),
            modifier = Modifier.padding(innerPadding),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@PingogramPreviews
@Composable
private fun ContactsScreenPreview() {
    PingogramPreview { ContactsScreen() }
}

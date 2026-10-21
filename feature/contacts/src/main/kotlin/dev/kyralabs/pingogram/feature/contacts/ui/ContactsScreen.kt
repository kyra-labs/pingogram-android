package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.screenContentWindowInsets
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Contacts tab until the contacts list lands (`docs/ROADMAP.md` M4), laid out like the Chats tab
 * (`docs/DESIGN_SYSTEM.md` §2.2, §6): a small [TopAppBar] with "Contacts" in `titleLargeEmphasized` on
 * `surfaceContainer`, and below it a rounded `surface` sheet ([ContentSheet]) with an empty state that says contacts
 * come in a later version, and offers New chat ([onOpenNewChat]), which lists them meanwhile. Like every top-level
 * screen it draws behind the floating navigation bar and keeps its content clear of it ([screenContentWindowInsets]).
 */
@Composable
internal fun ContactsScreen(
    onOpenNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        // The top bar's container; the content sits on a sheet below it (ContentSheet).
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { ContactsTopBar() },
        contentWindowInsets = screenContentWindowInsets(),
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            ContactsComingSoon(onOpenNewChat, Modifier.padding(contentPadding))
        }
    }
}

/** The small top bar, its title at the window margin (`docs/DESIGN_SYSTEM.md` §7), as the Chats tab's. */
@Composable
private fun ContactsTopBar() {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.contacts_title),
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        // The bar insets its title by the compact margin itself.
        modifier = Modifier.padding(horizontal = windowMargin() - MaterialTheme.spacing.windowMarginCompact),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
    )
}

/** Contacts arrive in a later version; until then New chat lists them ([onOpenNewChat]), the screen's next step. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContactsComingSoon(
    onOpenNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        illustration = MaterialShapes.Clover8Leaf,
        icon = UiR.drawable.ic_contacts_filled,
        title = stringResource(R.string.contacts_placeholder_title),
        body = stringResource(R.string.contacts_placeholder_body),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        action = {
            Button(onClick = onOpenNewChat, shapes = ButtonDefaults.shapes()) {
                Text(
                    text = stringResource(R.string.contacts_new_chat),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
    )
}

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun ContactsScreenPreview() {
    PingogramPreview { ContactsScreen(onOpenNewChat = {}) }
}

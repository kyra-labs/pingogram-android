package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Global search (`REQUIREMENTS.md` FR-L6) until it lands with the chat list (`docs/ROADMAP.md` M2): an empty state
 * under a top app bar whose back button returns to the tab it was opened from.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.search_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.search_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        EmptyState(
            illustration = MaterialShapes.Sunny,
            icon = UiR.drawable.ic_search,
            title = stringResource(R.string.search_placeholder_title),
            body = stringResource(R.string.search_placeholder_body),
            modifier = Modifier.padding(innerPadding),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@PingogramPreviews
@Composable
private fun SearchScreenPreview() {
    PingogramPreview { SearchScreen(onBack = {}) }
}

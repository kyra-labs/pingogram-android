package dev.kyralabs.pingogram.feature.settings.ui.passkeys

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

@PingogramPreviews
@Composable
private fun PasskeysScreenPreview() {
    PingogramPreview {
        PasskeysScreen(
            state = PasskeysPreviewStates.content,
            canAdd = true,
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun PasskeysScreenRemovingPreview() {
    PingogramPreview {
        PasskeysScreen(
            state = PasskeysPreviewStates.removing,
            canAdd = true,
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun PasskeysScreenAddingPreview() {
    PingogramPreview {
        PasskeysScreen(
            state = PasskeysPreviewStates.adding,
            canAdd = true,
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun PasskeysScreenDialogPreview() {
    PingogramPreview {
        PasskeysScreen(
            state = PasskeysPreviewStates.dialog,
            canAdd = true,
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun PasskeysScreenEmptyPreview() {
    PingogramPreview {
        PasskeysScreen(
            state = PasskeysPreviewStates.empty,
            canAdd = true,
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun PasskeysScreenOldAndroidPreview() {
    PingogramPreview {
        PasskeysScreen(
            state = PasskeysPreviewStates.content,
            canAdd = false,
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun PasskeysScreenLoadingPreview() {
    PingogramPreview {
        PasskeysScreen(
            state = PasskeysPreviewStates.loading,
            canAdd = true,
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun PasskeysScreenErrorPreview() {
    PingogramPreview {
        PasskeysScreen(
            state = PasskeysPreviewStates.error,
            canAdd = true,
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}

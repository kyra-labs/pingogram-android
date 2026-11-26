package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettings
import java.time.Instant

/** The page's states for previews and goldens: a group to leave, a chat never opened, loading, none, and off. */
internal class CleanupPreviewStates : PreviewParameterProvider<CleanupUiState> {
    override val values: Sequence<CleanupUiState> =
        sequenceOf(group, neverOpened, loading, none, off, confirmingLeave)

    companion object {
        val group =
            CleanupUiState(
                settings = CleanupSettings(),
                suggestion =
                    CleanupSuggestion(
                        chatId = -100,
                        title = "Book club",
                        photo = null,
                        type = ChatType.Supergroup(supergroupId = 100, isChannel = false),
                        lastOpened = Instant.parse("2026-03-14T18:00:00Z"),
                        neverOpened = false,
                        canLeave = true,
                    ),
                remaining = 4,
                loading = false,
            )

        val neverOpened =
            group.copy(
                suggestion =
                    group.suggestion?.copy(
                        chatId = 7,
                        title = "Ada Lovelace",
                        type = ChatType.Private(userId = 7),
                        lastOpened = Instant.parse("2026-01-02T09:00:00Z"),
                        neverOpened = true,
                        canLeave = false,
                    ),
                remaining = 0,
            )

        val loading = CleanupUiState(settings = CleanupSettings(), loading = true)

        val none = CleanupUiState(settings = CleanupSettings(months = 6), loading = false)

        val off = CleanupUiState(settings = CleanupSettings(enabled = false), loading = false)

        val confirmingLeave = group.copy(isConfirmingLeave = true)
    }
}

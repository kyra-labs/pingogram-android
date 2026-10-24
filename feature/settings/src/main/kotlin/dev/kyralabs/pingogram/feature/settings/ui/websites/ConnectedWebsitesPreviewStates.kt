package dev.kyralabs.pingogram.feature.settings.ui.websites

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** The states of the Websites page, shared by previews and screenshot tests. */
internal object ConnectedWebsitesPreviewStates {
    private val websites =
        persistentListOf(
            WebsiteItem(
                id = 1,
                domain = "example.org",
                details = UiText.Plain("Example Bot · Firefox, Linux"),
                activity = UiText.Plain("203.0.113.1 · Berlin, Germany · 9:41 AM"),
            ),
            WebsiteItem(
                id = 2,
                domain = "shop.example",
                details = UiText.Plain("Shop Login · Chrome, Windows"),
                activity = UiText.Plain("198.51.100.7 · Lisbon, Portugal · Mar 17"),
            ),
        )

    val content = ConnectedWebsitesUiState(websites = WebsitesState.Loaded(websites))

    val busy = content.copy(busyIds = persistentSetOf(2L))

    val dialog = content.copy(dialog = DisconnectDialog.Website(1, "example.org"))

    val dialogAll = content.copy(dialog = DisconnectDialog.All)

    val empty = ConnectedWebsitesUiState(websites = WebsitesState.Loaded(persistentListOf()))

    val loading = ConnectedWebsitesUiState()

    val error = ConnectedWebsitesUiState(websites = WebsitesState.Failed(DomainError.Network))
}

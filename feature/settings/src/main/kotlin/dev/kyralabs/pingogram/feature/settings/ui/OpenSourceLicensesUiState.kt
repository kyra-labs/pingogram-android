package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList

/** What the Open-source licenses page shows (`REQUIREMENTS.md` FR-S1, About). */
@Immutable
sealed interface OpenSourceLicensesUiState {
    data object Loading : OpenSourceLicensesUiState

    /** The list could not be read, because of [error]. */
    data class Error(
        val error: DomainError,
    ) : OpenSourceLicensesUiState

    /** Every library, font and icon set the app is built from, sorted by name. */
    data class Content(
        val libraries: ImmutableList<OpenSourceLibraryItem>,
    ) : OpenSourceLicensesUiState
}

/**
 * A library as its row shows it.
 *
 * @property id the library's id, the row's key and what opens its page.
 * @property version e.g. `1.5.0-beta01`, null without one.
 * @property licenses the names of its licenses, comma-separated, e.g. `Apache License 2.0`.
 */
@Immutable
data class OpenSourceLibraryItem(
    val id: String,
    val name: String,
    val version: String?,
    val licenses: String,
)

/** What the user does on the Open-source licenses page, handled by [OpenSourceLicensesViewModel.onEvent]. */
sealed interface OpenSourceLicensesEvent {
    /** Read the list again after it failed. */
    data object Retry : OpenSourceLicensesEvent
}

/** What the page of one library shows: the library, with the full texts of its licenses. */
@Immutable
sealed interface OpenSourceLibraryUiState {
    data object Loading : OpenSourceLibraryUiState

    /** The library could not be read, because of [error]; `NotFound` if this build has no library of that id. */
    data class Error(
        val error: DomainError,
    ) : OpenSourceLibraryUiState

    /**
     * The library.
     *
     * @property id its Maven `group:artifact`, or a name of its own for what is not on Maven.
     * @property isMavenArtifact whether [id] is a Maven `group:artifact`, which the page then shows.
     * @property developers who makes it, comma-separated; null if unknown.
     */
    data class Content(
        val id: String,
        val name: String,
        val version: String?,
        val isMavenArtifact: Boolean,
        val description: String?,
        val developers: String?,
        val website: String?,
        val licenses: ImmutableList<LicenseItem>,
    ) : OpenSourceLibraryUiState
}

/**
 * A license of the library on its page.
 *
 * @property id the license's id (SPDX where it has one), its key on the page.
 * @property text its full text reflowed for the screen ([reflowLicenseText]); null where the build has none.
 * @property url where it is published, null if unknown.
 */
@Immutable
data class LicenseItem(
    val id: String,
    val name: String,
    val text: String?,
    val url: String?,
)

/** What the user does on the page of a library, handled by [OpenSourceLibraryViewModel.onEvent]. */
sealed interface OpenSourceLibraryEvent {
    /** Read the library again after it failed. */
    data object Retry : OpenSourceLibraryEvent

    /** The website row was tapped. */
    data object OpenWebsite : OpenSourceLibraryEvent

    /** The link to license [licenseId] ([LicenseItem.id]) was tapped. */
    data class OpenLicense(
        val licenseId: String,
    ) : OpenSourceLibraryEvent
}

/** One-off side effects of [OpenSourceLibraryViewModel], handled once by its route. */
sealed interface OpenSourceLibraryEffect {
    /** Opens [url] in the browser. */
    data class OpenUrl(
        val url: String,
    ) : OpenSourceLibraryEffect
}

package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.OpenSourceLibrary
import dev.kyralabs.pingogram.core.domain.repository.OpenSourceLicensesRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The page of library [libraryId] (an `OpenSourceLibrary.id`): its version, artifact, developers and website, and
 * the full texts of its licenses, reflowed for the screen ([reflowLicenseText]). The website and the licenses' links
 * open in the browser.
 */
@HiltViewModel(assistedFactory = OpenSourceLibraryViewModel.Factory::class)
class OpenSourceLibraryViewModel
    @AssistedInject
    constructor(
        @Assisted private val libraryId: String,
        private val repository: OpenSourceLicensesRepository,
    ) : ViewModel() {
        /** Creates the view model of the page of a library. */
        @AssistedFactory
        interface Factory {
            fun create(libraryId: String): OpenSourceLibraryViewModel
        }

        private val mutableUiState = MutableStateFlow<OpenSourceLibraryUiState>(OpenSourceLibraryUiState.Loading)
        val uiState: StateFlow<OpenSourceLibraryUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<OpenSourceLibraryEffect>(Channel.BUFFERED)
        val effects: Flow<OpenSourceLibraryEffect> = effectChannel.receiveAsFlow()

        private var loadJob: Job? = null

        init {
            load()
        }

        fun onEvent(event: OpenSourceLibraryEvent) {
            val content = mutableUiState.value as? OpenSourceLibraryUiState.Content
            when (event) {
                OpenSourceLibraryEvent.Retry -> {
                    if (mutableUiState.value is OpenSourceLibraryUiState.Error) load()
                }

                OpenSourceLibraryEvent.OpenWebsite -> {
                    content?.website?.let { effectChannel.trySend(OpenSourceLibraryEffect.OpenUrl(it)) }
                }

                is OpenSourceLibraryEvent.OpenLicense -> {
                    content
                        ?.licenses
                        ?.firstOrNull { it.id == event.licenseId }
                        ?.url
                        ?.let { effectChannel.trySend(OpenSourceLibraryEffect.OpenUrl(it)) }
                }
            }
        }

        private fun load() {
            loadJob?.cancel()
            mutableUiState.value = OpenSourceLibraryUiState.Loading
            loadJob =
                viewModelScope.launch {
                    mutableUiState.value =
                        when (val library = repository.library(libraryId)) {
                            is Outcome.Success -> library.value.toContent()
                            is Outcome.Failure -> OpenSourceLibraryUiState.Error(library.error)
                        }
                }
        }
    }

private fun OpenSourceLibrary.toContent(): OpenSourceLibraryUiState.Content =
    OpenSourceLibraryUiState.Content(
        id = id,
        name = name,
        version = version,
        isMavenArtifact = ':' in id,
        description = description,
        developers = developers.takeIf { it.isNotEmpty() }?.joinToString(),
        website = website,
        licenses =
            licenses
                .map { license ->
                    LicenseItem(
                        id = license.id,
                        name = license.name,
                        text = license.text?.let(::reflowLicenseText),
                        url = license.url,
                    )
                }.toImmutableList(),
    )

/**
 * [text], a license as published (lines broken at about 70 characters, for a fixed-width font), reflowed for a screen
 * of any width: the lines of each paragraph joined into one, which the screen breaks where it fits, and paragraphs one
 * empty line apart. Separator lines (`-----`) break paragraphs too, and are left out.
 */
internal fun reflowLicenseText(text: String): String =
    text
        .lines()
        .map { it.trim() }
        .fold(mutableListOf(mutableListOf<String>())) { paragraphs, line ->
            if (line.isEmpty() || SEPARATOR.matches(line)) {
                if (paragraphs.last().isNotEmpty()) paragraphs += mutableListOf<String>()
            } else {
                paragraphs.last() += line
            }
            paragraphs
        }.filter { it.isNotEmpty() }
        .joinToString(separator = "\n\n") { it.joinToString(separator = " ") }

/** A line of only dashes, equals signs, underscores or asterisks, which some licenses draw to set a title apart. */
private val SEPARATOR = Regex("[-=_*]{3,}")

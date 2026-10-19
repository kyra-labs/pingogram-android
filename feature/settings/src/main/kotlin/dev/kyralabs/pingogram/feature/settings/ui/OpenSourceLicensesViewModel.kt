package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.OpenSourceLibrary
import dev.kyralabs.pingogram.core.domain.repository.OpenSourceLicensesRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Open-source licenses page (`REQUIREMENTS.md` FR-S1, About): every library, font and icon set the app is built
 * from, as its build lists them ([OpenSourceLicensesRepository]), each with its version and the names of its licenses.
 */
@HiltViewModel
class OpenSourceLicensesViewModel
    @Inject
    constructor(
        private val repository: OpenSourceLicensesRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow<OpenSourceLicensesUiState>(OpenSourceLicensesUiState.Loading)
        val uiState: StateFlow<OpenSourceLicensesUiState> = mutableUiState.asStateFlow()

        private var loadJob: Job? = null

        init {
            load()
        }

        fun onEvent(event: OpenSourceLicensesEvent) {
            when (event) {
                OpenSourceLicensesEvent.Retry -> if (mutableUiState.value is OpenSourceLicensesUiState.Error) load()
            }
        }

        private fun load() {
            loadJob?.cancel()
            mutableUiState.value = OpenSourceLicensesUiState.Loading
            loadJob =
                viewModelScope.launch {
                    mutableUiState.value =
                        when (val libraries = repository.libraries()) {
                            is Outcome.Success -> {
                                OpenSourceLicensesUiState.Content(
                                    libraries.value.map { it.toItem() }.toImmutableList(),
                                )
                            }

                            is Outcome.Failure -> {
                                OpenSourceLicensesUiState.Error(libraries.error)
                            }
                        }
                }
        }
    }

private fun OpenSourceLibrary.toItem(): OpenSourceLibraryItem =
    OpenSourceLibraryItem(
        id = id,
        name = name,
        version = version,
        licenses = licenses.joinToString { it.name },
    )

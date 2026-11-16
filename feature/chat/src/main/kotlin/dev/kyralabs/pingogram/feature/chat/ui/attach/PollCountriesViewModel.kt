package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Country
import dev.kyralabs.pingogram.core.domain.repository.CountryRepository
import dev.kyralabs.pingogram.core.ui.phone.CountryPickerUiState
import dev.kyralabs.pingogram.core.ui.phone.matching
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The countries a channel's poll may limit voting to (`docs/DESIGN_SYSTEM.md` §6 "New poll", "Who can vote"):
 * Telegram's country list (`CountryRepository`, as sign-in's), read once the picker first opens, filtered by the
 * picker's query; the countries already picked are left out.
 */
@HiltViewModel
class PollCountriesViewModel
    @Inject
    constructor(
        private val countries: CountryRepository,
    ) : ViewModel() {
        private val all = MutableStateFlow<List<Country>>(emptyList())
        private val query = MutableStateFlow("")
        private val picked = MutableStateFlow<Set<String>>(emptySet())
        private var loaded = false

        /** The picker's list. */
        val picker: StateFlow<CountryPickerUiState> =
            combine(all, query, picked) { all, query, picked ->
                CountryPickerUiState(query, all.filter { it.isoCode !in picked }.matching(query))
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                CountryPickerUiState("", persistentListOf()),
            )

        /** Reads the country list, once, leaving out [alreadyPicked]. */
        fun open(alreadyPicked: Collection<String>) {
            picked.value = alreadyPicked.toSet()
            query.value = ""
            if (loaded) return
            loaded = true
            viewModelScope.launch {
                when (val result = countries.countries()) {
                    is Outcome.Success -> all.value = result.value
                    is Outcome.Failure -> loaded = false
                }
            }
        }

        fun setQuery(text: String) {
            query.value = text
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

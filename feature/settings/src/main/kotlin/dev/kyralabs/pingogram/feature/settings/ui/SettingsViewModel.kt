package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.DeviceAccount
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.TelegramServers
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.CountryRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceAccountsRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Settings screen: the logged-in account at the top, and logging out with a confirmation (`REQUIREMENTS.md`
 * FR-A8). Leaving for the login flow is not its job: the app follows TDLib's login state and replaces the main graph
 * once TDLib starts logging out.
 *
 * The account follows the user TDLib announces, with its phone number formatted by TDLib for its country
 * (`getPhoneNumberInfo`, once per number); until then, and if TDLib cannot format it, the number shows as `+` and its
 * digits.
 *
 * Below the card, the device's other accounts and "Add account" (`docs/adr/0031-multiple-accounts.md`), only while
 * the app offers several ([DeviceAccountsRepository.isAvailable]); their numbers are formatted as the card's. Switching
 * and adding hand over to [DeviceAccountsRepository], after which the app opens again on the other account.
 *
 * The open confirmation survives process death through the [SavedStateHandle]; a logout in progress does not, as
 * TDLib reports it by itself after a restart.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val authRepository: AuthRepository,
        userRepository: UserRepository,
        private val countryRepository: CountryRepository,
        private val deviceAccounts: DeviceAccountsRepository,
    ) : ViewModel() {
        private val mutableUiState =
            MutableStateFlow(SettingsUiState(isLogOutDialogOpen = savedStateHandle[KEY_LOG_OUT_DIALOG_OPEN] ?: false))
        val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<SettingsEffect>(Channel.BUFFERED)
        val effects: Flow<SettingsEffect> = effectChannel.receiveAsFlow()

        /** The raw number formatted last, and how; a user's other changes (their status) do not ask TDLib again. */
        private var formattedPhoneNumber: Pair<String, String>? = null

        /** The other accounts' raw numbers formatted so far, so their unread counts do not ask TDLib again. */
        private val otherPhoneNumbers = mutableMapOf<String, String>()

        init {
            combine(account(userRepository), authRepository.servers) { account, servers ->
                account?.copy(isOnTestServers = servers == TelegramServers.Test)
            }.onEach { account -> mutableUiState.update { it.copy(account = account) } }
                .launchIn(viewModelScope)
            if (deviceAccounts.isAvailable) {
                combine(otherAccounts(), deviceAccounts.canAddAccount) { others, canAdd -> others to canAdd }
                    .onEach { (others, canAdd) ->
                        mutableUiState.update { it.copy(otherAccounts = others, canAddAccount = canAdd) }
                    }.launchIn(viewModelScope)
            }
        }

        fun onEvent(event: SettingsEvent) {
            when (event) {
                SettingsEvent.LogOut -> if (!uiState.value.isLoggingOut) setLogOutDialogOpen(true)
                SettingsEvent.DismissLogOut -> setLogOutDialogOpen(false)
                SettingsEvent.ConfirmLogOut -> logOut()
                is SettingsEvent.SwitchAccount -> deviceAccounts.switchTo(event.slot)
                SettingsEvent.AddAccount -> deviceAccounts.addAccount()
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun account(userRepository: UserRepository): Flow<AccountUiModel?> =
            userRepository.myUserId
                .flatMapLatest { id -> if (id == null) flowOf(null) else userRepository.user(id) }
                .map { user -> user?.toAccountFields() }
                .distinctUntilChanged()
                .transformLatest { fields ->
                    if (fields == null) {
                        emit(null)
                        return@transformLatest
                    }
                    val cached = formattedPhoneNumber?.takeIf { it.first == fields.phoneNumber }?.second
                    emit(fields.toUiModel(cached ?: fields.phoneNumber.unformatted()))
                    if (cached != null || fields.phoneNumber.isEmpty()) return@transformLatest
                    val formatted = formatPhoneNumber(fields.phoneNumber) ?: return@transformLatest
                    formattedPhoneNumber = fields.phoneNumber to formatted
                    emit(fields.toUiModel(formatted))
                }

        /** The signed-in accounts but the one shown, with their numbers formatted once TDLib has (then on). */
        private fun otherAccounts(): Flow<ImmutableList<OtherAccountUiModel>> =
            deviceAccounts.accounts
                .map { accounts -> accounts.filter { !it.isActive && it.user != null } }
                .distinctUntilChanged()
                .transformLatest { accounts ->
                    emit(accounts.toUiModels())
                    val unformatted =
                        accounts
                            .mapNotNull {
                                it.rawPhoneNumber()
                            }.filter { it.isNotEmpty() && it !in otherPhoneNumbers }
                    if (unformatted.isEmpty()) return@transformLatest
                    unformatted.distinct().forEach { raw ->
                        formatPhoneNumber(raw)?.let { otherPhoneNumbers[raw] = it }
                    }
                    emit(accounts.toUiModels())
                }

        private fun List<DeviceAccount>.toUiModels(): ImmutableList<OtherAccountUiModel> =
            mapNotNull { account ->
                val fields = account.user?.toAccountFields() ?: return@mapNotNull null
                OtherAccountUiModel(
                    slot = account.slot,
                    userId = fields.userId,
                    name = fields.name,
                    photo = fields.photo,
                    phoneNumber = otherPhoneNumbers[fields.phoneNumber] ?: fields.phoneNumber.unformatted(),
                    unreadChats = account.unreadChats,
                )
            }.toImmutableList()

        /** [rawNumber] as TDLib formats it for its country, "+44 7911 123456"; null if TDLib cannot. */
        private suspend fun formatPhoneNumber(rawNumber: String): String? =
            countryRepository
                .phoneNumberInfo(rawNumber)
                .getOrNull()
                ?.takeIf { it.callingCode.isNotEmpty() }
                ?.let { "+${it.callingCode} ${it.formattedNumber.replace("-", "").trim()}".trim() }

        /** Sends one logout at a time; a second confirmation while it runs (a double tap) does nothing. */
        private fun logOut() {
            if (uiState.value.isLoggingOut) return
            setLogOutDialogOpen(false)
            mutableUiState.update { it.copy(isLoggingOut = true) }
            viewModelScope.launch {
                // On success the progress stays until the app leaves this screen.
                val error = (authRepository.logOut() as? Outcome.Failure)?.error ?: return@launch
                mutableUiState.update { it.copy(isLoggingOut = false) }
                if (error != DomainError.Silent) effectChannel.trySend(SettingsEffect.ShowError(error))
            }
        }

        private fun setLogOutDialogOpen(open: Boolean) {
            mutableUiState.update { it.copy(isLogOutDialogOpen = open) }
            savedStateHandle[KEY_LOG_OUT_DIALOG_OPEN] = open
        }

        private companion object {
            const val KEY_LOG_OUT_DIALOG_OPEN = "settings.logOutDialogOpen"
        }
    }

/** What the account's card shows of a [User], so that its other changes (status, contact flags) change nothing. */
private data class AccountFields(
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val phoneNumber: String,
    val username: String?,
    val isPremium: Boolean,
) {
    fun toUiModel(formattedPhoneNumber: String) =
        AccountUiModel(
            userId = userId,
            name = name,
            photo = photo,
            phoneNumber = formattedPhoneNumber,
            username = username,
            isPremium = isPremium,
        )

    override fun toString() = "AccountFields(userId=$userId, …)"
}

private fun User.toAccountFields() =
    AccountFields(
        userId = id,
        name = listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" "),
        photo = profilePhoto,
        phoneNumber = phoneNumber.filter(Char::isDigit),
        username = usernames.firstOrNull(),
        isPremium = isPremium,
    )

private fun DeviceAccount.rawPhoneNumber(): String? = user?.phoneNumber?.filter(Char::isDigit)

/** The digits TDLib reports as an international number, "+447911123456"; empty for none. */
private fun String.unformatted(): String = if (isEmpty()) "" else "+$this"

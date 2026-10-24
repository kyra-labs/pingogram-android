package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.ProxyKind
import dev.kyralabs.pingogram.core.domain.model.ProxyType
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import dev.kyralabs.pingogram.core.domain.repository.ProxyRepository
import dev.kyralabs.pingogram.core.navigation.EditProxyRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Adds a proxy, or edits proxy [proxyId] (`docs/adr/0027-proxies.md`): the protocol, the address, the port, what the
 * protocol needs to log in, and a name. The form is checked on the device before anything is sent; "Check" pings the
 * proxy as typed, "Save" hands it to TDLib, which connects through a new proxy at once and keeps an edited one as it
 * was (in use or not). What was typed survives process death in [savedStateHandle], except the password and the
 * secret, which are never saved there.
 */
@HiltViewModel
class EditProxyViewModel internal constructor(
    private val repository: ProxyRepository,
    private val savedStateHandle: SavedStateHandle,
    private val proxyId: Int?,
) : ViewModel() {
    @Inject
    constructor(
        repository: ProxyRepository,
        savedStateHandle: SavedStateHandle,
    ) : this(repository, savedStateHandle, savedStateHandle.toRoute<EditProxyRoute>().proxyId)

    private val mutableUiState = MutableStateFlow<EditProxyUiState>(EditProxyUiState.Loading)
    val uiState: StateFlow<EditProxyUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<EditProxyEffect>(Channel.BUFFERED)
    val effects: Flow<EditProxyEffect> = effectChannel.receiveAsFlow()

    /** Whether the edited proxy is in use, kept as it is when it is saved. */
    private var wasEnabled = false
    private var checkJob: Job? = null

    init {
        val restored = savedStateHandle.get<Boolean>(KEY_RESTORED) == true
        when {
            proxyId == null -> show(restoredForm() ?: ProxyForm())
            else -> load(proxyId, restored)
        }
    }

    fun onEvent(event: EditProxyEvent) {
        when (event) {
            is EditProxyEvent.Change -> change(event.form)
            EditProxyEvent.Check -> check()
            EditProxyEvent.Save -> save()
            EditProxyEvent.Retry -> if (uiState.value is EditProxyUiState.Error && proxyId != null) load(proxyId, false)
        }
    }

    private fun load(
        id: Int,
        restored: Boolean,
    ) {
        mutableUiState.value = EditProxyUiState.Loading
        viewModelScope.launch {
            when (val outcome = repository.proxies.first()) {
                is Outcome.Success -> {
                    val proxy = outcome.value.firstOrNull { it.id == id }
                    if (proxy == null) {
                        mutableUiState.value = EditProxyUiState.Error(DomainError.NotFound)
                    } else {
                        wasEnabled = proxy.isEnabled
                        val loaded = proxy.toForm()
                        // After process death the typed text wins, the secrets come from TDLib again.
                        show(if (restored) restoredForm(loaded) ?: loaded else loaded)
                    }
                }

                is Outcome.Failure -> {
                    mutableUiState.value = EditProxyUiState.Error(outcome.error)
                }
            }
        }
    }

    private fun show(form: ProxyForm) {
        mutableUiState.value = EditProxyUiState.Form(form, isNew = proxyId == null)
        persist(form)
    }

    private fun change(form: ProxyForm) {
        val state = uiState.value as? EditProxyUiState.Form ?: return
        if (state.saving || form == state.form) return
        checkJob?.cancel()
        mutableUiState.value =
            state.copy(
                form = form,
                // A field the user corrects stops showing its error; the others keep theirs until the next try.
                errors = state.errors intersect form.invalidFields(),
                check = null,
            )
        persist(form)
    }

    private fun check() {
        val state = uiState.value as? EditProxyUiState.Form ?: return
        val proxy = state.validProxy() ?: return
        checkJob?.cancel()
        mutableUiState.value = state.copy(check = ProxyCheck.Checking)
        checkJob =
            viewModelScope.launch {
                val check =
                    when (val outcome = repository.ping(proxy)) {
                        is Outcome.Success -> ProxyCheck.Available(outcome.value)
                        is Outcome.Failure -> ProxyCheck.Unavailable
                    }
                mutableUiState.update {
                    if (it is EditProxyUiState.Form &&
                        it.form == state.form
                    ) {
                        it.copy(check = check)
                    } else {
                        it
                    }
                }
            }
    }

    private fun save() {
        val state = (uiState.value as? EditProxyUiState.Form)?.takeUnless { it.saving } ?: return
        val proxy = state.validProxy() ?: return
        mutableUiState.value = state.copy(saving = true)
        viewModelScope.launch {
            val name = state.form.name.trim()
            val outcome =
                if (proxyId == null) {
                    repository.add(proxy, name, enable = true)
                } else {
                    repository.edit(proxyId, proxy, name, enable = wasEnabled)
                }
            when (outcome) {
                is Outcome.Success -> {
                    effectChannel.trySend(EditProxyEffect.Saved)
                }

                is Outcome.Failure -> {
                    mutableUiState.update { if (it is EditProxyUiState.Form) it.copy(saving = false) else it }
                    if (outcome.error !=
                        DomainError.Silent
                    ) {
                        effectChannel.trySend(EditProxyEffect.ShowError(outcome.error))
                    }
                }
            }
        }
    }

    /** The proxy of the form, or null after marking the fields that keep it from being sent. */
    private fun EditProxyUiState.Form.validProxy(): Proxy? {
        val invalid = form.invalidFields()
        if (invalid.isNotEmpty()) {
            mutableUiState.value = copy(errors = invalid)
            return null
        }
        return form.toProxy()
    }

    private fun persist(form: ProxyForm) {
        savedStateHandle[KEY_RESTORED] = true
        savedStateHandle[KEY_KIND] = form.kind.name
        savedStateHandle[KEY_SERVER] = form.server
        savedStateHandle[KEY_PORT] = form.port
        savedStateHandle[KEY_USERNAME] = form.username
        savedStateHandle[KEY_HTTP_ONLY] = form.httpOnly
        savedStateHandle[KEY_NAME] = form.name
    }

    /** The form as saved before process death, with [base]'s password and secret; null if nothing was saved. */
    private fun restoredForm(base: ProxyForm = ProxyForm()): ProxyForm? {
        if (savedStateHandle.get<Boolean>(KEY_RESTORED) != true) return null
        return base.copy(
            kind =
                savedStateHandle.get<String>(KEY_KIND)?.let { name ->
                    ProxyKind.entries.firstOrNull { it.name == name }
                }
                    ?: base.kind,
            server = savedStateHandle[KEY_SERVER] ?: base.server,
            port = savedStateHandle[KEY_PORT] ?: base.port,
            username = savedStateHandle[KEY_USERNAME] ?: base.username,
            httpOnly = savedStateHandle[KEY_HTTP_ONLY] ?: base.httpOnly,
            name = savedStateHandle[KEY_NAME] ?: base.name,
        )
    }

    internal companion object {
        const val KEY_RESTORED = "proxy.restored"
        const val KEY_KIND = "proxy.kind"
        const val KEY_SERVER = "proxy.server"
        const val KEY_PORT = "proxy.port"
        const val KEY_USERNAME = "proxy.username"
        const val KEY_HTTP_ONLY = "proxy.httpOnly"
        const val KEY_NAME = "proxy.name"
    }
}

private const val MAX_PORT = 65_535
private const val MAX_SERVER_LENGTH = 253
private val SecretCharacters = Regex("[0-9A-Za-z_+/=-]+")

/** The fields that keep this form from being sent: a server with spaces, a port out of range, a secret with others. */
internal fun ProxyForm.invalidFields(): Set<ProxyField> =
    buildSet {
        val server = server.trim()
        if (server.isEmpty() || server.length > MAX_SERVER_LENGTH ||
            server.any { it.isWhitespace() }
        ) {
            add(ProxyField.Server)
        }
        if (port.trim().toIntOrNull() !in 1..MAX_PORT) add(ProxyField.Port)
        if (kind == ProxyKind.Mtproto && !SecretCharacters.matches(secret.trim())) add(ProxyField.Secret)
    }

/** The proxy of a valid form. */
internal fun ProxyForm.toProxy(): Proxy =
    Proxy(
        server = server.trim(),
        port = port.trim().toInt(),
        type =
            when (kind) {
                ProxyKind.Socks5 -> ProxyType.Socks5(username.trim(), password)
                ProxyKind.Http -> ProxyType.Http(username.trim(), password, httpOnly)
                ProxyKind.Mtproto -> ProxyType.Mtproto(secret.trim())
            },
    )

internal fun SavedProxy.toForm(): ProxyForm {
    val base = ProxyForm(kind = proxy.type.kind, server = proxy.server, port = proxy.port.toString(), name = name)
    return when (val type = proxy.type) {
        is ProxyType.Socks5 -> base.copy(username = type.username, password = type.password)
        is ProxyType.Http -> base.copy(username = type.username, password = type.password, httpOnly = type.httpOnly)
        is ProxyType.Mtproto -> base.copy(secret = type.secret)
    }
}

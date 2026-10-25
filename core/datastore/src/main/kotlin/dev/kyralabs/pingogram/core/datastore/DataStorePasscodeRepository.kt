package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.model.AutoLock
import dev.kyralabs.pingogram.core.domain.model.PasscodeSettings
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PasscodeRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]), which is never backed up
 * (the app allows no backup). The passcode is saved only as its PBKDF2 hash with its salt and rounds
 * ([PasscodeHasher]), hashed and checked on the [dispatcher] for CPU work. Enums are stored by name; a name that no
 * longer exists, a file that cannot be read, and a missing key read as the default.
 */
@Singleton
class DataStorePasscodeRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val dispatcher: CoroutineDispatcher,
    private val hasher: PasscodeHasher,
) : PasscodeRepository {
    @Inject
    constructor(
        @SettingsDataStore dataStore: DataStore<Preferences>,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(dataStore, dispatcher, PasscodeHasher())

    override val settings: Flow<PasscodeSettings> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { it.toPasscodeSettings() }
            .distinctUntilChanged()

    override suspend fun setPasscode(
        passcode: String,
        type: PasscodeType,
    ): Outcome<Unit> {
        require(type.accepts(passcode)) { "Not a valid passcode of type $type" }
        val hash = withContext(dispatcher) { hasher.hash(passcode) }
        return edit {
            it[HASH] = hash.hash.encode()
            it[SALT] = hash.salt.encode()
            it[ITERATIONS] = hash.iterations
            it[TYPE] = type.name
            it.remove(FAILED_ATTEMPTS)
        }
    }

    override suspend fun checkPasscode(passcode: String): Outcome<Boolean> =
        when (val saved = savedHash()) {
            is Outcome.Failure -> {
                saved
            }

            // Without a passcode nothing matches, and nothing counts as an attempt.
            is Outcome.Success -> {
                val hash = saved.value ?: return Outcome.Success(false)
                val matches = withContext(dispatcher) { hasher.matches(passcode, hash) }
                edit {
                    if (matches) it.remove(FAILED_ATTEMPTS) else it[FAILED_ATTEMPTS] = (it[FAILED_ATTEMPTS] ?: 0) + 1
                }.map { matches }
            }
        }

    /** The saved hash, null without a passcode; fails if the settings cannot be read. */
    private suspend fun savedHash(): Outcome<PasscodeHash?> =
        try {
            Outcome.Success(dataStore.data.first().passcodeHash())
        } catch (error: IOException) {
            Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not read: ${error.message}"))
        }

    override suspend fun resetFailedAttempts() = edit { it.remove(FAILED_ATTEMPTS) }

    override suspend fun removePasscode() =
        edit {
            it.remove(HASH)
            it.remove(SALT)
            it.remove(ITERATIONS)
            it.remove(FAILED_ATTEMPTS)
        }

    override suspend fun setAutoLock(autoLock: AutoLock) = edit { it[AUTO_LOCK] = autoLock.name }

    override suspend fun setBiometricUnlock(enabled: Boolean) = edit { it[BIOMETRIC_UNLOCK] = enabled }

    override suspend fun setDeviceCredentialUnlock(enabled: Boolean) = edit { it[DEVICE_CREDENTIAL_UNLOCK] = enabled }

    override suspend fun setHideContent(enabled: Boolean) = edit { it[HIDE_CONTENT] = enabled }

    private suspend fun edit(change: (MutablePreferences) -> Unit): Outcome<Unit> =
        try {
            dataStore.edit(change)
            Outcome.Success(Unit)
        } catch (error: IOException) {
            Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
        }
}

private fun Preferences.passcodeHash(): PasscodeHash? {
    val hash = this[HASH]?.decode()
    val salt = this[SALT]?.decode()
    val iterations = this[ITERATIONS]
    return if (hash != null && salt != null && iterations != null) PasscodeHash(hash, salt, iterations) else null
}

private fun Preferences.toPasscodeSettings(): PasscodeSettings {
    val defaults = PasscodeSettings()
    return PasscodeSettings(
        isSet = passcodeHash() != null,
        type = enumOrNull<PasscodeType>(this[TYPE]) ?: defaults.type,
        autoLock = enumOrNull<AutoLock>(this[AUTO_LOCK]) ?: defaults.autoLock,
        biometricUnlock = this[BIOMETRIC_UNLOCK] ?: defaults.biometricUnlock,
        deviceCredentialUnlock = this[DEVICE_CREDENTIAL_UNLOCK] ?: defaults.deviceCredentialUnlock,
        hideContent = this[HIDE_CONTENT] ?: defaults.hideContent,
        failedAttempts = this[FAILED_ATTEMPTS] ?: defaults.failedAttempts,
    )
}

private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = enumValues<E>().firstOrNull { it.name == name }

private fun ByteArray.encode(): String = Base64.getEncoder().encodeToString(this)

/** The bytes of a Base64 string; null for one that is not, which then reads as no passcode. */
private fun String.decode(): ByteArray? =
    try {
        Base64.getDecoder().decode(this)
    } catch (_: IllegalArgumentException) {
        null
    }

// Keys of the settings file; renaming one loses what users saved under it, the passcode included.
private val HASH = stringPreferencesKey("passcode.hash")
private val SALT = stringPreferencesKey("passcode.salt")
private val ITERATIONS = intPreferencesKey("passcode.iterations")
private val TYPE = stringPreferencesKey("passcode.type")
private val AUTO_LOCK = stringPreferencesKey("passcode.auto_lock")
private val BIOMETRIC_UNLOCK = booleanPreferencesKey("passcode.biometric_unlock")
private val DEVICE_CREDENTIAL_UNLOCK = booleanPreferencesKey("passcode.device_credential_unlock")
private val HIDE_CONTENT = booleanPreferencesKey("passcode.hide_content")
private val FAILED_ATTEMPTS = intPreferencesKey("passcode.failed_attempts")

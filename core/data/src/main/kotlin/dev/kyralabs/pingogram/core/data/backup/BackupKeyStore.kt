package dev.kyralabs.pingogram.core.data.backup

import android.content.Context
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The key automatic backups encrypt with (`docs/adr/0039-local-backup-and-restore.md`, "Automatic backups"): made from
 * the password of the last backup the user wrote, then kept; the password itself never is. Blocking: off the main
 * thread.
 */
interface BackupKeyStore {
    /** Whether a key is kept; then every change. */
    val hasKey: Flow<Boolean>

    /** Keeps [key] in place of the one kept before; false when it cannot be kept. */
    fun keep(key: BackupKey): Boolean

    /** The kept key with its salt and rounds; null while none is kept or it cannot be read. */
    fun key(): BackupKey?
}

/**
 * [BackupKeyStore] in Android's Keystore, where the key can only encrypt and never leaves; its salt and rounds, which
 * every backup's header holds anyway, in a small file of the app's no-backup files.
 */
@Singleton
class AndroidBackupKeyStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : BackupKeyStore {
        private val kept = MutableStateFlow<Boolean?>(null)
        private val file: File get() = File(context.noBackupFilesDir, FILE_NAME)

        override val hasKey: Flow<Boolean> =
            flow {
                if (kept.value == null) kept.compareAndSet(null, key() != null)
                emitAll(kept.filterNotNull())
            }

        override fun keep(key: BackupKey): Boolean =
            try {
                val protection =
                    KeyProtection
                        .Builder(KeyProperties.PURPOSE_ENCRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                keyStore().setEntry(ALIAS, KeyStore.SecretKeyEntry(key.key), protection)
                file.writeText("${Base64.getEncoder().encodeToString(key.salt)} ${key.rounds}")
                kept.value = true
                true
            } catch (_: GeneralSecurityException) {
                false
            } catch (_: IOException) {
                false
            }

        override fun key(): BackupKey? =
            try {
                val secret = keyStore().getKey(ALIAS, null) as? SecretKey
                val parts = if (file.exists()) file.readText().split(' ') else emptyList()
                if (secret == null || parts.size != 2) {
                    null
                } else {
                    BackupKey(secret, Base64.getDecoder().decode(parts[0]), parts[1].toInt())
                }
            } catch (_: GeneralSecurityException) {
                null
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }

        private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

        private companion object {
            const val PROVIDER = "AndroidKeyStore"
            const val ALIAS = "pingogram.backup"
            const val FILE_NAME = "backup_key"
        }
    }

package dev.kyralabs.pingogram.core.tdlib

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * An account's place on the device (`docs/adr/0031-multiple-accounts.md`): its TDLib directories, its database key
 * file and the Keystore key that wraps it. The first slot keeps the names of before multiple accounts, so an update
 * moves nothing; the others add their index ("tdlib-1", "tdlib_database_key_1").
 *
 * A plain class rather than a value class: Dagger injects it, and value classes in injected constructors are not
 * supported.
 */
data class AccountSlot(
    val index: Int,
) {
    init {
        require(index in 0 until MAX_ACCOUNTS) { "No account slot $index" }
    }

    /** "" for the first slot, else [separator] and the index. */
    internal fun suffix(separator: String) = if (index == 0) "" else "$separator$index"

    companion object {
        /** Telegram's app allows 3 accounts, a fourth while one has Telegram Premium (ADR 0031). */
        const val MAX_ACCOUNTS = 4

        val First = AccountSlot(0)
    }
}

/**
 * Deletes what an account left on the device: both of its TDLib directories (the production and the test DC's), its
 * database key file and its Keystore key. Only for a slot whose TDLib client is closed. Blocking: off the main thread.
 */
class AccountStorage internal constructor(
    private val filesDir: () -> File,
    private val noBackupFilesDir: () -> File,
    private val wrappingKeys: (AccountSlot) -> WrappingKeyStore,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        filesDir = { context.filesDir },
        noBackupFilesDir = { context.noBackupFilesDir },
        wrappingKeys = ::AndroidKeystoreWrappingKeyStore,
    )

    fun delete(slot: AccountSlot) {
        for (useTestDc in listOf(false, true)) {
            TdlibDirectories.of(filesDir(), useTestDc, slot).root.deleteRecursively()
        }
        noBackupFilesDir().resolve(KeystoreDatabaseKeyProvider.keyFileName(slot)).delete()
        wrappingKeys(slot).delete()
    }
}

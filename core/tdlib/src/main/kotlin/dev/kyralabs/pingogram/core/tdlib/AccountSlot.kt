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
        require(index in 0 until MAX_ACCOUNTS || index == DEMO_INDEX) { "No account slot $index" }
    }

    /** The demo's slot ([Demo]): not one of the accounts, with no TDLib directories or database key of its own. */
    val isDemo: Boolean get() = index == DEMO_INDEX

    /** "" for the first slot, else [separator] and the index. */
    internal fun suffix(separator: String) = if (index == 0) "" else "$separator$index"

    companion object {
        /** Telegram's app allows 3 accounts, a fourth while one has Telegram Premium (ADR 0031). */
        const val MAX_ACCOUNTS = 4

        val First = AccountSlot(0)

        private const val DEMO_INDEX = -1

        /**
         * The demo's graph (`docs/adr/0034-demo-mode-for-store-review.md`), apart from the accounts: never in their
         * registry, so notifications, push, widgets and the accounts limit never see it.
         */
        val Demo = AccountSlot(DEMO_INDEX)
    }
}

/**
 * Deletes what an account left on the device: its TDLib directory, its database key file and its Keystore key, and what
 * the test-server sign-in of versions up to 1.0.0 left. Only for a slot whose TDLib client is closed.
 * Blocking: off the main thread.
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
        TdlibDirectories.of(filesDir(), slot).root.deleteRecursively()
        TdlibDirectories.legacyTestDc(filesDir(), slot).deleteRecursively()
        noBackupFilesDir().resolve(KeystoreDatabaseKeyProvider.keyFileName(slot)).delete()
        noBackupFilesDir().resolve(legacyTestServersMarkerName(slot)).delete()
        wrappingKeys(slot).delete()
    }
}

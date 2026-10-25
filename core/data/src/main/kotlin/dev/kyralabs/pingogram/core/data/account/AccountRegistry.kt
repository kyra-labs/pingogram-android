package dev.kyralabs.pingogram.core.data.account

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Which account slots are on the device, in the order they were added, and the one the screens show
 * (`docs/adr/0031-multiple-accounts.md`). Slot 0, the first account's, is on the device from the first start.
 */
data class AccountRegistry(
    val slots: List<Int>,
    val active: Int,
) {
    init {
        require(active in slots) { "The active slot $active is not on the device" }
    }

    companion object {
        val First = AccountRegistry(slots = listOf(0), active = 0)
    }
}

/**
 * [AccountRegistry] in a small file of `noBackupFilesDir` rather than the settings file: it is read before anything
 * shows, a millisecond for a few bytes, where the settings file's first read takes tens of milliseconds
 * (`docs/PERFORMANCE.md` §2). Blocking: off the main thread.
 */
class AccountRegistryFile(
    private val file: () -> File,
) {
    /** The registry, or [AccountRegistry.First] for a file that is missing or unreadable. */
    fun read(): AccountRegistry =
        try {
            val f = file()
            if (f.isFile) parse(f.readText()) ?: AccountRegistry.First else AccountRegistry.First
        } catch (ignored: IOException) {
            AccountRegistry.First
        }

    /** Replaces the file in one step, so a crash never leaves half of it. */
    fun write(registry: AccountRegistry) {
        val f = file()
        f.parentFile?.mkdirs()
        val temporary = File(f.path + ".tmp")
        FileOutputStream(temporary).use { output ->
            output.write(format(registry).toByteArray())
            output.fd.sync()
        }
        Files.move(temporary.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        const val FILE_NAME = "accounts"
        private const val VERSION = "1"

        internal fun format(registry: AccountRegistry) =
            "$VERSION\nactive=${registry.active}\nslots=${registry.slots.joinToString(",")}\n"

        /** Null for anything that is not a registry in this format. */
        internal fun parse(text: String): AccountRegistry? {
            val lines = text.lines()
            if (lines.firstOrNull() != VERSION) return null
            val values = lines.drop(1).mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
            val fields = values.associate { (key, value) -> key to value }
            val slots =
                fields["slots"]
                    ?.split(',')
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?.distinct()
                    .orEmpty()
            val active = fields["active"]?.toIntOrNull()
            return if (slots.isNotEmpty() && active in slots && slots.all { it >= 0 }) {
                AccountRegistry(slots, active!!)
            } else {
                null
            }
        }
    }
}

package dev.kyralabs.pingogram.core.data.backup

import dev.kyralabs.pingogram.core.domain.model.BackupHeader
import dev.kyralabs.pingogram.core.domain.model.BackupSection
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipException
import javax.crypto.SecretKey

/**
 * The bytes of a backup file (`docs/adr/0039-local-backup-and-restore.md`): "PGBACKUP", the header's length (4 bytes,
 * big-endian) and the header, JSON that reads without the password (format version, app version, date, sections, and
 * the key's salt and rounds and the nonce); then the body, JSON compressed with GZIP and encrypted with AES-256-GCM
 * ([BackupCipher]), whose tag covers everything before it too, so a changed header reads as a wrong password.
 *
 * A file of a newer [FORMAT_VERSION] is refused before its password is tried: the app that reads it must be updated.
 */
internal object BackupFile {
    /** The format this app writes, and the newest it reads. */
    const val FORMAT_VERSION = 1

    private val MAGIC = "PGBACKUP".toByteArray(Charsets.US_ASCII)
    private const val MAX_HEADER_BYTES = 64 * 1024
    private const val MAX_BODY_BYTES = 64 * 1024 * 1024

    /** At most this many times the rounds this app uses: a file asking for more is not one this app wrote. */
    private const val MAX_ROUNDS_FACTOR = 10

    private const val BUFFER_BYTES = 8 * 1024

    /** Writes [body] into [output] under [header], encrypted with [key]. */
    fun write(
        output: OutputStream,
        header: BackupHeader,
        key: BackupKey,
        body: String,
    ) {
        val compressed =
            ByteArrayOutputStream().also { bytes -> GZIPOutputStream(bytes).use { it.write(body.toByteArray()) } }
        var prefix = ByteArray(0)
        val encrypted =
            BackupCipher.encrypt(
                key = key.key,
                aad = { nonce -> prefixOf(header, key.salt, key.rounds, nonce).also { prefix = it } },
                plain = compressed.toByteArray(),
            )
        output.write(prefix)
        output.write(encrypted)
    }

    /** The header of the backup in [input]; null when it is not a backup. */
    fun readHeader(input: InputStream): BackupHeader? = readPrefix(input)?.header

    /** The backup in [input], decrypted with [password]. */
    fun read(
        input: InputStream,
        password: String,
    ): BackupFileRead {
        val prefix = readPrefix(input)
        val encrypted = prefix?.let { input.readAtMost(MAX_BODY_BYTES + 1) }
        return when {
            prefix == null || encrypted == null -> BackupFileRead.NotABackup
            prefix.header.formatVersion > FORMAT_VERSION -> BackupFileRead.Newer(prefix.header)
            encrypted.size > MAX_BODY_BYTES -> BackupFileRead.NotABackup
            else -> decrypt(prefix, encrypted, password)
        }
    }

    private fun decrypt(
        prefix: Prefix,
        encrypted: ByteArray,
        password: String,
    ): BackupFileRead {
        val key = BackupCipher.key(password, prefix.salt, prefix.rounds)
        val compressed = BackupCipher.decrypt(key, prefix.nonce, prefix.bytes, encrypted)
        val body = compressed?.let(::gunzip)
        return when {
            compressed == null -> BackupFileRead.WrongPassword
            body == null -> BackupFileRead.NotABackup
            else -> BackupFileRead.Read(prefix.header, body.decodeToString())
        }
    }

    /** [compressed] unzipped; null when it is not GZIP or unzips to more than a backup holds. */
    private fun gunzip(compressed: ByteArray): ByteArray? =
        try {
            GZIPInputStream(ByteArrayInputStream(compressed))
                .use { it.readAtMost(MAX_BODY_BYTES + 1) }
                .takeIf { it.size <= MAX_BODY_BYTES }
        } catch (_: ZipException) {
            null
        }

    private fun prefixOf(
        header: BackupHeader,
        salt: ByteArray,
        rounds: Int,
        nonce: ByteArray,
    ): ByteArray {
        val json =
            JSONObject()
                .put(FORMAT, header.formatVersion)
                .put(APP, header.appVersion)
                .put(CREATED, header.createdAt.epochSecond)
                .put(SECTIONS, JSONArray(header.sections.map { it.key }))
                .put(SALT, salt.base64())
                .put(ROUNDS, rounds)
                .put(NONCE, nonce.base64())
                .toString()
                .toByteArray()
        return MAGIC + ByteBuffer.allocate(Int.SIZE_BYTES).putInt(json.size).array() + json
    }

    private fun readPrefix(input: InputStream): Prefix? =
        try {
            val data = DataInputStream(input)
            val magic = ByteArray(MAGIC.size).also(data::readFully)
            val length = data.readInt()
            if (!magic.contentEquals(MAGIC) || length !in 1..MAX_HEADER_BYTES) {
                null
            } else {
                val json = ByteArray(length).also(data::readFully)
                prefixOf(json, magic + ByteBuffer.allocate(Int.SIZE_BYTES).putInt(length).array() + json)
            }
        } catch (_: EOFException) {
            null
        }

    private fun prefixOf(
        json: ByteArray,
        bytes: ByteArray,
    ): Prefix? =
        try {
            val header = JSONObject(json.decodeToString())
            val sections = header.getJSONArray(SECTIONS)
            Prefix(
                header =
                    BackupHeader(
                        formatVersion = header.getInt(FORMAT),
                        appVersion = header.getString(APP),
                        createdAt = Instant.ofEpochSecond(header.getLong(CREATED)),
                        sections =
                            (0 until sections.length())
                                .mapNotNull { index ->
                                    BackupSection.entries.firstOrNull { it.key == sections.getString(index) }
                                }.toSet(),
                    ),
                salt = Base64.getDecoder().decode(header.getString(SALT)),
                rounds =
                    header.getInt(ROUNDS).takeIf { it in 1..BackupCipher.ROUNDS * MAX_ROUNDS_FACTOR } ?: return null,
                nonce = Base64.getDecoder().decode(header.getString(NONCE)),
                bytes = bytes,
            )
        } catch (_: JSONException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    /** Up to [limit] bytes, or all there are (`InputStream.readNBytes` needs Android 13). */
    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (bytes.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - bytes.size()))
            if (read < 0) break
            bytes.write(buffer, 0, read)
        }
        return bytes.toByteArray()
    }

    private fun ByteArray.base64(): String = Base64.getEncoder().encodeToString(this)

    /** The header, the key's [salt] and [rounds], the [nonce], and the header's [bytes] as the file has them. */
    private class Prefix(
        val header: BackupHeader,
        val salt: ByteArray,
        val rounds: Int,
        val nonce: ByteArray,
        val bytes: ByteArray,
    )

    private const val FORMAT = "format"
    private const val APP = "app"
    private const val CREATED = "created"
    private const val SECTIONS = "sections"
    private const val SALT = "salt"
    private const val ROUNDS = "rounds"
    private const val NONCE = "nonce"
}

/**
 * The key of a backup: [key] made from the user's password with [salt] and [rounds] ([BackupCipher.key]), which the
 * file's header keeps, so the password alone opens it again.
 */
class BackupKey(
    val key: SecretKey,
    val salt: ByteArray,
    val rounds: Int,
) {
    companion object {
        /** A new key for [password], with a new salt and the rounds this app uses. Slow: off the main thread. */
        internal fun of(password: String): BackupKey {
            val salt = BackupCipher.newSalt()
            return BackupKey(BackupCipher.key(password, salt, BackupCipher.ROUNDS), salt, BackupCipher.ROUNDS)
        }
    }
}

/** A section's name in a backup's header and body. */
internal val BackupSection.key: String
    get() =
        when (this) {
            BackupSection.Settings -> "settings"
            BackupSection.Account -> "account"
        }

/** What reading a backup file found. */
internal sealed interface BackupFileRead {
    /** The backup's [header] and its [body], the JSON of its sections. */
    data class Read(
        val header: BackupHeader,
        val body: String,
    ) : BackupFileRead

    /** A backup of a newer format than this app reads: the app must be updated. */
    data class Newer(
        val header: BackupHeader,
    ) : BackupFileRead

    /** The password is not the backup's, or the file was changed. */
    data object WrongPassword : BackupFileRead

    /** Not a backup file, or a damaged one. */
    data object NotABackup : BackupFileRead
}

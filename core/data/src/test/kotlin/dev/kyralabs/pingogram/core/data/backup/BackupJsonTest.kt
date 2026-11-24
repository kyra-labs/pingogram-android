package dev.kyralabs.pingogram.core.data.backup

import dev.kyralabs.pingogram.core.domain.model.AccountBackup
import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkPreview
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import dev.kyralabs.pingogram.core.domain.model.KeywordHit
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.domain.model.Snippet
import kotlinx.collections.immutable.persistentListOf
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** A backup body's sections in JSON and back. On Robolectric for Android's `org.json`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupJsonTest {
    @Test
    fun `settings keep their types`() {
        val settings =
            mapOf("b" to true, "i" to 7, "l" to 7L, "f" to 1.5f, "d" to 2.25, "s" to "Dark", "set" to setOf("a", "b"))

        assertEquals(settings, JSONObject(settings.toSettingsJson().toString()).toSettings())
    }

    @Test
    fun `an account keeps all its data, notes and previews included`() {
        val account =
            AccountBackup(
                userId = 7,
                bookmarks =
                    listOf(
                        Bookmark(
                            chatId = -100,
                            messageId = 2_097_152,
                            savedAt = 1_763_000_000,
                            tagIds = persistentListOf(1, 2),
                            note = "read \"later\"\nplease",
                            preview = BookmarkPreview(sender = "Ada", text = "Launch at 9"),
                        ),
                    ),
                tags = listOf(BookmarkTag(1, "Work", 2), BookmarkTag(2, "Ideas", 5)),
                reminders =
                    listOf(
                        MessageReminder(-100, 2_097_152, Instant.ofEpochSecond(1_763_100_000), "call", true),
                    ),
                keywordHits = listOf(KeywordHit(-100, 3_145_728, "café", 1_763_000_100)),
                snippets = listOf(Snippet(4, "addr", "1 Main St\nSpringfield")),
                birthdayReminders = listOf(BirthdayReminder(userId = 42, day = 29, month = 2)),
            )

        assertEquals(account, JSONObject(account.toJson().toString()).toAccountBackup())
    }

    @Test
    fun `an account without data reads back as one without data`() {
        assertEquals(AccountBackup(userId = 7), JSONObject().put("user_id", 7).toAccountBackup())
    }
}

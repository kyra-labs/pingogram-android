package dev.kyralabs.pingogram.core.data.backup

import dev.kyralabs.pingogram.core.domain.model.AccountBackup
import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkPreview
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import dev.kyralabs.pingogram.core.domain.model.ChatNote
import dev.kyralabs.pingogram.core.domain.model.KeywordHit
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.domain.model.Snippet
import kotlinx.collections.immutable.toImmutableList
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** An `account` section of a backup's body: the account's Telegram user id and its data, as JSON. */
internal fun AccountBackup.toJson(): JSONObject =
    JSONObject()
        .put(USER_ID, userId)
        .put(BOOKMARKS, bookmarks.toJsonArray { it.toJson() })
        .put(TAGS, tags.toJsonArray { JSONObject().put(ID, it.id).put(NAME, it.name).put(COLOR, it.color) })
        .put(REMINDERS, reminders.toJsonArray { it.toJson() })
        .put(KEYWORD_HITS, keywordHits.toJsonArray { it.toJson() })
        .put(SNIPPETS, snippets.toJsonArray { JSONObject().put(ID, it.id).put(NAME, it.name).put(TEXT, it.text) })
        .put(BIRTHDAY_REMINDERS, birthdayReminders.toJsonArray { it.toJson() })
        .put(
            CHAT_NOTES,
            chatNotes.toJsonArray { JSONObject().put(CHAT_ID, it.chatId).put(NAME, it.name).put(NOTE, it.note) },
        )

/** The account section [toJson] wrote; throws `JSONException` for one that does not read. */
internal fun JSONObject.toAccountBackup(): AccountBackup =
    AccountBackup(
        userId = getLong(USER_ID),
        bookmarks = objects(BOOKMARKS) { it.toBookmark() },
        tags = objects(TAGS) { BookmarkTag(it.getLong(ID), it.getString(NAME), it.getInt(COLOR)) },
        reminders = objects(REMINDERS) { it.toReminder() },
        keywordHits =
            objects(KEYWORD_HITS) {
                KeywordHit(it.getLong(CHAT_ID), it.getLong(MESSAGE_ID), it.getString(WORD), it.getLong(DATE))
            },
        snippets = objects(SNIPPETS) { Snippet(it.getLong(ID), it.getString(NAME), it.getString(TEXT)) },
        birthdayReminders =
            objects(BIRTHDAY_REMINDERS) { BirthdayReminder(it.getLong(USER_ID), it.getInt(DAY), it.getInt(MONTH)) },
        chatNotes = objects(CHAT_NOTES) { ChatNote(it.getLong(CHAT_ID), it.optString(NAME), it.optString(NOTE)) },
    )

private fun Bookmark.toJson(): JSONObject =
    JSONObject()
        .put(CHAT_ID, chatId)
        .put(MESSAGE_ID, messageId)
        .put(SAVED_AT, savedAt)
        .put(TAG_IDS, JSONArray(tagIds))
        .put(NOTE, note)
        .put(SENDER, preview.sender)
        .put(TEXT, preview.text)

private fun JSONObject.toBookmark(): Bookmark {
    val tagIds = getJSONArray(TAG_IDS)
    return Bookmark(
        chatId = getLong(CHAT_ID),
        messageId = getLong(MESSAGE_ID),
        savedAt = getLong(SAVED_AT),
        tagIds = (0 until tagIds.length()).map(tagIds::getLong).toImmutableList(),
        note = optString(NOTE),
        preview = BookmarkPreview(sender = optString(SENDER), text = optString(TEXT)),
    )
}

private fun MessageReminder.toJson(): JSONObject =
    JSONObject()
        .put(CHAT_ID, chatId)
        .put(MESSAGE_ID, messageId)
        .put(AT, at.epochSecond)
        .put(NOTE, note)
        .put(DONE, isDone)

private fun JSONObject.toReminder(): MessageReminder =
    MessageReminder(
        chatId = getLong(CHAT_ID),
        messageId = getLong(MESSAGE_ID),
        at = Instant.ofEpochSecond(getLong(AT)),
        note = optString(NOTE),
        isDone = optBoolean(DONE),
    )

private fun BirthdayReminder.toJson(): JSONObject = JSONObject().put(USER_ID, userId).put(DAY, day).put(MONTH, month)

private fun KeywordHit.toJson(): JSONObject =
    JSONObject()
        .put(CHAT_ID, chatId)
        .put(MESSAGE_ID, messageId)
        .put(WORD, word)
        .put(DATE, date)

private fun <T> List<T>.toJsonArray(item: (T) -> JSONObject): JSONArray = JSONArray(map(item))

private fun <T> JSONObject.objects(
    name: String,
    item: (JSONObject) -> T,
): List<T> {
    val array = optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).map { item(array.getJSONObject(it)) }
}

private const val USER_ID = "user_id"
private const val BOOKMARKS = "bookmarks"
private const val TAGS = "tags"
private const val REMINDERS = "reminders"
private const val KEYWORD_HITS = "keyword_hits"
private const val SNIPPETS = "snippets"
private const val BIRTHDAY_REMINDERS = "birthday_reminders"
private const val CHAT_NOTES = "chat_notes"
private const val ID = "id"
private const val NAME = "name"
private const val COLOR = "color"
private const val TEXT = "text"
private const val CHAT_ID = "chat_id"
private const val MESSAGE_ID = "message_id"
private const val SAVED_AT = "saved_at"
private const val TAG_IDS = "tag_ids"
private const val NOTE = "note"
private const val SENDER = "sender"
private const val AT = "at"
private const val DONE = "done"
private const val WORD = "word"
private const val DATE = "date"
private const val DAY = "day"
private const val MONTH = "month"

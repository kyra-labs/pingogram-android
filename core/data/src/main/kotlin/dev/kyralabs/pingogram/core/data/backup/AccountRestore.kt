package dev.kyralabs.pingogram.core.data.backup

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.model.AccountBackup
import dev.kyralabs.pingogram.core.domain.model.Snippet
import dev.kyralabs.pingogram.core.domain.repository.AccountRepositories
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Merges an account section of a backup into the account of [repositories]
 * (`docs/adr/0039-local-backup-and-restore.md`, "Restoring"), through its own repositories, so what shows and what is
 * saved stay one:
 *
 * - **Tags** by name, letter case aside: a tag the phone lacks is added, and the backup's bookmarks take the phone's.
 * - **Bookmarks** by message: the backup's when the phone has none or saved it earlier (the newer one wins).
 * - **Reminders** by message: the backup's when the phone has none, done if its time is before [now]; one on the phone
 *   was set after the backup or kept since, so it stays.
 * - **Keyword hits** the phone lacks; the repository keeps those of the last week only, as always.
 * - **Snippets** by name, letter case aside: the backup's when the phone has none by that name and has room for it.
 *
 * Nothing on the phone is deleted. The first write that fails stops it, with what was written so far kept.
 */
internal class AccountRestore(
    private val repositories: AccountRepositories,
    private val now: Instant,
) {
    suspend fun restore(backup: AccountBackup): Outcome<Unit> =
        tags(backup)
            .then { tagIds -> bookmarks(backup, tagIds) }
            .then { reminders(backup) }
            .then {
                repositories.keywordHitRepository().restore(backup.keywordHits)
                snippets(backup)
            }

    /** The phone's tag id for each tag id of [backup], adding the tags the phone lacks. */
    private suspend fun tags(backup: AccountBackup): Outcome<Map<Long, Long>> {
        val repository = repositories.bookmarkRepository()
        val byName = repository.tags.first().associateBy { it.name.lowercase() }
        val ids = mutableMapOf<Long, Long>()
        for (tag in backup.tags) {
            val id =
                byName[tag.name.lowercase()]?.id
                    ?: when (val added = repository.addTag(tag.name, tag.color)) {
                        is Outcome.Success -> added.value.id
                        is Outcome.Failure -> return added
                    }
            ids[tag.id] = id
        }
        return Outcome.Success(ids)
    }

    private suspend fun bookmarks(
        backup: AccountBackup,
        tagIds: Map<Long, Long>,
    ): Outcome<Unit> {
        val repository = repositories.bookmarkRepository()
        val onPhone = repository.bookmarks.first().associateBy { it.chatId to it.messageId }
        return backup.bookmarks
            .filter { bookmark ->
                onPhone[bookmark.chatId to bookmark.messageId].let { it == null || it.savedAt < bookmark.savedAt }
            }.map { it.copy(tagIds = it.tagIds.mapNotNull(tagIds::get).toImmutableList()) }
            .firstFailure { repository.save(it) }
    }

    private suspend fun reminders(backup: AccountBackup): Outcome<Unit> {
        val repository = repositories.reminderRepository()
        val onPhone =
            repository.reminders
                .first()
                .map { it.chatId to it.messageId }
                .toSet()
        return backup.reminders
            .filter { (it.chatId to it.messageId) !in onPhone }
            .firstFailure { repository.set(it.copy(isDone = it.isDone || it.at <= now)) }
    }

    private suspend fun snippets(backup: AccountBackup): Outcome<Unit> {
        val repository = repositories.snippetRepository()
        val onPhone = repository.snippets.first()
        val names = onPhone.map { it.name.lowercase() }.toSet()
        return backup.snippets
            .filter { it.name.lowercase() !in names }
            .take((Snippet.MAX_SNIPPETS - onPhone.size).coerceAtLeast(0))
            .firstFailure { repository.add(it.name, it.text).map {} }
    }
}

/** [next] once this succeeded; this failure otherwise. */
internal inline fun <T, R> Outcome<T>.then(next: (T) -> Outcome<R>): Outcome<R> =
    when (this) {
        is Outcome.Success -> next(value)
        is Outcome.Failure -> this
    }

/** [write] on each item in turn, up to the first that fails, whose failure it answers. */
internal suspend fun <T> List<T>.firstFailure(write: suspend (T) -> Outcome<Unit>): Outcome<Unit> {
    for (item in this) {
        val written = write(item)
        if (written is Outcome.Failure) return written
    }
    return Outcome.Success(Unit)
}

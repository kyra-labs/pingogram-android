package dev.kyralabs.pingogram.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.keyword.KeywordMatcher
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.KeywordAlert
import dev.kyralabs.pingogram.core.domain.model.KeywordAlertSettings
import dev.kyralabs.pingogram.core.domain.model.KeywordHit
import dev.kyralabs.pingogram.core.domain.model.KeywordScope
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.KeywordAlertsRepository
import dev.kyralabs.pingogram.core.domain.repository.KeywordHitRepository
import dev.kyralabs.pingogram.core.tdlib.AccountSlot
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.TdlibDirectories
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * [KeywordHitRepository] on TDLib's `updateNewMessage`: each new message someone else sent, not scheduled, whose text
 * or caption has one of the user's words ([KeywordMatcher]) in a chat the settings look at, is a match. Matching runs
 * on this phone, only on what TDLib announces while Pingogram is connected (owner decision 2026-11-15); nothing is
 * sent anywhere.
 *
 * The matches of the last week are kept in a small file of the account's TDLib directory (`keyword_hits.txt`, at most
 * [MAX_HITS]), read once on the [io] dispatcher and written whole after each change, as [FileBookmarkRepository] keeps
 * bookmarks; the demo keeps them in memory. Logging out deletes them (a [TdlibUpdateCache] for that). The message's
 * text goes only to [alerts], never to the file or a log.
 */
@Suppress("LongParameterList") // The client, the settings, the chats, the clock, where it runs and its file.
@AccountScoped
class TdlibKeywordHitRepository internal constructor(
    client: TdlibClient,
    settings: Flow<KeywordAlertSettings>,
    private val chatOf: suspend (Long) -> Chat?,
    private val clock: WallClock,
    private val scope: CoroutineScope,
    private val file: File?,
    private val io: CoroutineDispatcher,
) : KeywordHitRepository,
    TdlibUpdateCache {
    @Inject
    constructor(
        client: TdlibClient,
        slot: AccountSlot,
        keywordAlerts: KeywordAlertsRepository,
        chats: ChatRepository,
        clock: WallClock,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(IO) io: CoroutineDispatcher,
    ) : this(
        client = client,
        settings = keywordAlerts.settings,
        chatOf = { id -> chats.chat(id).first() },
        clock = clock,
        scope = scope,
        file = if (slot.isDemo) null else TdlibDirectories.of(context.filesDir, slot).root.resolve(FILE_NAME),
        io = io,
    )

    private val hits = MutableStateFlow<List<KeywordHit>?>(null)
    private val mutex = Mutex()
    private val newAlerts = MutableSharedFlow<KeywordAlert>(extraBufferCapacity = ALERT_BUFFER)

    /** The settings and their matcher, read once and kept up to date while the account runs. */
    private val matching: StateFlow<Matching?> =
        settings
            .map { Matching(it, KeywordMatcher(it.words)) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        // UNDISPATCHED: subscribed before the constructor returns, as for every TdlibUpdateCache.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.updates.collect { update ->
                when {
                    update is TdApi.UpdateNewMessage -> scope.launch { consider(update.message) }
                    update.endsSession() -> scope.launch { clear() }
                }
            }
        }
    }

    override val recent: Flow<ImmutableList<KeywordHit>> =
        flow {
            load()
            emitAll(hits.filterNotNull())
        }.map { list -> list.filter { it.isRecent() }.sortedByDescending { it.date }.toImmutableList() }
            .distinctUntilChanged()

    override val alerts: Flow<KeywordAlert> = newAlerts.asSharedFlow()

    override suspend fun clearChat(chatId: Long) {
        change { list -> list.filterNot { it.chatId == chatId } }
    }

    private suspend fun consider(message: TdApi.Message) {
        val text = message.takeIf { !it.isOutgoing && it.schedulingState == null }?.content?.searchableText()
        val matching = text?.let { matching.filterNotNull().first() }?.takeUnless { it.matcher.isEmpty }
        val match = matching?.matcher?.firstMatch(checkNotNull(text)) ?: return
        val chat = chatOf(message.chatId)?.takeIf { matching.settings.where.covers(it) } ?: return
        val hit = KeywordHit(chatId = chat.id, messageId = message.id, word = match.word, date = message.date.toLong())
        change { list -> list.filterNot { it.chatId == hit.chatId && it.messageId == hit.messageId } + hit }
        newAlerts.emit(KeywordAlert(hit, checkNotNull(text), match.start, match.end, chat.isMuted))
    }

    private suspend fun load() {
        if (hits.value ==
            null
        ) {
            mutex.withLock { hits.value ?: withContext(io) { readHits(file) }.also { hits.value = it } }
        }
    }

    private suspend fun change(transform: (List<KeywordHit>) -> List<KeywordHit>) {
        mutex.withLock {
            val current = hits.value ?: withContext(io) { readHits(file) }
            val next = transform(current).filter { it.isRecent() }.sortedBy { it.date }.takeLast(MAX_HITS)
            hits.value = next
            try {
                file?.let { target -> withContext(io) { writeHits(target, next) } }
            } catch (_: IOException) {
                // Kept in memory for this run; the next change writes the file again.
            }
        }
    }

    private suspend fun clear() {
        mutex.withLock {
            hits.value = emptyList()
            withContext(io) { file?.delete() }
        }
    }

    private fun KeywordHit.isRecent(): Boolean = date >= clock.now().epochSecond - RECENT_SECONDS

    private class Matching(
        val settings: KeywordAlertSettings,
        val matcher: KeywordMatcher,
    )

    internal companion object {
        const val FILE_NAME = "keyword_hits.txt"

        /** The most matches kept: a few kilobytes. */
        const val MAX_HITS = 500

        /** How long a match is kept: a week (board 30's "7-day match counts"). */
        const val RECENT_SECONDS = 7L * 24 * 60 * 60

        private const val ALERT_BUFFER = 16
    }
}

/** Whether a message of [chat] is one these settings look at. */
internal fun KeywordScope.covers(chat: Chat): Boolean =
    when (this) {
        KeywordScope.AllChats -> true
        KeywordScope.MutedChats -> chat.isMuted
        is KeywordScope.Folders -> chat.positions.any { (it.list as? ChatListType.Folder)?.id in folderIds }
    }

/** The text keyword alerts look in: a text message's text, or a photo's, video's, GIF's, file's or song's caption. */
internal fun TdApi.MessageContent.searchableText(): String? =
    when (this) {
        is TdApi.MessageText -> text?.text
        is TdApi.MessagePhoto -> caption?.text
        is TdApi.MessageVideo -> caption?.text
        is TdApi.MessageAnimation -> caption?.text
        is TdApi.MessageDocument -> caption?.text
        is TdApi.MessageAudio -> caption?.text
        is TdApi.MessageVoiceNote -> caption?.text
        else -> null
    }?.takeIf { it.isNotBlank() }

private fun TdApi.Update.endsSession(): Boolean =
    this is TdApi.UpdateAuthorizationState &&
        (
            authorizationState is TdApi.AuthorizationStateLoggingOut ||
                authorizationState is TdApi.AuthorizationStateClosed
        )

private const val FIELDS = 4
private const val WORD_FIELD = 3

/** The matches in [file], the oldest first; none where it cannot be read. */
private fun readHits(file: File?): List<KeywordHit> {
    val lines =
        try {
            file?.takeIf { it.isFile }?.readLines().orEmpty()
        } catch (_: IOException) {
            emptyList()
        }
    return lines.mapNotNull { line ->
        val fields = line.split('\t', limit = FIELDS)
        if (fields.size < FIELDS) return@mapNotNull null
        val chatId = fields[0].toLongOrNull() ?: return@mapNotNull null
        val messageId = fields[1].toLongOrNull() ?: return@mapNotNull null
        val date = fields[2].toLongOrNull() ?: return@mapNotNull null
        KeywordHit(chatId, messageId, unescapeWord(fields[WORD_FIELD]), date)
    }
}

/** Writes [hits] whole through a temporary file, so a write cut short leaves the last good one. */
private fun writeHits(
    target: File,
    hits: List<KeywordHit>,
) {
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, "${target.name}.tmp")
    temporary.writeText(
        buildString {
            hits.forEach { append("${it.chatId}\t${it.messageId}\t${it.date}\t${escapeWord(it.word)}\n") }
        },
    )
    if (!temporary.renameTo(target)) throw IOException("Could not replace ${target.name}")
}

private fun escapeWord(word: String) = word.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

private fun unescapeWord(value: String): String =
    buildString {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '\\' && index + 1 < value.length) {
                when (value[index + 1]) {
                    't' -> append('\t')
                    'n' -> append('\n')
                    else -> append(value[index + 1])
                }
                index += 2
            } else {
                append(char)
                index++
            }
        }
    }

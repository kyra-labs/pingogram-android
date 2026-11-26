package dev.kyralabs.pingogram.core.notifications

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.AccountRepositories
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.NotesAndNamesRepository
import dev.kyralabs.pingogram.core.domain.repository.NotesLocalChatNamesRepository
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import dev.kyralabs.pingogram.core.domain.repository.awaitLoggedIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Reads the Daily digest (`REQUIREMENTS.md` FR-X23) of every account on the device that is signed in: once TDLib is
 * ready, the first pages of its main list (TDLib knows only the chats loaded so far; unread ones are among the most
 * recent), its locked chats, reminders and names on this phone, then [dailyDigestOf] with Catch-up's choice of muted
 * chats and the passcode's "Hide content". An account TDLib does not report signed in in time is left out.
 */
internal class DailyDigestReader(
    private val accounts: suspend () -> List<DigestAccountParts>,
    private val includesMuted: suspend () -> Boolean,
    private val hidesContent: suspend () -> Boolean,
    private val clock: WallClock,
) {
    @Inject
    constructor(
        graphs: AccountGraphs,
        chatListSettings: ChatListSettingsRepository,
        passcode: PasscodeRepository,
        notesAndNames: NotesAndNamesRepository,
        clock: WallClock,
    ) : this(
        accounts = { graphs.accounts.first().map { DigestAccountParts(it.repositories, notesAndNames) } },
        includesMuted = { chatListSettings.settings.first().catchUpIncludesMuted },
        hidesContent = { passcode.settings.first().let { it.isSet && it.hideContent } },
        clock = clock,
    )

    suspend fun read(): DailyDigest {
        val parts = accounts().mapNotNull { it.read() }
        val now = clock.now()
        val zone = clock.zone()
        return dailyDigestOf(parts, now.atZone(zone).toLocalDate(), zone, includesMuted(), hidesContent())
    }

    private suspend fun DigestAccountParts.read(): DigestAccount? {
        if (withTimeoutOrNull(LOGIN_TIMEOUT) { loggedIn() } != true) return null
        return DigestAccount(chats(), lockedChatIds(), reminders(), localNames())
    }

    internal companion object {
        /** How long TDLib may take to open its database and report the login. */
        val LOGIN_TIMEOUT = 30.seconds

        /** The main list's pages read at most: the chats with unread messages are among the most recent. */
        const val MAX_PAGES = 4
    }
}

/**
 * What the digest reads of one account: whether it is signed in ([loggedIn], waiting for TDLib to say), the main list's
 * chats once its first pages are loaded ([chats]), its locked chats, reminders and names on this phone.
 */
internal class DigestAccountParts(
    val loggedIn: suspend () -> Boolean,
    val chats: suspend () -> List<Chat>,
    val lockedChatIds: suspend () -> Set<Long> = { emptySet() },
    val reminders: suspend () -> List<MessageReminder> = { emptyList() },
    val localNames: suspend () -> Map<Long, String> = { emptyMap() },
) {
    constructor(repositories: AccountRepositories, notesAndNames: NotesAndNamesRepository) : this(
        loggedIn = { repositories.authRepository().awaitLoggedIn() },
        chats = { repositories.chatRepository().firstPagesOfMainList() },
        lockedChatIds = { repositories.chatLockRepository().lockedChatIds.first() },
        reminders = { repositories.reminderRepository().reminders.first() },
        localNames = {
            NotesLocalChatNamesRepository(repositories.chatNoteRepository(), notesAndNames).names.first()
        },
    )
}

/** The main list after loading up to [DailyDigestReader.MAX_PAGES] pages: TDLib knows only the chats loaded so far. */
private suspend fun ChatRepository.firstPagesOfMainList(): List<Chat> {
    var pages = 0
    do {
        // Each page reaches the list's flow by itself.
        val more = (loadChats(ChatListType.Main) as? Outcome.Success)?.value == true
    } while (more && ++pages < DailyDigestReader.MAX_PAGES)
    return chatList(ChatListType.Main).first()
}

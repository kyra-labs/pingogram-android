package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import androidx.core.graphics.drawable.IconCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.AccountRepositories
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.BirthdayReminderRepository
import dev.kyralabs.pingogram.core.domain.repository.awaitLoggedIn
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.resolve
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Rings a birthday reminder (`REQUIREMENTS.md` FR-X18, scheduled by [WorkManagerBirthdayReminderWork]): if the reminder
 * still stands for the same day and today is that day, once TDLib is ready, posts its notification
 * ([BirthdayReminderNotificationBuilder]) with the contact's name and avatar, named nothing for a locked chat. Whether
 * it rang or not, a standing reminder's next ring is scheduled a year on. Nothing is retried.
 */
@HiltWorker
class BirthdayReminderWorker
    internal constructor(
        context: Context,
        parameters: WorkerParameters,
        private val accounts: suspend (slot: Int) -> BirthdayAccountParts?,
        private val notifications: BirthdayReminderNotificationBuilder,
        private val clock: WallClock,
        private val schedule: (slot: Int, reminder: BirthdayReminder, at: Instant) -> Unit,
        private val iconOf: suspend (slot: Int, user: User) -> IconCompat? = { _, _ -> null },
    ) : CoroutineWorker(context, parameters) {
        @AssistedInject
        internal constructor(
            @Assisted context: Context,
            @Assisted parameters: WorkerParameters,
            graphs: AccountGraphs,
            notifications: BirthdayReminderNotificationBuilder,
            clock: WallClock,
            avatars: AvatarIcons,
        ) : this(
            context = context,
            parameters = parameters,
            accounts = { slot ->
                graphs.accounts
                    .first()
                    .firstOrNull { it.slot == slot }
                    ?.repositories
                    ?.let(::BirthdayAccountParts)
            },
            notifications = notifications,
            clock = clock,
            schedule = { slot, reminder, at -> WorkManagerBirthdayReminderWork(context).enqueue(slot, reminder, at) },
            // Another account's photos are not the shown account's files: its avatars are initials.
            iconOf = { slot, user ->
                val name = user.fullName().resolve(context.resources)
                avatars.icon(user.profilePhoto.takeIf { slot == graphs.activeSlot.value }, user.id, name)
            },
        )

        override suspend fun doWork(): Result {
            val slot = inputData.getInt(KEY_SLOT, -1)
            val asked =
                BirthdayReminder(
                    userId = inputData.getLong(KEY_USER, 0),
                    day = inputData.getInt(KEY_DAY, 0),
                    month = inputData.getInt(KEY_MONTH, 0),
                )
            accounts(slot)?.let { account -> ringIfStanding(slot, account, asked) }
            return Result.success()
        }

        /** Rings [asked] if it still stands and today is its day, then schedules its next ring. */
        private suspend fun ringIfStanding(
            slot: Int,
            account: BirthdayAccountParts,
            asked: BirthdayReminder,
        ) {
            if (asked !in account.reminders.reminders.first()) return
            val now = clock.now()
            if (isBirthdayOf(asked, now.atZone(clock.zone()).toLocalDate())) ring(slot, account, asked.userId, now)
            schedule(slot, asked, nextBirthdayRing(asked, now, clock.zone()))
        }

        private suspend fun ring(
            slot: Int,
            account: BirthdayAccountParts,
            userId: Long,
            now: Instant,
        ) {
            // TDLib did not report the login in time: this year's ring is missed.
            if (withTimeoutOrNull(LOGIN_TIMEOUT) { account.auth.awaitLoggedIn() } != true) return
            val locked = userId in account.lockedChatIds()
            val user = if (locked) null else account.user(userId)
            notifications.post(
                slot = slot,
                userId = userId,
                name = user?.fullName()?.resolve(applicationContext.resources),
                locked = locked,
                at = now,
                icon = user?.let { iconOf(slot, it) },
            )
        }

        internal companion object {
            /** How long TDLib may take to open its database and report the login. */
            val LOGIN_TIMEOUT = 30.seconds

            private const val KEY_SLOT = "slot"
            private const val KEY_USER = "user"
            private const val KEY_DAY = "day"
            private const val KEY_MONTH = "month"

            /** The input of the work that rings [reminder] of the account in [slot]. */
            fun inputOf(
                slot: Int,
                reminder: BirthdayReminder,
            ): Data =
                workDataOf(
                    KEY_SLOT to slot,
                    KEY_USER to reminder.userId,
                    KEY_DAY to reminder.day,
                    KEY_MONTH to reminder.month,
                )
        }
    }

/** What ringing a birthday reminder needs of its account: its reminders, its login, its locked chats and its users. */
internal class BirthdayAccountParts(
    val reminders: BirthdayReminderRepository,
    val auth: AuthRepository,
    val lockedChatIds: suspend () -> Set<Long> = { emptySet() },
    val user: suspend (userId: Long) -> User? = { null },
) {
    constructor(repositories: AccountRepositories) : this(
        reminders = repositories.birthdayReminderRepository(),
        auth = repositories.authRepository(),
        lockedChatIds = { repositories.chatLockRepository().lockedChatIds.first() },
        user = { id -> (repositories.userRepository().getUser(id) as? Outcome.Success)?.value },
    )
}

package dev.kyralabs.pingogram.feature.contacts.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.AddContactResult
import dev.kyralabs.pingogram.core.domain.model.Country
import dev.kyralabs.pingogram.core.domain.model.EditableContact
import dev.kyralabs.pingogram.core.domain.model.PhoneNumberInfo
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.ContactRepository
import dev.kyralabs.pingogram.core.domain.repository.CountryRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** A contact as the view models see it, named [firstName] [lastName]. */
fun contact(
    id: Long,
    firstName: String = "User $id",
    lastName: String = "",
    status: UserStatus = UserStatus.Empty,
    usernames: List<String> = emptyList(),
    phoneNumber: String = "",
    isContact: Boolean = true,
    type: UserType = UserType.Regular,
) = User(
    id = id,
    firstName = firstName,
    lastName = lastName,
    usernames = usernames.toImmutableList(),
    phoneNumber = phoneNumber,
    status = status,
    profilePhoto = null,
    isVerified = false,
    isScam = false,
    isFake = false,
    isPremium = false,
    isContact = isContact,
    isMutualContact = false,
    isSupport = false,
    type = type,
    haveAccess = true,
)

/**
 * [ContactRepository] for view model tests: [contactList] is what [contacts] lists (or [listFailure] instead), every
 * call is recorded in [calls], and each answer can be replaced.
 */
class FakeContactRepository : ContactRepository {
    val contactList = MutableStateFlow<ImmutableList<User>>(persistentListOf())
    var listFailure: DomainError? = null

    /** Runs before [contacts] lists anything, e.g. to wait on a `CompletableDeferred` for the loading state. */
    var beforeList: suspend () -> Unit = {}
    val calls = mutableListOf<String>()

    var onSearch: suspend (String) -> Outcome<ImmutableList<Long>> = { Outcome.Success(persistentListOf()) }
    var onAddByPhoneNumber: suspend (String, String, String) -> Outcome<AddContactResult> =
        { _, _, _ -> Outcome.Success(AddContactResult.Added(7)) }
    var onAddByUsername: suspend (String, String, String) -> Outcome<Long> = { _, _, _ -> Outcome.Success(7) }
    var onEditableContact: suspend (Long) -> Outcome<EditableContact> =
        { id -> Outcome.Success(EditableContact(contact(id, "Grace", "Hopper"), canSharePhoneNumber = false)) }
    var onSave: suspend (Long, String, String, Boolean) -> Outcome<Unit> = { _, _, _, _ -> Outcome.Success(Unit) }
    var onDelete: suspend (Long) -> Outcome<Unit> = { Outcome.Success(Unit) }
    var onPrivateChatId: suspend (Long) -> Outcome<Long> = { userId -> Outcome.Success(userId * 10) }
    var link = "https://t.me/dl"

    override fun contacts(): Flow<Outcome<ImmutableList<User>>> =
        flow {
            calls += "contacts"
            beforeList()
            val failure = listFailure
            if (failure != null) {
                emit(Outcome.Failure(failure))
            } else {
                contactList.collect { emit(Outcome.Success(it)) }
            }
        }

    override suspend fun search(query: String): Outcome<ImmutableList<Long>> {
        calls += "search:$query"
        return onSearch(query)
    }

    override suspend fun addByPhoneNumber(
        phoneNumber: String,
        firstName: String,
        lastName: String,
    ): Outcome<AddContactResult> {
        calls += "addByPhoneNumber:$phoneNumber:$firstName:$lastName"
        return onAddByPhoneNumber(phoneNumber, firstName, lastName)
    }

    override suspend fun addByUsername(
        username: String,
        firstName: String,
        lastName: String,
    ): Outcome<Long> {
        calls += "addByUsername:$username:$firstName:$lastName"
        return onAddByUsername(username, firstName, lastName)
    }

    override suspend fun editableContact(userId: Long): Outcome<EditableContact> {
        calls += "editableContact:$userId"
        return onEditableContact(userId)
    }

    override suspend fun save(
        userId: Long,
        firstName: String,
        lastName: String,
        sharePhoneNumber: Boolean,
    ): Outcome<Unit> {
        calls += "save:$userId:$firstName:$lastName:$sharePhoneNumber"
        return onSave(userId, firstName, lastName, sharePhoneNumber)
    }

    override suspend fun delete(userId: Long): Outcome<Unit> {
        calls += "delete:$userId"
        return onDelete(userId)
    }

    override suspend fun privateChatId(userId: Long): Outcome<Long> {
        calls += "privateChatId:$userId"
        return onPrivateChatId(userId)
    }

    override suspend fun inviteLink(): String {
        calls += "inviteLink"
        return link
    }
}

/** [UserRepository] with the current user [myUserId] among [userMap]. */
class FakeUserRepository : UserRepository {
    override val myUserId = MutableStateFlow<Long?>(null)
    val userMap = MutableStateFlow<ImmutableMap<Long, User>>(persistentMapOf())

    override val users: Flow<ImmutableMap<Long, User>> = userMap

    override fun user(id: Long): Flow<User?> = userMap.map { it[id] }

    override suspend fun getUser(id: Long): Outcome<User> =
        userMap.value[id]?.let { Outcome.Success(it) } ?: Outcome.Failure(DomainError.NotFound)
}

/** [CountryRepository] that knows the numbers starting with 44 (the United Kingdom) and 1 (the United States). */
class FakeCountryRepository : CountryRepository {
    val lookups = mutableListOf<String>()

    override suspend fun countries(): Outcome<List<Country>> = Outcome.Success(listOf(UK, US))

    override suspend fun currentCountryCode(): Outcome<String> = Outcome.Success("GB")

    override suspend fun phoneNumberInfo(phoneNumberPrefix: String): Outcome<PhoneNumberInfo> {
        lookups += phoneNumberPrefix
        val digits = phoneNumberPrefix.filter(Char::isDigit)
        val country =
            when {
                digits.startsWith("44") -> UK
                digits.startsWith("1") -> US
                else -> null
            }
        return Outcome.Success(PhoneNumberInfo(country, country?.callingCodes?.first().orEmpty(), "", false))
    }

    companion object {
        val UK = Country("GB", "United Kingdom", "United Kingdom", "🇬🇧", listOf("44"), isHidden = false)
        val US = Country("US", "United States", "United States", "🇺🇸", listOf("1"), isHidden = false)
    }
}

/** A wall clock at [now] in UTC; [setTime] moves it and emits the new time as the minute. */
class FakeWallClock(
    now: Instant,
) : WallClock {
    private var current = now

    override val minutes = MutableStateFlow(now)

    override fun now(): Instant = current

    override fun zone(): ZoneId = ZoneOffset.UTC

    fun setTime(time: Instant) {
        current = time
        minutes.value = time
    }
}

/** Fixed English patterns, as the platform gives them for `Locale.US`. */
class FakeTimePatterns : TimePatterns {
    override fun is24HourClock(): Boolean = false

    override fun bestPattern(
        locale: Locale,
        skeleton: String,
    ): String = PATTERNS[skeleton] ?: error("No pattern for $skeleton")

    private companion object {
        val PATTERNS = mapOf("Hm" to "HH:mm", "hm" to "h:mm a", "MMMd" to "MMM d", "yMd" to "M/d/y")
    }
}

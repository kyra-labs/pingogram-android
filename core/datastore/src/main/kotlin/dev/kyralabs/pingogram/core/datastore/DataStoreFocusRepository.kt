package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import dev.kyralabs.pingogram.core.domain.model.FocusUntil
import dev.kyralabs.pingogram.core.domain.model.ManualFocus
import dev.kyralabs.pingogram.core.domain.repository.FocusRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [FocusRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]), on this phone only (board
 * 31). Profiles are one line each (id, name in Base64, icon, folders, chats, schedule, calls), in their order; what
 * cannot be read reads as nothing, and a file that cannot be read as no profiles.
 */
@Singleton
class DataStoreFocusRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : FocusRepository {
        private val data: Flow<Preferences> =
            dataStore.data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

        override val profiles: Flow<ImmutableList<FocusProfile>> =
            data.map { profilesOf(it) }.distinctUntilChanged()

        override val manual: Flow<ManualFocus?> =
            data.map { it[MANUAL]?.let(::decodeManual) }.distinctUntilChanged()

        override val lastUsedId: Flow<Long?> = data.map { it[LAST_USED] }.distinctUntilChanged()

        override suspend fun saveProfile(profile: FocusProfile): Outcome<Long> =
            write { preferences ->
                val profiles = profilesOf(preferences).toMutableList()
                val id =
                    if (profile.id > 0) {
                        profile.id
                    } else {
                        (preferences[NEXT_ID] ?: 1L).also { preferences[NEXT_ID] = it + 1 }
                    }
                val saved = profile.copy(id = id)
                val index = profiles.indexOfFirst { it.id == id }
                if (index >= 0) profiles[index] = saved else profiles += saved
                preferences[PROFILES] = profiles.joinToString(LINE) { encode(it) }
                id
            }

        override suspend fun deleteProfile(id: Long): Outcome<Unit> =
            write { preferences ->
                preferences[PROFILES] = profilesOf(preferences).filter { it.id != id }.joinToString(LINE) { encode(it) }
                if (preferences[MANUAL]?.let(::decodeManual)?.profileId == id) preferences.remove(MANUAL)
                if (preferences[LAST_USED] == id) preferences.remove(LAST_USED)
            }

        override suspend fun setManual(manual: ManualFocus?): Outcome<Unit> =
            write { preferences ->
                if (manual == null) {
                    preferences.remove(MANUAL)
                } else {
                    preferences[MANUAL] = encode(manual)
                    manual.profileId?.let { preferences[LAST_USED] = it }
                }
            }

        private suspend fun <T> write(change: (MutablePreferences) -> T): Outcome<T> =
            try {
                var result: T? = null
                dataStore.edit { result = change(it) }
                @Suppress("UNCHECKED_CAST") // Set by the edit, which ran once it returned.
                Outcome.Success(result as T)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Focus not saved: ${error.message}"))
            }
    }

private fun profilesOf(preferences: Preferences): ImmutableList<FocusProfile> =
    preferences[PROFILES]
        .orEmpty()
        .split(LINE)
        .mapNotNull(::decodeProfile)
        .toImmutableList()

internal fun encode(profile: FocusProfile): String =
    listOf(
        profile.id.toString(),
        Base64.getEncoder().encodeToString(profile.name.toByteArray()),
        profile.icon.name,
        profile.allowedFolderIds.joinToString(LIST),
        profile.allowedChatIds.joinToString(LIST),
        profile.schedule
            ?.let { schedule ->
                listOf(
                    schedule.days.sumOf { 1 shl (it.value - 1) },
                    schedule.startMinute,
                    schedule.endMinute,
                ).joinToString(LIST)
            }.orEmpty(),
        if (profile.allowsCallsFromContacts) "1" else "0",
    ).joinToString(FIELD)

internal fun decodeProfile(line: String): FocusProfile? {
    val fields = line.split(FIELD).takeIf { it.size == PROFILE_FIELDS } ?: return null
    val (id, name, icon) = fields
    val (folders, chats) = fields.drop(FOLDERS_FIELD)
    val (schedule, calls) = fields.drop(SCHEDULE_FIELD)
    return runCatching {
        FocusProfile(
            id = id.toLong(),
            name = String(Base64.getDecoder().decode(name)),
            icon = FocusIcon.valueOf(icon),
            allowedFolderIds = numbers(folders).map { it.toInt() }.toImmutableList(),
            allowedChatIds = numbers(chats).toImmutableList(),
            schedule = schedule.takeIf { it.isNotEmpty() }?.let(::decodeSchedule),
            allowsCallsFromContacts = calls == "1",
        )
    }.getOrNull()
}

/** "days,start,end": the days as bits, Monday the lowest. */
private fun decodeSchedule(text: String): FocusSchedule {
    val (days, start, end) = text.split(LIST).map { it.toInt() }
    return FocusSchedule(
        days = DayOfWeek.entries.filter { days and (1 shl (it.value - 1)) != 0 }.toImmutableSet(),
        startMinute = start,
        endMinute = end,
    )
}

internal fun encode(manual: ManualFocus): String {
    val until =
        when (val until = manual.until) {
            FocusUntil.ScheduleChange -> "schedule"
            FocusUntil.TurnedOff -> "off"
            is FocusUntil.Time -> until.epochSeconds.toString()
        }
    return listOf(manual.profileId?.toString().orEmpty(), manual.setAt.toString(), until).joinToString(FIELD)
}

internal fun decodeManual(text: String): ManualFocus? =
    text
        .split(FIELD)
        .takeIf { it.size == MANUAL_FIELDS }
        ?.let { (profileId, setAt, value) ->
            val until =
                when (value) {
                    "schedule" -> FocusUntil.ScheduleChange
                    "off" -> FocusUntil.TurnedOff
                    else -> value.toLongOrNull()?.let(FocusUntil::Time)
                }
            val at = setAt.toLongOrNull()
            if (until != null && at != null) ManualFocus(profileId.toLongOrNull(), at, until) else null
        }

private fun numbers(text: String): List<Long> = text.split(LIST).mapNotNull { it.toLongOrNull() }

// The keys in the settings file; renaming one loses what users saved under it.
private val PROFILES = stringPreferencesKey("focus.profiles")
private val MANUAL = stringPreferencesKey("focus.manual")
private val LAST_USED = longPreferencesKey("focus.last_used")
private val NEXT_ID = longPreferencesKey("focus.next_id")
private const val LINE = "\n"
private const val FIELD = "|"
private const val LIST = ","
private const val PROFILE_FIELDS = 7

/** Where a profile's folders start among its fields, its chats following; and its schedule, its calls following. */
private const val FOLDERS_FIELD = 3
private const val SCHEDULE_FIELD = 5
private const val MANUAL_FIELDS = 3

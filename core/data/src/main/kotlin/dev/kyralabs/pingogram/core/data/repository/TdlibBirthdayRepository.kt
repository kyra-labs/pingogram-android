package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toBirthdate
import dev.kyralabs.pingogram.core.domain.model.CloseBirthday
import dev.kyralabs.pingogram.core.domain.repository.BirthdayRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * [BirthdayRepository] on TDLib: the contacts of `updateContactCloseBirthdays`, a [TdlibUpdateCache], as TDLib
 * announces them at start and when they change and has no function that reads them; logging out forgets them.
 */
@AccountScoped
class TdlibBirthdayRepository
    @Inject
    constructor(
        client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : BirthdayRepository,
        TdlibUpdateCache {
        private val store =
            UpdateStore(client, scope, BIRTHDAY_UPDATES, NONE) { state, update ->
                if (update is TdApi.UpdateContactCloseBirthdays) update.toCloseBirthdays() else state
            }

        override val closeBirthdays: StateFlow<ImmutableList<CloseBirthday>> = store.state
    }

private val NONE: ImmutableList<CloseBirthday> = persistentListOf()

private fun TdApi.UpdateContactCloseBirthdays.toCloseBirthdays(): ImmutableList<CloseBirthday> =
    closeBirthdayUsers.map { CloseBirthday(it.userId, it.birthdate.toBirthdate()) }.toImmutableList()

/** The updates its store's reducer handles. */
private val BIRTHDAY_UPDATES: Set<KClass<out TdApi.Update>> = setOf(TdApi.UpdateContactCloseBirthdays::class)

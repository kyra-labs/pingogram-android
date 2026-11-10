package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Birthdate
import org.drinkless.tdlib.TdApi

/** A birthday as the domain keeps it; a year of 0 means the user shares only the day and month. */
internal fun TdApi.Birthdate.toBirthdate(): Birthdate = Birthdate(day, month, year.takeIf { it > 0 })

/** A birthday as TDLib takes it, its year 0 where the user shares only the day and month. */
internal fun Birthdate.toTdBirthdate(): TdApi.Birthdate = TdApi.Birthdate(day, month, year ?: 0)

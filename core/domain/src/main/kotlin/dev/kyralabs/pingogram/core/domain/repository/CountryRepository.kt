package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Country
import dev.kyralabs.pingogram.core.domain.model.PhoneNumberInfo

/** Countries and phone number formats, for phone number entry (login, contacts, changing the number). */
interface CountryRepository {
    /** Every country with its calling codes, in TDLib's order. */
    suspend fun countries(): Outcome<List<Country>>

    /** The ISO code of the country the user is in now, guessed from the IP address. */
    suspend fun currentCountryCode(): Outcome<String>

    /** What is known about a phone number from its first digits ([phoneNumberPrefix], with or without `+`). */
    suspend fun phoneNumberInfo(phoneNumberPrefix: String): Outcome<PhoneNumberInfo>
}

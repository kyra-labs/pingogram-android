package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toCountry
import dev.kyralabs.pingogram.core.data.mapper.toPhoneNumberInfo
import dev.kyralabs.pingogram.core.domain.model.Country
import dev.kyralabs.pingogram.core.domain.model.PhoneNumberInfo
import dev.kyralabs.pingogram.core.domain.repository.CountryRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/** [CountryRepository] on TDLib's country list, which works before login too. */
@AccountScoped
class TdlibCountryRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : CountryRepository {
        override suspend fun countries(): Outcome<List<Country>> =
            client.request(TdApi.GetCountries()).map { result -> result.countries.orEmpty().map { it.toCountry() } }

        override suspend fun currentCountryCode(): Outcome<String> =
            client.request(TdApi.GetCountryCode()).map { it.text.orEmpty() }

        override suspend fun phoneNumberInfo(phoneNumberPrefix: String): Outcome<PhoneNumberInfo> =
            client.request(TdApi.GetPhoneNumberInfo(phoneNumberPrefix)).map { it.toPhoneNumberInfo() }
    }

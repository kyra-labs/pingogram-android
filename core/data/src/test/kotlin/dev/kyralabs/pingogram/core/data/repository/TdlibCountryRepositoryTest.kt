package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TdlibCountryRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibCountryRepository(client)

    @Test
    fun `countries are mapped in TDLib's order`() =
        runTest {
            client.on(
                TdApi.GetCountries::class,
                TdResult.Success(
                    TdApi.Countries(
                        arrayOf(
                            TdApi.CountryInfo("DE", "Germany", "Germany", "", false, arrayOf("49")),
                            TdApi.CountryInfo("GB", "United Kingdom", "United Kingdom", "", false, arrayOf("44")),
                        ),
                    ),
                ),
            )

            val countries = (repository.countries() as Outcome.Success).value

            assertEquals(
                listOf("DE" to listOf("49"), "GB" to listOf("44")),
                countries.map { it.isoCode to it.callingCodes },
            )
        }

    @Test
    fun `currentCountryCode returns TDLib's guess`() =
        runTest {
            client.on(TdApi.GetCountryCode::class, TdResult.Success(TdApi.Text("GB")))

            assertEquals(Outcome.Success("GB"), repository.currentCountryCode())
        }

    @Test
    fun `phoneNumberInfo sends the prefix and maps the answer`() =
        runTest {
            client.on(
                TdApi.GetPhoneNumberInfo::class,
                TdResult.Success(TdApi.PhoneNumberInfo(null, "44", "7911 ------", false)),
            )

            val info = (repository.phoneNumberInfo("+447911") as Outcome.Success).value

            assertEquals("+447911", client.sentOf<TdApi.GetPhoneNumberInfo>().single().phoneNumberPrefix)
            assertEquals("44" to "7911 ------", info.callingCode to info.formattedNumber)
        }

    @Test
    fun `failures are mapped`() =
        runTest {
            client.on(TdApi.GetCountries::class, TdResult.Failure(TdError(500, "Request aborted")))

            assertEquals(Outcome.Failure(DomainError.Unknown(500, "Request aborted")), repository.countries())
        }
}

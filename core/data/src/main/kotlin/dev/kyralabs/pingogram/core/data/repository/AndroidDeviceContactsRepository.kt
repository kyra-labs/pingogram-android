package dev.kyralabs.pingogram.core.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PhoneContact
import dev.kyralabs.pingogram.core.domain.repository.DeviceContactsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DeviceContactsRepository] on Android's contacts provider (`ContactsContract`), with `READ_CONTACTS`: one query of
 * the phone numbers (`Phone.CONTENT_URI`, only the contact's id, display name and number columns), on [ioDispatcher].
 * Numbers become international ([internationalNumber]): the provider's own normalized number, else Android's E.164
 * form for the country of the network, the SIM or the language, else the digits as entered. Nothing is cached.
 */
@Singleton
class AndroidDeviceContactsRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    ) : DeviceContactsRepository {
        override fun canRead(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED

        override suspend fun read(): Outcome<List<PhoneContact>> {
            if (!canRead()) return Outcome.Failure(DomainError.Unauthorized)
            return withContext(ioDispatcher) {
                try {
                    Outcome.Success(query(countryIso()))
                } catch (_: SecurityException) {
                    // Taken back between the check and the query.
                    Outcome.Failure(DomainError.Unauthorized)
                }
            }
        }

        private fun query(country: String): List<PhoneContact> {
            val contacts = LinkedHashMap<String, PhoneContact>()
            context.contentResolver.query(Phone.CONTENT_URI, PROJECTION, null, null, null)?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID)
                val name = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
                val number = cursor.getColumnIndexOrThrow(Phone.NUMBER)
                val normalized = cursor.getColumnIndexOrThrow(Phone.NORMALIZED_NUMBER)
                while (cursor.moveToNext()) {
                    val phone = internationalNumber(cursor.getString(number), cursor.getString(normalized), country)
                    // One entry per number: the first contact that has it.
                    if (phone == null || phone in contacts) continue
                    val displayName =
                        cursor
                            .getString(name)
                            ?.trim()
                            .orEmpty()
                            .ifEmpty { phone }
                    contacts[phone] = PhoneContact("${cursor.getLong(id)}:$phone", displayName, phone)
                }
            }
            return contacts.values.toList()
        }

        /** The ISO country the device is in, as its network, its SIM or the language tells; for local numbers. */
        private fun countryIso(): String {
            val telephony = context.getSystemService(TelephonyManager::class.java)
            return listOfNotNull(telephony?.networkCountryIso, telephony?.simCountryIso, Locale.getDefault().country)
                .firstOrNull(String::isNotBlank)
                .orEmpty()
                .uppercase(Locale.ROOT)
        }

        private companion object {
            val PROJECTION =
                arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.NORMALIZED_NUMBER)
        }
    }

/**
 * [number] (as entered in the phone's contacts) in international form: [normalized], the provider's E.164 number,
 * if it has one; else Android's E.164 form for [country]; else `+` and its digits if it was entered with `+`, or just
 * its digits. Null for a number without digits.
 */
internal fun internationalNumber(
    number: String?,
    normalized: String?,
    country: String,
): String? {
    val digits = number?.filter(Char::isDigit).orEmpty()
    return when {
        !normalized.isNullOrBlank() -> normalized
        digits.isEmpty() -> null
        country.isNotEmpty() -> PhoneNumberUtils.formatNumberToE164(number, country) ?: plainNumber(number, digits)
        else -> plainNumber(number, digits)
    }
}

private fun plainNumber(
    number: String?,
    digits: String,
): String = if (number?.trim()?.startsWith("+") == true) "+$digits" else digits

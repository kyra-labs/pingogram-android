package dev.kyralabs.pingogram.core.ui.text

import android.content.res.Resources
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ErrorTextTest {
    private val resources: Resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun durationIsRoundedUpToTheLargestWholeUnit() {
        val texts = listOf(0L, 1, 42, 60, 61, 299, 3_600, 3_601).map { durationText(resources, it) }

        assertEquals(
            listOf("0 seconds", "1 second", "42 seconds", "1 minute", "2 minutes", "5 minutes", "1 hour", "2 hours"),
            texts,
        )
    }

    @Test
    fun floodWaitSaysHowLongToWait() {
        assertEquals(
            "Too many attempts. Try again in 5 minutes.",
            errorMessage(resources, DomainError.FloodWait(299), showDetails = false),
        )
    }

    @Test
    fun tdlibTextOfAnUnknownErrorAppearsOnlyWithDetails() {
        val error = DomainError.Unknown(500, "SOMETHING_NEW")

        assertEquals("Something went wrong. Please try again.", errorMessage(resources, error, showDetails = false))
        assertEquals("Something went wrong (500: SOMETHING_NEW).", errorMessage(resources, error, showDetails = true))
    }

    @Test
    fun everyErrorButSilentHasAText() {
        val errors =
            listOf(
                DomainError.Network,
                DomainError.Unauthorized,
                DomainError.NotFound,
                DomainError.AlreadyRequested,
                DomainError.AppUpdateRequired,
                DomainError.InputRejected,
                DomainError.PhoneNumberInvalid,
                DomainError.PhoneNumberBanned,
                DomainError.PhoneNumberFlood,
                DomainError.CodeInvalid,
                DomainError.CodeExpired,
                DomainError.PasswordInvalid,
                DomainError.FirstNameInvalid,
                DomainError.LastNameInvalid,
                DomainError.EmailInvalid,
                DomainError.EmailNotAllowed,
                DomainError.PasswordRecoveryUnavailable,
                DomainError.PasswordRecoveryExpired,
                DomainError.TooManyPinnedChats,
                DomainError.PremiumRequired,
                DomainError.UsernameTaken,
                DomainError.UsernameInvalid,
                DomainError.UsernameForSale,
                DomainError.BioTooLong,
                DomainError.PhotoInvalid,
                DomainError.TitleInvalid,
                DomainError.TooManyChats,
                DomainError.TooManyPublicLinks,
                DomainError.AccountRestricted,
                DomainError.InviteLinkInvalid,
                DomainError.ChatFull,
                DomainError.QrLoginExpired,
            )

        val texts = errors.map { errorMessage(resources, it, showDetails = false) }

        assertTrue(texts.none { it.isBlank() || it == resources.getString(R.string.error_unknown) })
        assertEquals(errors.size, texts.toSet().size)
        assertEquals("", errorMessage(resources, DomainError.Silent, showDetails = true))
    }
}

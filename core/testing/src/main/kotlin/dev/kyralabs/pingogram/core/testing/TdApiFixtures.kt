package dev.kyralabs.pingogram.core.testing

import org.drinkless.tdlib.TdApi

/**
 * Builders for TdApi objects in tests (`docs/TESTING.md`), with harmless defaults: test-DC phone numbers
 * (`+99966…`) and `example.org` addresses. TdApi's `toString()` is native, so never print these in a JVM test.
 */
object TdApiFixtures {
    const val PHONE_NUMBER = "+99966 2 1234"

    fun waitTdlibParameters() = TdApi.AuthorizationStateWaitTdlibParameters()

    fun waitPhoneNumber() = TdApi.AuthorizationStateWaitPhoneNumber()

    fun waitPremiumPurchase(
        storeProductId: String = "premium.monthly",
        premiumDayCount: Int = 30,
        supportEmailAddress: String = "support@example.org",
        supportEmailSubject: String = "Login",
    ) = TdApi.AuthorizationStateWaitPremiumPurchase(
        storeProductId,
        premiumDayCount,
        supportEmailAddress,
        supportEmailSubject,
    )

    fun waitEmailAddress(
        allowAppleId: Boolean = false,
        allowGoogleId: Boolean = false,
    ) = TdApi.AuthorizationStateWaitEmailAddress(allowAppleId, allowGoogleId)

    fun waitEmailCode(
        emailAddressPattern: String = "a***@example.org",
        codeLength: Int = 6,
        allowAppleId: Boolean = false,
        allowGoogleId: Boolean = false,
        emailAddressResetState: TdApi.EmailAddressResetState? = null,
    ) = TdApi.AuthorizationStateWaitEmailCode(
        allowAppleId,
        allowGoogleId,
        TdApi.EmailAddressAuthenticationCodeInfo(emailAddressPattern, codeLength),
        emailAddressResetState,
    )

    fun waitCode(codeInfo: TdApi.AuthenticationCodeInfo = authenticationCodeInfo()) =
        TdApi.AuthorizationStateWaitCode(codeInfo)

    fun authenticationCodeInfo(
        phoneNumber: String = PHONE_NUMBER,
        type: TdApi.AuthenticationCodeType = TdApi.AuthenticationCodeTypeSms(5),
        nextType: TdApi.AuthenticationCodeType? = null,
        timeoutSeconds: Int = 60,
    ) = TdApi.AuthenticationCodeInfo(phoneNumber, type, nextType, timeoutSeconds)

    fun waitOtherDeviceConfirmation(link: String = "tg://login?token=dGVzdA") =
        TdApi.AuthorizationStateWaitOtherDeviceConfirmation(link)

    fun waitRegistration(
        termsOfService: TdApi.TermsOfService = TdApi.TermsOfService(formattedText("Terms of Service"), 0, true),
    ) = TdApi.AuthorizationStateWaitRegistration(termsOfService)

    fun waitPassword(
        passwordHint: String = "",
        hasRecoveryEmailAddress: Boolean = false,
        hasPassportData: Boolean = false,
        recoveryEmailAddressPattern: String = "",
    ) = TdApi.AuthorizationStateWaitPassword(
        passwordHint,
        hasRecoveryEmailAddress,
        hasPassportData,
        recoveryEmailAddressPattern,
    )

    fun ready() = TdApi.AuthorizationStateReady()

    fun loggingOut() = TdApi.AuthorizationStateLoggingOut()

    fun closing() = TdApi.AuthorizationStateClosing()

    fun closed() = TdApi.AuthorizationStateClosed()

    /** One instance of every `AuthorizationState` of the pinned TDLib version, in td_api.tl order. */
    fun allAuthorizationStates(): List<TdApi.AuthorizationState> =
        listOf(
            waitTdlibParameters(),
            waitPhoneNumber(),
            waitPremiumPurchase(),
            waitEmailAddress(),
            waitEmailCode(),
            waitCode(),
            waitOtherDeviceConfirmation(),
            waitRegistration(),
            waitPassword(),
            ready(),
            loggingOut(),
            closing(),
            closed(),
        )

    /** One instance of every `ConnectionState`, in td_api.tl order. */
    fun allConnectionStates(): List<TdApi.ConnectionState> =
        listOf(
            TdApi.ConnectionStateWaitingForNetwork(),
            TdApi.ConnectionStateConnectingToProxy(),
            TdApi.ConnectionStateConnecting(),
            TdApi.ConnectionStateUpdating(),
            TdApi.ConnectionStateReady(),
        )

    fun authorizationStateUpdate(state: TdApi.AuthorizationState) = TdApi.UpdateAuthorizationState(state)

    fun connectionStateUpdate(state: TdApi.ConnectionState) = TdApi.UpdateConnectionState(state)

    fun formattedText(text: String) = TdApi.FormattedText(text, emptyArray())

    fun error(
        code: Int = 400,
        message: String = "BAD_REQUEST",
    ) = TdApi.Error(code, message)

    /** What TDLib returns for a flood wait: code 429 with the seconds in the message. */
    fun floodWait(seconds: Int) = TdApi.Error(429, "Too Many Requests: retry after $seconds")

    /** A remote file that is not downloaded yet; pass [localPath] and [downloadedSize] for other states. */
    fun file(
        id: Int = 1,
        size: Long = 1_024,
        expectedSize: Long = size,
        localPath: String = "",
        downloadedSize: Long = 0,
        isDownloadingActive: Boolean = false,
        isDownloadingCompleted: Boolean = false,
        canBeDownloaded: Boolean = true,
    ) = TdApi.File(
        id,
        size,
        expectedSize,
        TdApi.LocalFile(
            localPath,
            canBeDownloaded,
            isDownloadingCompleted,
            isDownloadingActive,
            isDownloadingCompleted,
            0,
            downloadedSize,
            downloadedSize,
        ),
        TdApi.RemoteFile("remote-$id", "unique-$id", false, true, size),
    )

    fun minithumbnail(
        width: Int = 40,
        height: Int = 30,
        data: ByteArray = byteArrayOf(1, 2, 3),
    ) = TdApi.Minithumbnail(width, height, data)

    /** A regular user with a test-DC phone number (digits only, as TDLib reports it), no photo and no status. */
    fun user(
        id: Long = 1,
        firstName: String = "Ada",
        lastName: String = "",
        usernames: List<String> = emptyList(),
        phoneNumber: String = "9996621234",
        status: TdApi.UserStatus = TdApi.UserStatusEmpty(),
        profilePhoto: TdApi.ProfilePhoto? = null,
        verificationStatus: TdApi.VerificationStatus? = null,
        isPremium: Boolean = false,
        isContact: Boolean = false,
        isMutualContact: Boolean = false,
        isSupport: Boolean = false,
        haveAccess: Boolean = true,
        type: TdApi.UserType = TdApi.UserTypeRegular(),
    ) = TdApi.User().also {
        it.id = id
        it.firstName = firstName
        it.lastName = lastName
        it.usernames =
            TdApi.Usernames(usernames.toTypedArray(), emptyArray(), usernames.firstOrNull().orEmpty(), emptyArray(), "")
        it.phoneNumber = phoneNumber
        it.status = status
        it.profilePhoto = profilePhoto
        it.verificationStatus = verificationStatus
        it.isPremium = isPremium
        it.isContact = isContact
        it.isMutualContact = isMutualContact
        it.isSupport = isSupport
        it.haveAccess = haveAccess
        it.type = type
        it.languageCode = "en"
    }

    fun profilePhoto(
        smallFileId: Int = 10,
        bigFileId: Int = 11,
        minithumbnail: TdApi.Minithumbnail? = minithumbnail(),
    ) = TdApi.ProfilePhoto(1, file(smallFileId), file(bigFileId), minithumbnail, false, false)

    /** Online until [expires], a Unix time. */
    fun onlineStatus(expires: Int) = TdApi.UserStatusOnline(expires)

    /** Last online at [wasOnline], a Unix time. */
    fun offlineStatus(wasOnline: Int) = TdApi.UserStatusOffline(wasOnline)

    fun updateUser(user: TdApi.User = user()) = TdApi.UpdateUser(user)

    fun updateUserStatus(
        userId: Long,
        status: TdApi.UserStatus,
    ) = TdApi.UpdateUserStatus(userId, status)

    /** The option that tells the logged-in user's id; [userId] null for the empty value of a removed option. */
    fun myIdOption(userId: Long?) =
        TdApi.UpdateOption("my_id", userId?.let { TdApi.OptionValueInteger(it) } ?: TdApi.OptionValueEmpty())

    fun updateFile(file: TdApi.File) = TdApi.UpdateFile(file)
}

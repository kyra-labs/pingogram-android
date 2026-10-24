package dev.kyralabs.pingogram.core.testing

import org.drinkless.tdlib.TdApi

/**
 * TdApi objects for tests of editing the logged-in user's own profile: the user's full info, integer options
 * (`bio_length_max`) and a profile photo's upload. Users and profile photos themselves are in [TdApiFixtures].
 */
object ProfileFixtures {
    /** A user's full info with [bio], and TDLib's "none" for everything else. */
    fun userFullInfo(
        bio: String? = null,
        birthdate: TdApi.Birthdate? = null,
    ) = TdApi.UserFullInfo().also {
        it.bio = bio?.let(TdApiFixtures::formattedText)
        it.birthdate = birthdate
    }

    /** An integer option, e.g. `bio_length_max`; [value] null for the empty value of a removed option. */
    fun integerOption(
        name: String,
        value: Long?,
    ) = TdApi.UpdateOption(name, value?.let { TdApi.OptionValueInteger(it) } ?: TdApi.OptionValueEmpty())

    /**
     * File [id] at [localPath] being uploaded: [uploadedSize] of [size] bytes sent so far, as `updateFile` reports a
     * file sent from this device.
     */
    fun uploadingFile(
        id: Int = 50,
        localPath: String,
        size: Long = 1_000,
        uploadedSize: Long = 0,
        isUploadingCompleted: Boolean = false,
    ) = TdApi.File(
        id,
        size,
        size,
        TdApi.LocalFile(localPath, false, false, false, true, 0, size, size),
        TdApi.RemoteFile("", "", !isUploadingCompleted, isUploadingCompleted, uploadedSize),
    )
}

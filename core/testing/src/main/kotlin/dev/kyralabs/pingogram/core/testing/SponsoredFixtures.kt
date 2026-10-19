package dev.kyralabs.pingogram.core.testing

import org.drinkless.tdlib.TdApi

/**
 * TdApi objects for tests of the sponsored messages of channels and bots (`REQUIREMENTS.md` §2): the messages, their
 * sponsor and the options of a report. Contents and photos are in [TdApiFixtures].
 */
object SponsoredFixtures {
    fun advertisementSponsor(
        url: String = "https://t.me/sponsor",
        photo: TdApi.Photo? = null,
        info: String = "",
    ) = TdApi.AdvertisementSponsor(url, photo, info)

    /** A sponsored message, a text by default; ids of sponsored messages are large. */
    fun sponsoredMessage(
        id: Long = 1L shl 30,
        content: TdApi.MessageContent = TdApiFixtures.messageText("Try it"),
        title: String = "Sponsor",
        buttonText: String = "Open",
        isRecommended: Boolean = false,
        canBeReported: Boolean = true,
        sponsor: TdApi.AdvertisementSponsor = advertisementSponsor(),
        additionalInfo: String = "",
    ) = TdApi.SponsoredMessage(
        id,
        isRecommended,
        canBeReported,
        content,
        sponsor,
        title,
        buttonText,
        0,
        0L,
        additionalInfo,
    )

    fun sponsoredMessages(
        vararg messages: TdApi.SponsoredMessage,
        messagesBetween: Int = 0,
    ) = TdApi.SponsoredMessages(arrayOf(*messages), messagesBetween)

    fun reportOption(
        id: ByteArray,
        text: String,
    ) = TdApi.ReportOption(id, text)
}

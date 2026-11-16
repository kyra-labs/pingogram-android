package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.AccountInfo
import dev.kyralabs.pingogram.core.domain.model.ChatActionBar
import org.drinkless.tdlib.TdApi

/** The domain's action bar; null for none, and for a bar Pingogram does not show (inviting members, newer kinds). */
internal fun TdApi.ChatActionBar.toChatActionBar(): ChatActionBar? =
    when (this) {
        is TdApi.ChatActionBarReportAddBlock -> ChatActionBar.ReportAddBlock(canUnarchive, accountInfo?.toAccountInfo())
        is TdApi.ChatActionBarReportSpam -> ChatActionBar.ReportSpam(canUnarchive)
        is TdApi.ChatActionBarAddContact -> ChatActionBar.AddContact
        is TdApi.ChatActionBarSharePhoneNumber -> ChatActionBar.SharePhoneNumber
        is TdApi.ChatActionBarJoinRequest -> ChatActionBar.JoinRequest(title, isChannel, requestDate.toLong())
        else -> null
    }

private fun TdApi.AccountInfo.toAccountInfo(): AccountInfo =
    AccountInfo(
        registrationMonth = registrationMonth,
        registrationYear = registrationYear,
        phoneCountryCode = phoneNumberCountryCode.orEmpty(),
        lastNameChangeDate = lastNameChangeDate.toLong(),
        lastPhotoChangeDate = lastPhotoChangeDate.toLong(),
    )

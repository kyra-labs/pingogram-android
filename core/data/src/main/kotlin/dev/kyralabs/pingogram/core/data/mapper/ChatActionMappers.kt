package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ChatAction
import org.drinkless.tdlib.TdApi

/** Null for [TdApi.ChatActionCancel], and for an action added by a newer TDLib: neither is shown. */
internal fun TdApi.ChatAction.toChatAction(): ChatAction? =
    when (this) {
        is TdApi.ChatActionTyping -> ChatAction.Typing
        is TdApi.ChatActionRecordingVoiceNote -> ChatAction.RecordingVoiceNote
        is TdApi.ChatActionUploadingVoiceNote -> ChatAction.UploadingVoiceNote(progress)
        is TdApi.ChatActionRecordingVideo -> ChatAction.RecordingVideo
        is TdApi.ChatActionUploadingVideo -> ChatAction.UploadingVideo(progress)
        is TdApi.ChatActionRecordingVideoNote -> ChatAction.RecordingVideoNote
        is TdApi.ChatActionUploadingVideoNote -> ChatAction.UploadingVideoNote(progress)
        is TdApi.ChatActionUploadingPhoto -> ChatAction.UploadingPhoto(progress)
        is TdApi.ChatActionUploadingDocument -> ChatAction.UploadingDocument(progress)
        is TdApi.ChatActionChoosingSticker -> ChatAction.ChoosingSticker
        is TdApi.ChatActionChoosingLocation -> ChatAction.ChoosingLocation
        is TdApi.ChatActionChoosingContact -> ChatAction.ChoosingContact
        is TdApi.ChatActionStartPlayingGame -> ChatAction.PlayingGame
        is TdApi.ChatActionWatchingAnimations -> ChatAction.WatchingAnimations(emoji)
        else -> null
    }

/** TDLib's action for [this], to send (`sendChatAction`); null ends the user's action ([TdApi.ChatActionCancel]). */
internal fun ChatAction?.toTdChatAction(): TdApi.ChatAction =
    when (this) {
        null -> TdApi.ChatActionCancel()
        ChatAction.Typing -> TdApi.ChatActionTyping()
        ChatAction.RecordingVoiceNote -> TdApi.ChatActionRecordingVoiceNote()
        is ChatAction.UploadingVoiceNote -> TdApi.ChatActionUploadingVoiceNote(progress)
        ChatAction.RecordingVideo -> TdApi.ChatActionRecordingVideo()
        is ChatAction.UploadingVideo -> TdApi.ChatActionUploadingVideo(progress)
        ChatAction.RecordingVideoNote -> TdApi.ChatActionRecordingVideoNote()
        is ChatAction.UploadingVideoNote -> TdApi.ChatActionUploadingVideoNote(progress)
        is ChatAction.UploadingPhoto -> TdApi.ChatActionUploadingPhoto(progress)
        is ChatAction.UploadingDocument -> TdApi.ChatActionUploadingDocument(progress)
        ChatAction.ChoosingSticker -> TdApi.ChatActionChoosingSticker()
        ChatAction.ChoosingLocation -> TdApi.ChatActionChoosingLocation()
        ChatAction.ChoosingContact -> TdApi.ChatActionChoosingContact()
        ChatAction.PlayingGame -> TdApi.ChatActionStartPlayingGame()
        is ChatAction.WatchingAnimations -> TdApi.ChatActionWatchingAnimations(emoji)
    }

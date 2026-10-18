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

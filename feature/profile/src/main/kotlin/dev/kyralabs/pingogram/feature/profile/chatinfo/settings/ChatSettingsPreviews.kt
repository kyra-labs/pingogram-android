package dev.kyralabs.pingogram.feature.profile.chatinfo.settings

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.ChatSettingsPreviewData as Data

// The previews of a group's or channel's settings page and its discussion picker, one per state.

@PingogramPreviews
@Composable
private fun ChannelSettingsPreview() = Settings(Data.channel)

@PingogramPreviews
@Composable
private fun SupergroupSettingsPreview() = Settings(Data.supergroup)

@PingogramPreviews
@Composable
private fun BasicGroupSettingsPreview() = Settings(Data.basicGroup)

@PingogramPreviews
@Composable
private fun UpgradeDialogPreview() = Settings(Data.upgradeDialog)

@PingogramPreviews
@Composable
private fun SavingSettingsPreview() = Settings(Data.saving)

@PingogramPreviews
@Composable
private fun SettingsLoadingPreview() = Settings(ChatSettingsUiState.Loading)

@PingogramPreviews
@Composable
private fun SettingsErrorPreview() = Settings(Data.error)

@PingogramPreviews
@Composable
private fun DiscussionPickerPreview() {
    PingogramPreview { DiscussionPickerContent(Data.pickerGroups, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun DiscussionPickerEmptyPreview() {
    PingogramPreview { DiscussionPickerContent(Data.pickerEmpty, onEvent = {}) }
}

@Composable
private fun Settings(state: ChatSettingsUiState) {
    PingogramPreview { ChatSettingsScreen(state, onEvent = {}, onBack = {}) }
}

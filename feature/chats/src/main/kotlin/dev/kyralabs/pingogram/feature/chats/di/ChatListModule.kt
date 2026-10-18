package dev.kyralabs.pingogram.feature.chats.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dev.kyralabs.pingogram.feature.chats.ui.AndroidTimePatterns
import dev.kyralabs.pingogram.feature.chats.ui.TimePatterns

/** What the chat list's view model gets from the platform. */
@Module
@InstallIn(ViewModelComponent::class)
interface ChatListModule {
    @Binds
    fun timePatterns(patterns: AndroidTimePatterns): TimePatterns
}

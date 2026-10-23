package dev.kyralabs.pingogram.core.notifications.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.domain.repository.ChatExportJobs
import dev.kyralabs.pingogram.core.notifications.WorkManagerChatExportJobs

/** Chat exports run as WorkManager work in this module (`docs/adr/0021-chat-export.md`). */
@Module
@InstallIn(SingletonComponent::class)
interface ChatExportJobsModule {
    @Binds
    fun chatExportJobs(jobs: WorkManagerChatExportJobs): ChatExportJobs
}

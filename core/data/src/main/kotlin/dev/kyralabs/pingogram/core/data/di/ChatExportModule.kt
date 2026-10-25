package dev.kyralabs.pingogram.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.data.export.AndroidDocumentTree
import dev.kyralabs.pingogram.core.data.export.AndroidExportFolderRepository
import dev.kyralabs.pingogram.core.data.export.DocumentChatExporter
import dev.kyralabs.pingogram.core.data.export.DocumentTree
import dev.kyralabs.pingogram.core.data.repository.TdlibChatExportRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatExportRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatExporter
import dev.kyralabs.pingogram.core.domain.repository.ExportFolderRepository

/**
 * Chat export (`docs/adr/0021-chat-export.md`): the folders of the Storage Access Framework, which are the device's.
 * TDLib's side and the exporter are the account's ([RepositoryModule]). None keeps state between exports.
 */
@Module
@InstallIn(SingletonComponent::class)
interface ChatExportModule {
    @Binds
    fun exportFolderRepository(repository: AndroidExportFolderRepository): ExportFolderRepository

    @Binds
    fun documentTree(tree: AndroidDocumentTree): DocumentTree
}

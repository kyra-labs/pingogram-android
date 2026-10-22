package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.ui.component.avatarInitials
import dev.kyralabs.pingogram.core.ui.theme.avatarPlaceholderColors
import dev.kyralabs.pingogram.core.ui.theme.standaloneColorScheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The round avatars of notifications and conversation shortcuts: the small profile photo when TDLib has it (one that is
 * missing downloads first, briefly awaited), otherwise the initials on one of the placeholder colors the app's avatars
 * use (`docs/DESIGN_SYSTEM.md` §2.2), from the wallpaper's scheme when dynamic color is on. Bitmaps are drawn on the
 * IO dispatcher, never on the main thread, and kept for the process ([CACHE_SIZE] of them).
 */
@Singleton
internal class AvatarIcons
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val fileRepository: FileRepository,
        private val appearanceRepository: AppearanceRepository,
        @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val cache = LruCache<String, IconCompat>(CACHE_SIZE)

        /** The size Android draws a notification's large icon at, which conversations use for avatars too. */
        private val size by lazy {
            context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
        }

        private val typeface: Typeface by lazy {
            val font = ResourcesCompat.getFont(context, UiR.font.google_sans_flex) ?: Typeface.DEFAULT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(font, INITIALS_WEIGHT, false) else font
        }

        /** The avatar of the user or chat [id] named [name], with [photo] if it has one. */
        suspend fun icon(
            photo: ProfilePhoto?,
            id: Long,
            name: String,
        ): IconCompat {
            val path = photo?.let { downloadedPath(it.small.id) }
            return path?.let { photoIcon(it) } ?: initialsIcon(id, name)
        }

        private suspend fun initialsIcon(
            id: Long,
            name: String,
        ): IconCompat {
            val appearance = appearanceRepository.appearance.first()
            val dynamicColor = appearance.dynamicColor
            val palette = appearance.palette
            val key = "initials:$id:$dynamicColor:$palette:$name"
            return cache.get(key)
                ?: cached(key, withContext(ioDispatcher) { initials(id, name, dynamicColor, palette) })
        }

        /** Where file [fileId] is once downloaded, downloading it if needed; null if it does not arrive in time. */
        private suspend fun downloadedPath(fileId: Int): String? =
            withTimeoutOrNull(DOWNLOAD_WAIT) {
                when (val state = fileRepository.observe(fileId).first()) {
                    is FileState.Downloaded -> {
                        state.path
                    }

                    else -> {
                        fileRepository.download(fileId, DownloadPriority.Visible)
                        fileRepository
                            .observe(fileId)
                            .filterIsInstance<FileState.Downloaded>()
                            .first()
                            .path
                    }
                }
            }

        /** The round photo at [path]; null if it cannot be decoded. */
        private suspend fun photoIcon(path: String): IconCompat? {
            val key = "photo:$path"
            return cache.get(key)
                ?: withContext(ioDispatcher) { BitmapFactory.decodeFile(path)?.let(::circle) }?.let { cached(key, it) }
        }

        private fun cached(
            key: String,
            bitmap: Bitmap,
        ): IconCompat = IconCompat.createWithBitmap(bitmap).also { cache.put(key, it) }

        /** [source] cropped to its centered square and drawn round, [size] wide. */
        private fun circle(source: Bitmap): Bitmap {
            val output = createBitmap(size, size)
            val side = minOf(source.width, source.height).toFloat()
            val scale = size / side
            val matrix =
                Matrix().apply {
                    setTranslate(-(source.width - side) / 2, -(source.height - side) / 2)
                    postScale(scale, scale)
                }
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    shader =
                        BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                            setLocalMatrix(matrix)
                        }
                }
            Canvas(output).drawCircle(size / 2f, size / 2f, size / 2f, paint)
            return output
        }

        /** Up to two initials of [name] on [id]'s placeholder colors, as the app's own placeholder avatars. */
        private fun initials(
            id: Long,
            name: String,
            dynamicColor: Boolean,
            palette: ThemePalette,
        ): Bitmap {
            val colors = standaloneColorScheme(context, dynamicColor, palette).avatarPlaceholderColors(id)
            val output = createBitmap(size, size)
            val canvas = Canvas(output)
            val center = size / 2f
            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.container.toArgb() }
            canvas.drawCircle(center, center, center, background)
            val text =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = colors.content.toArgb()
                    typeface = this@AvatarIcons.typeface
                    textSize = size * INITIALS_SIZE
                    textAlign = Paint.Align.CENTER
                }
            // Centered on the middle of the letters' height, not on the baseline.
            val baseline = center - (text.descent() + text.ascent()) / 2
            canvas.drawText(avatarInitials(name), center, baseline, text)
            return output
        }

        private companion object {
            /** Avatars of about as many chats as TDLib shows notifications for at a time, and their senders. */
            const val CACHE_SIZE = 64

            /** How long a notification waits for a missing photo before it shows the initials instead. */
            val DOWNLOAD_WAIT = 2.seconds

            /** The initials' size against the avatar's, and their weight: Material's label weight. */
            const val INITIALS_SIZE = 0.4f
            const val INITIALS_WEIGHT = 500
        }
    }

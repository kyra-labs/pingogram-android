package dev.kyralabs.pingogram.core.ui.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the wallpapers a conversation draws: a photo, a pattern on the theme's colors and an inverted one, a
 * rotated gradient, a freeform gradient, and the theme-color wallpaper alone. Dark theme dims the photo and the fills.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ChatWallpaperScreenshotTest {
    @Test
    fun wallpapersLight() = capture(ThemeMode.Light)

    @Test
    fun wallpapersDark() = capture(ThemeMode.Dark)

    @Test
    fun wallpapersAmoled() = capture(ThemeMode.Dark, amoled = true)

    @OptIn(ExperimentalCoilApi::class)
    private fun capture(
        themeMode: ThemeMode,
        amoled: Boolean = false,
    ) = captureRoboImage {
        val chat = ChatAppearance(wallpaper = ChatWallpaper.SecondaryToPrimary)
        PingogramTheme(themeMode = themeMode, dynamicColor = false, amoled = amoled, chat = chat) {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides SampleImages,
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Showcase() }
            }
        }
    }
}

@Composable
private fun Showcase() {
    val rows =
        listOf(
            listOf(PHOTO, PATTERN),
            listOf(INVERTED_PATTERN, ROTATED_FILL),
            listOf(FREEFORM_FILL, null),
        )
    Column {
        rows.forEach { row ->
            Row(Modifier.weight(1f)) {
                row.forEach { wallpaper ->
                    ChatWallpaperLayer(
                        wallpaper,
                        darkThemeDimming = DIMMING,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** A sky-like photo for file [PHOTO_FILE], and a grid of dots as the pattern's mask for any other. */
@OptIn(ExperimentalCoilApi::class)
private val SampleImages =
    AsyncImagePreviewHandler { request ->
        if ((request.data as TdlibFileImage).fileId == PHOTO_FILE) photo().asImage() else dots().asImage()
    }

private fun photo() =
    Bitmap.createBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT, Bitmap.Config.ARGB_8888).also {
        val paint = Paint()
        paint.shader = LinearGradient(0f, 0f, 0f, SAMPLE_HEIGHT.toFloat(), SKY, GROUND, Shader.TileMode.CLAMP)
        Canvas(it).drawPaint(paint)
    }

private fun dots() =
    Bitmap.createBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT, Bitmap.Config.ALPHA_8).also {
        val canvas = Canvas(it)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (x in 0 until SAMPLE_WIDTH step DOT_STEP) {
            for (y in 0 until SAMPLE_HEIGHT step DOT_STEP) {
                canvas.drawCircle(
                    x.toFloat(),
                    y.toFloat(),
                    DOT_RADIUS,
                    paint,
                )
            }
        }
    }

private const val PHOTO_FILE = 1
private const val PATTERN_FILE = 2
private const val DIMMING = 40
private const val SAMPLE_WIDTH = 270
private const val SAMPLE_HEIGHT = 600
private const val DOT_STEP = 30
private const val DOT_RADIUS = 6f
private const val SKY = 0xFF6FA8DC.toInt()
private const val GROUND = 0xFFE6B87A.toInt()

private val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(PHOTO_FILE, 0)), isBlurred = false)
private val PATTERN = Wallpaper.Pattern(2, WallpaperImage(FileRef(PATTERN_FILE, 0), isVector = true), 50, false)
private val INVERTED_PATTERN = PATTERN.copy(id = 3, isInverted = true)
private val ROTATED_FILL = Wallpaper.Fill(4, listOf(0x8EC5FC, 0xE0C3FC), rotationDegrees = 45)
private val FREEFORM_FILL = Wallpaper.Fill(5, listOf(0xDBDDBB, 0x6BA587, 0xD5D88D, 0x88B884))

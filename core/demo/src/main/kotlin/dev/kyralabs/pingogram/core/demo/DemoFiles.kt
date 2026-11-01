package dev.kyralabs.pingogram.core.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

/**
 * The demo's files (`docs/adr/0034-demo-mode-for-store-review.md`): pictures and a sound made on the phone the first
 * time a screen asks for them, never stock or Telegram's artwork (CLAUDE.md rule 9). Each has a TDLib file id of the
 * demo's own, which `downloadFile` and `getFile` answer with the path once it is written.
 */
@Suppress("MagicNumber") // The ids are the files' own, as TDLib numbers a client's files.
internal enum class DemoFile(
    val id: Int,
    val fileName: String,
) {
    /** A photo: a gradient of Pingogram's blue. */
    Photo(1, "photo.png"),

    /** The channel's photo: a gradient of warmer colors. */
    ChannelPhoto(2, "colors.png"),

    /** A voice message: three soft tones. */
    Voice(3, "voice.wav"),

    /** A sticker: concentric rings, a "ping". */
    Sticker(4, "sticker.png"),
    ;

    companion object {
        fun of(id: Int): DemoFile? = entries.firstOrNull { it.id == id }
    }
}

/** Writes a [DemoFile] into a directory. Blocking: on the IO dispatcher. */
internal fun interface DemoFileWriter {
    fun write(
        file: DemoFile,
        target: File,
    )
}

/** Draws the pictures with Android's graphics and writes the sound as PCM WAV, which the players read. */
internal object AndroidDemoFileWriter : DemoFileWriter {
    override fun write(
        file: DemoFile,
        target: File,
    ) {
        target.parentFile?.mkdirs()
        when (file) {
            DemoFile.Photo -> gradient(target, BRAND_BLUE, BRAND_TEAL)
            DemoFile.ChannelPhoto -> gradient(target, WARM_ORANGE, WARM_PINK)
            DemoFile.Voice -> writeWav(target, tones())
            DemoFile.Sticker -> rings(target)
        }
    }

    private fun gradient(
        target: File,
        start: Int,
        end: Int,
    ) {
        val bitmap = createBitmap(PHOTO_WIDTH, PHOTO_HEIGHT)
        val paint =
            Paint().apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        PHOTO_WIDTH.toFloat(),
                        PHOTO_HEIGHT.toFloat(),
                        start,
                        end,
                        Shader.TileMode.CLAMP,
                    )
            }
        Canvas(bitmap).drawRect(0f, 0f, PHOTO_WIDTH.toFloat(), PHOTO_HEIGHT.toFloat(), paint)
        save(bitmap, target)
    }

    private fun rings(target: File) {
        val bitmap = createBitmap(STICKER_SIZE, STICKER_SIZE)
        val canvas = Canvas(bitmap)
        val center = STICKER_SIZE / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        RING_COLORS.forEachIndexed { index, color ->
            paint.color = color
            canvas.drawCircle(center, center, center * (RING_COLORS.size - index) / RING_COLORS.size, paint)
        }
        save(bitmap, target)
    }

    private fun save(
        bitmap: Bitmap,
        target: File,
    ) {
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        bitmap.recycle()
    }
}

/** Three short tones a third apart, faded in and out, as 16-bit mono samples. */
internal fun tones(): ShortArray {
    val toneSamples = SAMPLE_RATE * TONE_MILLIS / MILLIS
    val samples = ShortArray(toneSamples * TONES.size)
    TONES.forEachIndexed { tone, frequency ->
        for (i in 0 until toneSamples) {
            val fade = minOf(1.0, i / FADE_SAMPLES, (toneSamples - i) / FADE_SAMPLES)
            val value = sin(2 * PI * frequency * i / SAMPLE_RATE) * fade * AMPLITUDE
            samples[tone * toneSamples + i] = value.toInt().toShort()
        }
    }
    return samples
}

/** [samples] as a RIFF WAV file of 16-bit mono PCM at [SAMPLE_RATE]. */
internal fun writeWav(
    target: File,
    samples: ShortArray,
) {
    target.parentFile?.mkdirs()
    val dataSize = samples.size * 2
    RandomAccessFile(target, "rw").use { out ->
        out.setLength(0)
        out.writeBytes("RIFF")
        out.writeIntLe(WAV_HEADER_SIZE - RIFF_HEADER_SIZE + dataSize)
        out.writeBytes("WAVEfmt ")
        out.writeIntLe(FMT_CHUNK_SIZE)
        out.writeShortLe(PCM)
        out.writeShortLe(1)
        out.writeIntLe(SAMPLE_RATE)
        out.writeIntLe(SAMPLE_RATE * 2)
        out.writeShortLe(2)
        out.writeShortLe(BITS_PER_SAMPLE)
        out.writeBytes("data")
        out.writeIntLe(dataSize)
        val bytes = ByteArray(dataSize)
        samples.forEachIndexed { i, sample ->
            bytes[2 * i] = sample.toInt().toByte()
            bytes[2 * i + 1] = (sample.toInt() shr BYTE_BITS).toByte()
        }
        out.write(bytes)
    }
}

private fun RandomAccessFile.writeIntLe(value: Int) {
    for (shift in 0 until Int.SIZE_BITS step BYTE_BITS) write(value shr shift)
}

private fun RandomAccessFile.writeShortLe(value: Int) {
    write(value)
    write(value shr BYTE_BITS)
}

/** The voice message's length in seconds, which its bubble shows. */
internal const val VOICE_SECONDS = 3

private const val PHOTO_WIDTH = 1280
private const val PHOTO_HEIGHT = 960
private const val STICKER_SIZE = 512
private const val PNG_QUALITY = 100

// The brand's seed (docs/DESIGN_SYSTEM.md §2.1) and colors near it; artwork, not UI colors.
private const val BRAND_BLUE = 0xFF2156C4.toInt()
private const val BRAND_TEAL = 0xFF00897B.toInt()
private const val WARM_ORANGE = 0xFFFF9800.toInt()
private const val WARM_PINK = 0xFFE91E63.toInt()
private val RING_COLORS = listOf(0xFFD9E2FF.toInt(), 0xFF7C9CF0.toInt(), BRAND_BLUE, 0xFF0B2E7A.toInt())

private const val SAMPLE_RATE = 16_000
private const val MILLIS = 1_000
private const val TONE_MILLIS = VOICE_SECONDS * MILLIS / 3
private val TONES = listOf(440.0, 554.37, 659.25)
private const val FADE_SAMPLES = 800.0
private const val AMPLITUDE = 8_000.0
private const val WAV_HEADER_SIZE = 44
private const val RIFF_HEADER_SIZE = 8
private const val FMT_CHUNK_SIZE = 16
private const val PCM = 1
private const val BITS_PER_SAMPLE = 16
private const val BYTE_BITS = 8

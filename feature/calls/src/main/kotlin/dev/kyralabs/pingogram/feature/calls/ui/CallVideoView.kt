package dev.kyralabs.pingogram.feature.calls.ui

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import dev.kyralabs.pingogram.feature.calls.engine.VideoFrameSink
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A call's video (`docs/adr/0019-video-and-group-calls-scope.md`): WebRTC's [EglRenderer], which the engine's AAR
 * bundles, drawing on its own thread into a [TextureView], which Compose clips like any view (a `SurfaceView` would
 * keep square corners). It fills its bounds, cropping the frames to them, [mirror]ed for the front camera's self-view.
 *
 * [onSink] gets the renderer as the [VideoFrameSink] the engine hands frames to while the screen is started, and null
 * when it stops or the view leaves.
 */
@Composable
internal fun CallVideoView(
    name: String,
    mirror: Boolean,
    onSink: (VideoFrameSink?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderer = remember(name) { TextureRenderer(name) }
    DisposableEffect(renderer) { onDispose(renderer::release) }
    // Only while the screen is started: a call screen in the background (picture-in-picture aside) draws nothing, and
    // without a remote sink the engine does not even receive the other side's video.
    LifecycleStartEffect(renderer) {
        onSink(renderer)
        onStopOrDispose { onSink(null) }
    }
    AndroidView(
        factory = { context -> TextureView(context).apply { surfaceTextureListener = renderer } },
        update = { renderer.setMirror(mirror) },
        modifier = modifier,
    )
}

/** One EGL context for every renderer of the process, made with the first. */
private object CallEgl {
    val context: EglBase.Context by lazy { EglBase.create().eglBaseContext }
}

/**
 * Takes the engine's packed I420 frames on its threads and hands them to [EglRenderer]: each is copied into a direct
 * buffer from a small pool (a buffer returns once the renderer drew or dropped its frame), as WebRTC's frames hold
 * their planes in direct buffers.
 */
private class TextureRenderer(
    name: String,
) : VideoFrameSink,
    TextureView.SurfaceTextureListener {
    private val renderer = EglRenderer(name).apply { init(CallEgl.context, EglBase.CONFIG_PLAIN, GlRectDrawer()) }
    private val pool = ConcurrentLinkedQueue<ByteBuffer>()

    override fun onFrame(
        i420: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
    ) {
        val chromaWidth = (width + 1) / 2
        val lumaSize = width * height
        val chromaSize = chromaWidth * ((height + 1) / 2)
        if (i420.size < lumaSize + chromaSize * 2) return
        val buffer = obtain(i420.size)
        buffer.put(i420).rewind()
        val planes =
            JavaI420Buffer.wrap(
                width,
                height,
                buffer.plane(0, lumaSize),
                width,
                buffer.plane(lumaSize, chromaSize),
                chromaWidth,
                buffer.plane(lumaSize + chromaSize, chromaSize),
                chromaWidth,
            ) { pool.offer(buffer) }
        val frame = VideoFrame(planes, rotationDegrees, System.nanoTime())
        renderer.onFrame(frame)
        frame.release()
    }

    /** A pooled buffer of [size] bytes, cleared; buffers of another size (the video's size changed) are dropped. */
    private fun obtain(size: Int): ByteBuffer {
        while (true) {
            val pooled = pool.poll() ?: return ByteBuffer.allocateDirect(size)
            if (pooled.capacity() == size) return pooled.apply { clear() }
        }
    }

    private fun ByteBuffer.plane(
        offset: Int,
        size: Int,
    ): ByteBuffer =
        duplicate()
            .apply {
                position(offset)
                limit(offset + size)
            }.slice()

    fun setMirror(mirror: Boolean) = renderer.setMirror(mirror)

    fun release() = renderer.release()

    override fun onSurfaceTextureAvailable(
        surface: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        renderer.createEglSurface(surface)
        onSurfaceTextureSizeChanged(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(
        surface: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        // Crops the frames to the view's shape: the video fills it.
        if (width > 0 && height > 0) renderer.setLayoutAspectRatio(width.toFloat() / height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        // The renderer must be done with the surface before the view releases it: a frame at most, on its thread.
        val released = CountDownLatch(1)
        renderer.releaseEglSurface(released::countDown)
        released.await(RELEASE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
}

/** How long the main thread waits for the renderer to let go of a destroyed surface. */
private const val RELEASE_TIMEOUT_MS = 500L

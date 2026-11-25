package dev.kyralabs.pingogram.feature.calls.ui

import android.graphics.SurfaceTexture
import android.view.TextureView
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * A call video's renderer as [RendererSetup] drives it: WebRTC's `EglRenderer` in the app ([CallVideoView]), whose
 * steps each wait for its own thread.
 */
internal interface SurfaceRenderer {
    /** Sets it up: its thread and EGL context, the process's shared context made the first time. */
    fun setUp()

    /** Draws into [surface] from now on. */
    fun attach(surface: SurfaceTexture)

    /** Stops drawing into its surface, then runs [done] on its thread. */
    fun detach(done: Runnable)

    fun setAspectRatio(ratio: Float)

    fun setMirror(mirror: Boolean)

    /** Takes it down: its thread and EGL context. */
    fun release()
}

/**
 * Gives [renderer] its [TextureView]'s surface and takes it back, and sets it up and takes it down, all on [setup], in
 * the order asked: none of it waits on the main thread, where a call's screen composes. A destroyed surface is
 * released there once the renderer let go of it, rather than the main thread waiting for that (the view leaves its
 * release to us).
 */
internal class RendererSetup(
    private val renderer: SurfaceRenderer,
    private val setup: Executor = CallVideoSetup,
) : TextureView.SurfaceTextureListener {
    init {
        setup.execute(renderer::setUp)
    }

    fun setMirror(mirror: Boolean) = setup.execute { renderer.setMirror(mirror) }

    fun release() = setup.execute(renderer::release)

    override fun onSurfaceTextureAvailable(
        surface: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        setup.execute { renderer.attach(surface) }
        onSurfaceTextureSizeChanged(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(
        surface: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        // Crops the frames to the view's shape: the video fills it.
        if (width > 0 && height > 0) setup.execute { renderer.setAspectRatio(width.toFloat() / height) }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        setup.execute { renderer.detach(surface::release) }
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
}

/**
 * The thread every call video renderer is set up and taken down on, one step at a time, in the order asked; idle
 * between them.
 */
internal object CallVideoSetup : Executor by Executors.newSingleThreadExecutor({ runnable ->
    Thread(runnable, "CallVideoSetup").apply { isDaemon = true }
})

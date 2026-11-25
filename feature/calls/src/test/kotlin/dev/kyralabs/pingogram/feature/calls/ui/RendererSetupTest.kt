package dev.kyralabs.pingogram.feature.calls.ui

import android.graphics.SurfaceTexture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [RendererSetup]: the main thread, where the call's screen composes, only queues the renderer's steps for the setup
 * thread, in the order asked; none of them runs on it. A destroyed surface is released once the renderer let go of
 * it, so the view leaves its release to us.
 */
@RunWith(RobolectricTestRunner::class)
class RendererSetupTest {
    /** What was given to the setup thread, in order; run only when the test says. */
    private val queued = ArrayDeque<Runnable>()
    private val renderer = RecordingRenderer()

    private fun runQueued() {
        while (queued.isNotEmpty()) queued.removeFirst().run()
    }

    @Test
    fun `made on the main thread, it sets the renderer up only on the setup thread`() {
        RendererSetup(renderer, queued::add)

        assertEquals(emptyList<String>(), renderer.steps)
        runQueued()
        assertEquals(listOf("set up"), renderer.steps)
    }

    @Test
    fun `a surface that comes and goes, then the end, run on the setup thread in that order`() {
        val setup = RendererSetup(renderer, queued::add)
        val surface = SurfaceTexture(0)

        setup.onSurfaceTextureAvailable(surface, WIDTH, HEIGHT)
        setup.setMirror(true)
        val viewReleases = setup.onSurfaceTextureDestroyed(surface)
        setup.release()
        assertEquals(emptyList<String>(), renderer.steps)
        runQueued()

        assertEquals(
            listOf("set up", "attach", "aspect ${WIDTH.toFloat() / HEIGHT}", "mirror true", "detach", "release"),
            renderer.steps,
        )
        assertFalse("the view would release the surface while the renderer may still draw on it", viewReleases)
        assertTrue("the surface is released once the renderer let go of it", renderer.detached)
    }

    @Test
    fun `a surface with no size yet gives the renderer no shape`() {
        val setup = RendererSetup(renderer, queued::add)

        setup.onSurfaceTextureAvailable(SurfaceTexture(0), 0, 0)
        runQueued()

        assertEquals(listOf("set up", "attach"), renderer.steps)
    }

    private class RecordingRenderer : SurfaceRenderer {
        val steps = mutableListOf<String>()
        var detached = false

        override fun setUp() {
            steps += "set up"
        }

        override fun attach(surface: SurfaceTexture) {
            steps += "attach"
        }

        override fun detach(done: Runnable) {
            steps += "detach"
            done.run()
            detached = true
        }

        override fun setAspectRatio(ratio: Float) {
            steps += "aspect $ratio"
        }

        override fun setMirror(mirror: Boolean) {
            steps += "mirror $mirror"
        }

        override fun release() {
            steps += "release"
        }
    }

    private companion object {
        const val WIDTH = 720
        const val HEIGHT = 1280
    }
}

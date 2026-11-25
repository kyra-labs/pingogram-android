package dev.kyralabs.pingogram.feature.chat.ui.videonote

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display

/**
 * The default display's rotation, followed: [follow] tells it now and each time the display changes, until closed. A
 * camera's use cases take the rotation only when they are built, and the activity is not created again when the
 * screen rotates (its `configChanges`), so a recording follows the display itself, as CameraX advises for that case.
 */
internal class DisplayRotation(
    context: Context,
) {
    private val displays = context.getSystemService(DisplayManager::class.java)

    /** Calls [onRotation] with the display's rotation (a `Surface.ROTATION_*`) now and as it changes. */
    fun follow(onRotation: (Int) -> Unit): AutoCloseable {
        val display = displays.getDisplay(Display.DEFAULT_DISPLAY)
        onRotation(display.rotation)
        val listener =
            object : DisplayManager.DisplayListener {
                override fun onDisplayChanged(displayId: Int) {
                    if (displayId == Display.DEFAULT_DISPLAY) onRotation(display.rotation)
                }

                override fun onDisplayAdded(displayId: Int) = Unit

                override fun onDisplayRemoved(displayId: Int) = Unit
            }
        displays.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        return AutoCloseable { displays.unregisterDisplayListener(listener) }
    }
}

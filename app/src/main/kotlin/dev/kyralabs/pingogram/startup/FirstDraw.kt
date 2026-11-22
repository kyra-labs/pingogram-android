package dev.kyralabs.pingogram.startup

import android.view.View
import android.view.ViewTreeObserver

/** Calls [action] once, on the main thread, after this view's window drew its first frame. */
internal fun View.doOnFirstDraw(action: () -> Unit) {
    viewTreeObserver.addOnDrawListener(
        object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                // A draw listener cannot be removed while the window draws.
                post {
                    viewTreeObserver.removeOnDrawListener(this)
                    action()
                }
            }
        },
    )
}

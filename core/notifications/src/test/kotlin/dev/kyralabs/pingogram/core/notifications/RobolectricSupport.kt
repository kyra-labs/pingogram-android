package dev.kyralabs.pingogram.core.notifications

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import org.robolectric.Shadows.shadowOf

/** Gives the test application a launcher activity, which the intents to open a chat start. */
internal fun Application.registerLauncherActivity(): ComponentName {
    val component = ComponentName(this, "$packageName.Launcher")
    val packageManager = shadowOf(packageManager)
    packageManager.addActivityIfNotPresent(component)
    packageManager.addIntentFilterForActivity(
        component,
        IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
    )
    return component
}

package dev.kyralabs.pingogram.widget

import android.os.Build
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import dev.kyralabs.pingogram.R

/**
 * A part inside a widget (a button, the reply field) on [color], its corners the launcher's inner radius (board 33:
 * inner corners are the outer ones less the padding): Android's `system_app_widget_inner_radius` from Android 12,
 * which follows the launcher's own corners; before, the drawable's 20 dp, the 28 dp corner of most launchers then less
 * the widget's 8 dp padding, as those launchers ignore a view's corner radius.
 */
internal fun GlanceModifier.innerBackground(color: ColorProvider): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(color).cornerRadius(android.R.dimen.system_app_widget_inner_radius)
    } else {
        background(ImageProvider(R.drawable.widget_button), colorFilter = ColorFilter.tint(color))
    }

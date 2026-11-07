package dev.kyralabs.pingogram.core.ui.component

import android.app.UiModeManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.experimentalTypography
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The experimental look's shared parts (`docs/DESIGN_SYSTEM.md` §13): avatars with the vivid fills and the shapes of
 * each kind of chat (a person, a group, a channel, Saved Messages, a forum, selected), the wide and condensed display
 * styles and the tabs' header actions, in light, dark, AMOLED black, high contrast, right to left and at the largest
 * font scale, and with the Coral theme. The standard look's goldens ([AvatarScreenshotTest]) are unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ExperimentalPartsScreenshotTest {
    @Test
    fun light() = capture(ThemeMode.Light)

    @Test
    fun dark() = capture(ThemeMode.Dark)

    @Test
    fun amoled() = capture(ThemeMode.Dark, amoled = true)

    @Test
    fun highContrast() {
        val uiModeManager = RuntimeEnvironment.getApplication().getSystemService(UiModeManager::class.java)
        shadowOf(uiModeManager).setContrast(1f)
        capture(ThemeMode.Light)
    }

    @Test
    fun rtl() = capture(ThemeMode.Light, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(fontScale = 2f)
    fun fontScale200() = capture(ThemeMode.Light)

    @Test
    fun coralLight() = capture(ThemeMode.Light, palette = ThemePalette.Coral)

    @Test
    fun coralDark() = capture(ThemeMode.Dark, palette = ThemePalette.Coral)

    private fun capture(
        themeMode: ThemeMode,
        amoled: Boolean = false,
        palette: ThemePalette = ThemePalette.Pingogram,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = captureRoboImage {
        // Inspection mode, as in previews: Coil shows the showcase's preview photo instead of loading from TDLib.
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalExperimentalUi provides true,
            LocalLayoutDirection provides layoutDirection,
        ) {
            PingogramTheme(themeMode = themeMode, dynamicColor = false, palette = palette, amoled = amoled) {
                Surface(color = MaterialTheme.colorScheme.background) { ExperimentalParts() }
            }
        }
    }
}

@Composable
private fun ExperimentalParts() {
    val spacing = MaterialTheme.spacing
    val type = MaterialTheme.experimentalTypography
    Column {
        Column(
            Modifier.padding(horizontal = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Text("Chats", Modifier.weight(1f), style = type.displaySmallWide)
                HeaderAction(R.drawable.ic_search)
                HeaderAction(R.drawable.ic_edit)
            }
            Text("Conversation title", style = type.titleMediumWide)
            Text("Ada Lovelace", style = type.headlineSmallWide)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.large)) {
                Text("A", style = type.headlineLargeCondensed, color = MaterialTheme.colorScheme.primary)
                Text("999+", style = type.displaySmallCondensed)
            }
            ChatKinds()
        }
        AvatarShowcase()
    }
}

/** A person, a group, a channel, Saved Messages and a forum, then the same selected. */
@Composable
private fun ChatKinds() {
    val size = MaterialTheme.sizes.avatarMedium
    val kinds =
        listOf(
            AvatarShape.Circle,
            AvatarShape.Group,
            AvatarShape.Channel,
            AvatarShape.SavedMessages,
            AvatarShape.RoundedSquare,
        )
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        listOf(false, true).forEach { selected ->
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                kinds.forEachIndexed { index, shape ->
                    Avatar(
                        photo = null,
                        name = "Book club",
                        id = index.toLong(),
                        size = size,
                        shape = shape,
                        selected = selected,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(icon: Int) {
    Box(tabHeaderActionModifier()) {
        FilledTonalIconButton(
            onClick = {},
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.matchParentSize(),
        ) {
            Icon(painterResource(icon), contentDescription = null)
        }
    }
}

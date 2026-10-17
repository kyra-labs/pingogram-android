package dev.kyralabs.pingogram.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.navigation
import dev.kyralabs.pingogram.core.navigation.MainGraph
import dev.kyralabs.pingogram.feature.chats.navigation.chatsScreen
import dev.kyralabs.pingogram.feature.contacts.navigation.contactsScreen
import dev.kyralabs.pingogram.feature.settings.navigation.settingsScreen

/**
 * The app's single `NavHost`. Today it holds only [MainGraph]; the auth graph (`docs/ROADMAP.md` M1) goes
 * next to it, and the start destination will then follow the TDLib authorization state.
 *
 * Destinations switch with Material's fade-through (`docs/DESIGN_SYSTEM.md` §5), built from the theme's
 * expressive motion scheme. Destinations pushed on top of a tab later bring their own shared-axis
 * transitions.
 */
@Composable
internal fun PingogramNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    NavHost(
        navController = navController,
        startDestination = MainGraph,
        modifier = modifier,
        enterTransition = { fadeThroughEnter(motionScheme) },
        exitTransition = { fadeThroughExit(motionScheme) },
    ) {
        mainGraph()
    }
}

private fun NavGraphBuilder.mainGraph() {
    navigation<MainGraph>(startDestination = TopLevelDestination.Start.route) {
        chatsScreen()
        contactsScreen()
        settingsScreen()
    }
}

// Fade-through: the outgoing screen fades out quickly while the incoming one fades in and grows from 92%.
// Springs cannot be delayed, so the faster exit spring stands in for the spec's sequential timing.
private fun fadeThroughEnter(motionScheme: MotionScheme): EnterTransition =
    fadeIn(motionScheme.defaultEffectsSpec()) +
        scaleIn(motionScheme.defaultSpatialSpec(), initialScale = FADE_THROUGH_INITIAL_SCALE)

private fun fadeThroughExit(motionScheme: MotionScheme): ExitTransition = fadeOut(motionScheme.fastEffectsSpec())

private const val FADE_THROUGH_INITIAL_SCALE = 0.92f

package dev.kyralabs.pingogram.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.MutableThreePaneScaffoldState
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.min

/**
 * The Chats tab's pane navigator: Material's list-detail navigation (the history of destinations, back, predictive
 * back), except that the extra pane takes a partition only while the history holds it. Material's navigator gives
 * every partition the directive has to a pane, so on an extra-large window, with three, the extra pane would show
 * empty beside every conversation; here the list and the conversation share the window until a profile or an info
 * page opens beside them (`docs/DESIGN_SYSTEM.md` §7). Each step of the history is laid out with its own partitions,
 * so back from the extra pane, predictive back included, animates to the two panes again.
 *
 * Content keys are strings ([ChatsPaneContent.key]), which the history is saved with ([rememberChatsPaneNavigator]).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Stable
internal class ChatsPaneNavigator(
    initialHistory: List<ThreePaneScaffoldDestinationItem<String>>,
    initialDirective: PaneScaffoldDirective,
) : ThreePaneScaffoldNavigator<String> {
    private val history =
        mutableStateListOf<ThreePaneScaffoldDestinationItem<String>>().apply {
            addAll(
                initialHistory,
            )
        }

    override var scaffoldDirective by mutableStateOf(initialDirective)

    override var isDestinationHistoryAware by mutableStateOf(true)

    override val currentDestination: ThreePaneScaffoldDestinationItem<String>?
        get() = history.lastOrNull()

    override val scaffoldValue by derivedStateOf { valueAt(history.lastIndex) }

    private val state = MutableThreePaneScaffoldState(scaffoldValue)

    override val scaffoldState: ThreePaneScaffoldState get() = state

    override fun peekPreviousScaffoldValue(backNavigationBehavior: BackNavigationBehavior): ThreePaneScaffoldValue {
        val index = previousIndex(backNavigationBehavior)
        return if (index < 0) scaffoldValue else valueAt(index)
    }

    override suspend fun navigateTo(
        pane: ThreePaneScaffoldRole,
        contentKey: String?,
    ) {
        history.add(ThreePaneScaffoldDestinationItem(pane, contentKey))
        animateToCurrentValue()
    }

    override fun canNavigateBack(backNavigationBehavior: BackNavigationBehavior): Boolean =
        previousIndex(backNavigationBehavior) >= 0

    override suspend fun navigateBack(backNavigationBehavior: BackNavigationBehavior): Boolean {
        val index = previousIndex(backNavigationBehavior)
        if (index < 0) {
            // As Material's navigator: nothing to go back to clears the history.
            history.clear()
            animateToCurrentValue()
            return false
        }
        while (history.size > index + 1) history.removeAt(history.lastIndex)
        animateToCurrentValue()
        return true
    }

    override suspend fun seekBack(
        backNavigationBehavior: BackNavigationBehavior,
        fraction: Float,
    ) {
        if (fraction == 0f) {
            animateToCurrentValue()
        } else {
            state.seekTo(fraction, peekPreviousScaffoldValue(backNavigationBehavior), isPredictiveBackInProgress = true)
        }
    }

    private suspend fun animateToCurrentValue() {
        state.animateTo(scaffoldValue)
    }

    /** The index of the destination [behavior] goes back to; -1 for none. */
    private fun previousIndex(behavior: BackNavigationBehavior): Int {
        if (history.size <= 1) return -1
        val last = history.lastIndex
        val current = history[last]
        return when (behavior) {
            BackNavigationBehavior.PopLatest -> {
                last - 1
            }

            BackNavigationBehavior.PopUntilScaffoldValueChange -> {
                (last - 1 downTo 0).firstOrNull { valueAt(it) != scaffoldValue } ?: -1
            }

            BackNavigationBehavior.PopUntilCurrentDestinationChange -> {
                (last - 1 downTo 0).firstOrNull { history[it].pane != current.pane } ?: -1
            }

            BackNavigationBehavior.PopUntilContentChange -> {
                (last - 1 downTo 0).firstOrNull {
                    history[it].contentKey != current.contentKey || valueAt(it) != scaffoldValue
                } ?: -1
            }

            else -> {
                last - 1
            }
        }
    }

    /** The panes of the history up to [index]: the extra pane gets a partition only once the history holds it. */
    private fun valueAt(index: Int): ThreePaneScaffoldValue {
        val destinations = if (index < 0) emptyList() else history.subList(0, index + 1)
        val directive = scaffoldDirective
        val withExtra = destinations.any { it.pane == ListDetailPaneScaffoldRole.Extra }
        return calculateThreePaneScaffoldValue(
            maxHorizontalPartitions =
                if (withExtra) directive.maxHorizontalPartitions else min(directive.maxHorizontalPartitions, 2),
            adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
            destinationHistory = destinations,
            maxVerticalPartitions = directive.maxVerticalPartitions,
        )
    }

    companion object {
        /** Saves the history as the panes' names and their content keys. */
        fun saver(directive: PaneScaffoldDirective): Saver<ChatsPaneNavigator, *> =
            listSaver(
                save = { navigator -> navigator.history.flatMap { listOf(it.pane.name, it.contentKey) } },
                restore = { saved ->
                    val history =
                        saved.chunked(2).map { (pane, key) ->
                            ThreePaneScaffoldDestinationItem(
                                ThreePaneScaffoldRole.valueOf(pane as String),
                                key as String?,
                            )
                        }
                    ChatsPaneNavigator(history, directive)
                },
            )
    }
}

/** A [ChatsPaneNavigator] following [directive], starting at the list, saved with the composition. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun rememberChatsPaneNavigator(directive: PaneScaffoldDirective): ChatsPaneNavigator =
    rememberSaveable(saver = ChatsPaneNavigator.saver(directive)) {
        ChatsPaneNavigator(listOf(ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List)), directive)
    }.apply { scaffoldDirective = directive }

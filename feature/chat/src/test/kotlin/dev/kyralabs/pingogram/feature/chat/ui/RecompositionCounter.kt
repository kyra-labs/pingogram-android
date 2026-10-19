package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.setObserver
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Counts recompositions in a Compose test (`docs/PERFORMANCE.md` §4, §9), through the Compose runtime's composition
 * observer: every restartable composable (a function or a composable lambda) that composes again after it first
 * composed. Composables composing for the first time, e.g. rows a lazy list brings into view, are not recompositions.
 * Neither are the roots of a lazy list's items, which the list runs again when the range of items near its viewport
 * moves: their content skips unless its arguments changed, and is counted then.
 *
 * [start] it once the content is set, so it finds the content's recomposer; it then sees every composition of that
 * recomposer, subcompositions (lazy items, a `Scaffold`'s slots) included.
 *
 * Copied from `feature:chats`' (`docs/PERFORMANCE.md` §9), as features do not depend on each other, test sources
 * included, with one difference: a call that skips (its parent composed again, its arguments did not change) enters
 * its restart group too, and is not counted here, so a composable whose parent composes again counts only if it
 * composes again itself.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
class RecompositionCounter :
    CompositionRegistrationObserver,
    CompositionObserver {
    private val seen: MutableSet<RecomposeScope> = Collections.newSetFromMap(IdentityHashMap())

    /** The scopes entered again and not exited yet, with their composable: counted on exit unless they skipped. */
    private val entered = IdentityHashMap<RecomposeScope, String>()
    private val handles = mutableListOf<CompositionObserverHandle>()
    private val recompositions = mutableListOf<String>()

    /**
     * The composables that recomposed since the last [reset], once per recomposition: the function that started the
     * restart group (`dev.kyralabs.pingogram.feature.chat.ui.MessageBubbleKt.MessageBubbleRow`), or for a composable
     * lambda the class of the lambda where the runtime's internals show it (a `Scaffold`'s content as a lambda class of
     * the file that wrote it).
     */
    val recomposed: List<String> get() = recompositions.toList()

    /** [recomposed], only Pingogram's own composables. */
    val recomposedOwn: List<String> get() = recompositions.filter { it.startsWith(OWN_PACKAGE) }

    /**
     * [recomposedOwn] without composable lambdas: Pingogram's composable functions. A lambda may run again for its
     * caller's reasons, e.g. a `Scaffold`'s content, which Material's `Scaffold` composes again whenever it measures it
     * anew; the functions it calls skip unless their arguments changed, and are counted then.
     */
    val recomposedOwnFunctions: List<String> get() = recomposedOwn.filterNot { LAMBDA_CLASS in it }

    fun start() {
        Recomposer.runningRecomposers.value.forEach { recomposer -> recomposer.observe(this)?.let(handles::add) }
    }

    fun reset() = recompositions.clear()

    override fun onCompositionRegistered(composition: ObservableComposition) {
        handles += composition.setObserver(this)
    }

    override fun onCompositionUnregistered(composition: ObservableComposition) = Unit

    override fun onBeginComposition(composition: ObservableComposition) = Unit

    override fun onScopeEnter(scope: RecomposeScope) {
        if (seen.add(scope)) return
        restartGroupOwner(scope)?.let { entered[scope] = it }
    }

    override fun onReadInScope(
        scope: RecomposeScope,
        value: Any,
    ) = Unit

    override fun onScopeExit(scope: RecomposeScope) {
        val owner = entered.remove(scope) ?: return
        if (!scope.skipped()) recompositions += owner
    }

    override fun onEndComposition(composition: ObservableComposition) = Unit

    override fun onScopeInvalidated(
        scope: RecomposeScope,
        value: Any?,
    ) = Unit

    override fun onScopeDisposed(scope: RecomposeScope) {
        seen.remove(scope)
    }

    /**
     * The function whose restart group [scope] is, from the stack: the caller of the composer's `startRestartGroup`;
     * for a composable lambda the lambda's class, if found. Null for the root scope of a composition, which the
     * composer enters without one.
     */
    private fun restartGroupOwner(scope: RecomposeScope): String? {
        val stack = Throwable().stackTrace
        val start = stack.indexOfFirst { it.methodName == START_RESTART_GROUP }
        val owner = stack.getOrNull(start + 1)?.takeIf { start >= 0 } ?: return null
        val name = "${owner.className}.${owner.methodName}"
        return if (owner.className == COMPOSABLE_LAMBDA) lambdaClass(scope) ?: name else name
    }

    /** The class of the lambda that a composable lambda's [scope] runs; diagnostics only, so null on any failure. */
    private fun lambdaClass(scope: RecomposeScope): String? =
        runCatching {
            // The scope's restart block refers to its ComposableLambdaImpl (a captured value, or a function
            // reference's receiver), whose _block is the lambda as written.
            val block = scope.field(scope.javaClass, "block") ?: return null
            val lambda =
                generateSequence<Class<*>>(block.javaClass) { it.superclass }
                    .flatMap { it.declaredFields.asSequence() }
                    .map { it.apply { isAccessible = true }.get(block) }
                    .firstOrNull { it?.javaClass?.name == COMPOSABLE_LAMBDA } ?: return null
            lambda.field(lambda.javaClass, "_block")?.javaClass?.name
        }.getOrNull()

    /**
     * Whether the composer skipped [scope]'s body this time, from the runtime's internal flag; false when it cannot
     * tell, so the call counts.
     */
    private fun RecomposeScope.skipped(): Boolean =
        runCatching { javaClass.getMethod(SKIPPED_GETTER).invoke(this) as Boolean }.getOrDefault(false)

    private fun Any.field(
        type: Class<*>,
        name: String,
    ): Any? = type.getDeclaredField(name).apply { isAccessible = true }.get(this)

    private companion object {
        const val START_RESTART_GROUP = "startRestartGroup"
        const val COMPOSABLE_LAMBDA = "androidx.compose.runtime.internal.ComposableLambdaImpl"
        const val OWN_PACKAGE = "dev.kyralabs.pingogram."

        /** `RecomposeScopeImpl.skipped`, internal to the Compose runtime. */
        const val SKIPPED_GETTER = "getSkipped\$runtime"

        /** In the name of the class the JVM generates for a lambda. */
        const val LAMBDA_CLASS = "\$\$Lambda"
    }
}

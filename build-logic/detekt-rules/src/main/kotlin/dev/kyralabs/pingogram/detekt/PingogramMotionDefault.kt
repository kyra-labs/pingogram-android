package dev.kyralabs.pingogram.detekt

import io.github.detekt.psi.absolutePath
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Reports animations in the production sources of `:feature:*` modules that leave their spec to Compose
 * (`docs/plans/motion-expressive-plan.md` §4, `docs/DESIGN_SYSTEM.md` §5): a missing spec is not the theme's motion
 * but Compose's own default, which no theme changes. Features pass specs from `MaterialTheme.motionScheme` or use
 * `core:ui`'s motion helpers (`StateCrossfade`, `GrowingVisibility`, `animateItem(scope, motion)`…).
 *
 * It matches by name and arguments, as [PingogramHardcodedToken] does. Each check has a name, and the configuration's
 * `checks` lists those that run, so they are turned on in steps.
 */
class PingogramMotionDefault(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue =
        Issue(
            id = "PingogramMotionDefault",
            severity = Severity.Style,
            description = "Feature code must give every animation a spec from MaterialTheme.motionScheme.",
            debt = Debt.FIVE_MINS,
        )

    private val checks: Set<String> by lazy { valueOrDefault(CHECKS, DEFAULT_CHECKS).toSet() }

    override fun visitCondition(root: KtFile): Boolean =
        super.visitCondition(root) &&
            FEATURE_PRODUCTION_SOURCE.containsMatchIn(root.absolutePath().invariantSeparatorsPathString)

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val name = expression.calleeExpression?.text ?: return
        val check =
            when {
                name == "AnimatedVisibility" && !expression.hasEnterAndExit() -> VISIBILITY
                name == "AnimatedContent" && !expression.hasSpec("transitionSpec", expression.positional(3)) -> CONTENT
                name == "Crossfade" && !expression.hasSpec("animationSpec", expression.positional(3)) -> CROSSFADE
                name == "animateContentSize" && !expression.hasSpec("animationSpec", 1) -> CONTENT_SIZE
                ANIMATE_AS_STATE.matches(name) && !expression.hasSpec("animationSpec", asStatePositional(name)) ->
                    AS_STATE
                name in CONTAINER_SLIDES && !expression.hasSpec("animationSpec", 2) -> TRANSITIONS
                name in TRANSITIONS_WITH_SPEC_FIRST && !expression.hasSpec("animationSpec", 1) -> TRANSITIONS
                name in SPRINGS && expression.hasOnlyDefaults() -> SPRING_DEFAULT
                name in DECAYS -> SPRING_DEFAULT
                name in LOOPS -> LOOPS_CHECK
                name == "animateItem" && !expression.hasItemSpecs() -> ITEMS
                name == "SizeTransform" && !expression.hasSizeSpec() -> SIZE_TRANSFORM
                name == "animateScrollToPage" && !expression.hasSpec("animationSpec", 3) -> PAGER_SCROLL
                name == "AnimatedPane" && !expression.hasSpec("boundsAnimationSpec", 4) -> PANES
                name == "crossfade" -> COIL
                else -> null
            }
        if (check != null) report(expression, check)
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        val receiver = (expression.receiverExpression as? KtNameReferenceExpression)?.getReferencedName()
        val function = (expression.selectorExpression as? KtCallExpression)?.calleeExpression?.text
        if (receiver == "MotionScheme" && function in SCHEMES) report(expression, SCHEME_DIRECT)
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        super.visitSimpleNameExpression(expression)
        val isDirective =
            expression.getStrictParentOfType<KtImportDirective>() != null ||
                expression.getStrictParentOfType<KtPackageDirective>() != null
        if (expression.getReferencedName() in OTHER_ANIMATORS && !isDirective) report(expression, OTHER_ANIMATORS_CHECK)
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        val delegate = property.delegateExpression as? KtCallExpression ?: return
        val name = property.name ?: return
        if (!ANIMATE_AS_STATE.matches(delegate.calleeExpression?.text.orEmpty())) return
        val function = property.getStrictParentOfType<KtNamedFunction>() ?: return
        val readInComposition =
            function.collectDescendantsOfType<KtNameReferenceExpression> { it.getReferencedName() == name }.any { use ->
                use.parents.takeWhile { it != function }.none { it is KtLambdaExpression }
            }
        if (readInComposition) report(property, COMPOSITION_READ)
    }

    private fun report(
        element: KtElement,
        check: String,
    ) {
        if (check !in checks) return
        val code = element.text.replace(WHITESPACE, " ")
        val snippet = if (code.length > MAX_SNIPPET_LENGTH) code.take(MAX_SNIPPET_LENGTH - 1) + "…" else code
        report(CodeSmell(issue, Entity.from(element), "$check: `$snippet` ${MESSAGES.getValue(check)}"))
    }

    /** The arguments before the trailing lambda's place: those passed by position, not by name. */
    private fun KtCallExpression.positionalArguments(): List<KtValueArgument> =
        valueArguments.filter { it.getArgumentName() == null && it !in lambdaArguments }

    private fun KtCallExpression.named(): Set<String> =
        valueArguments.mapNotNull { it.getArgumentName()?.asName?.asString() }.toSet()

    /**
     * Whether the spec [name] is passed, by name or as the [position]th argument by position (1-based). An extension
     * call on a transition (`transition.AnimatedContent(…)`) has no target state first, so its position is one less.
     */
    private fun KtCallExpression.hasSpec(
        name: String,
        position: Int,
    ): Boolean = name in named() || positionalArguments().size >= position

    /** The position of the spec argument of a call that takes its target state first, one less on a transition. */
    private fun KtCallExpression.positional(position: Int): Int = if (isExtensionCall()) position - 1 else position

    private fun KtCallExpression.isExtensionCall(): Boolean =
        (parent as? KtDotQualifiedExpression)?.selectorExpression == this

    private fun KtCallExpression.hasEnterAndExit(): Boolean {
        val named = named()
        val enterAndExitByPosition = positionalArguments().size >= ANIMATED_VISIBILITY_EXIT_POSITION
        return enterAndExitByPosition || ("enter" in named && "exit" in named)
    }

    /** `spring()`, or a spring of named constants only (`spring(stiffness = Spring.StiffnessLow)`). */
    private fun KtCallExpression.hasOnlyDefaults(): Boolean =
        valueArguments.all { argument ->
            val value = argument.getArgumentExpression() as? KtDotQualifiedExpression
            (value?.receiverExpression as? KtNameReferenceExpression)?.getReferencedName() == "Spring"
        }

    /**
     * Compose's `animateItem` with all three specs, or `core:ui`'s `animateItem(scope, motion)`, whose first argument
     * is the item's scope.
     */
    private fun KtCallExpression.hasItemSpecs(): Boolean {
        val named = named()
        if ("motion" in named || "scope" in named) return true
        if (positionalArguments().firstOrNull()?.getArgumentExpression() is KtThisExpression) return true
        return named.containsAll(ITEM_SPECS) || valueArguments.size >= ITEM_SPECS.size
    }

    private fun KtCallExpression.hasSizeSpec(): Boolean =
        "sizeAnimationSpec" in named() || lambdaArguments.isNotEmpty() || positionalArguments().size >= 2

    private fun asStatePositional(name: String): Int = if (name == "animateValueAsState") 3 else 2

    companion object {
        /** The configuration key listing the checks that run. */
        const val CHECKS = "checks"

        const val VISIBILITY = "visibility"
        const val CONTENT = "content"
        const val CROSSFADE = "crossfade"
        const val CONTENT_SIZE = "contentSize"
        const val AS_STATE = "asState"
        const val TRANSITIONS = "transitions"
        const val SPRING_DEFAULT = "springDefault"
        const val LOOPS_CHECK = "loops"
        const val SCHEME_DIRECT = "schemeDirect"
        const val OTHER_ANIMATORS_CHECK = "otherAnimators"
        const val ITEMS = "items"
        const val SIZE_TRANSFORM = "sizeTransform"
        const val PAGER_SCROLL = "pagerScroll"
        const val PANES = "panes"
        const val COIL = "coil"
        const val COMPOSITION_READ = "compositionRead"

        /** The checks that run without configuration: those nothing in the features breaks (the plan's §4). */
        val DEFAULT_CHECKS =
            listOf(
                VISIBILITY,
                CONTENT,
                CROSSFADE,
                CONTENT_SIZE,
                AS_STATE,
                TRANSITIONS,
                SPRING_DEFAULT,
                LOOPS_CHECK,
                SCHEME_DIRECT,
                OTHER_ANIMATORS_CHECK,
            )

        /** The checks `chore/motion-lint-strict` turns on, once the findings they would report are fixed. */
        val STRICT_CHECKS = listOf(ITEMS, SIZE_TRANSFORM, PAGER_SCROLL, PANES, COIL, COMPOSITION_READ)

        private val MESSAGES =
            mapOf(
                VISIBILITY to
                    "needs enter and exit: core:ui's GrowingVisibility, or transitions from MaterialTheme.motionScheme.",
                CONTENT to "needs a transitionSpec: core:ui's StateCrossfade or stateCrossfade(motionScheme).",
                CROSSFADE to "needs an animationSpec from MaterialTheme.motionScheme, or core:ui's StateCrossfade.",
                CONTENT_SIZE to "needs an animationSpec from MaterialTheme.motionScheme.",
                AS_STATE to "needs an animationSpec from MaterialTheme.motionScheme.",
                TRANSITIONS to "needs an animationSpec from MaterialTheme.motionScheme, or a core:ui motion helper.",
                SPRING_DEFAULT to "is not the theme's motion: take a spec from MaterialTheme.motionScheme.",
                LOOPS_CHECK to
                    "loops: suppress it with a comment that says how it stops with reduced motion " +
                        "(core:ui's isReducedMotion).",
                SCHEME_DIRECT to "builds a motion scheme outside the theme: read MaterialTheme.motionScheme.",
                OTHER_ANIMATORS_CHECK to "is a View animator: animate with Compose and MaterialTheme.motionScheme.",
                ITEMS to "needs its three specs: core:ui's animateItem(scope, rememberItemMotion()).",
                SIZE_TRANSFORM to "needs its spec: SizeTransform { _, _ -> motionScheme.defaultSpatialSpec() }.",
                PAGER_SCROLL to "needs an animationSpec from MaterialTheme.motionScheme.",
                PANES to "needs a boundsAnimationSpec from MaterialTheme.motionScheme.",
                COIL to "is Coil's tween: fade the image on MaterialTheme.motionScheme, as core:ui's Avatar does.",
                COMPOSITION_READ to
                    "is read in composition, so it recomposes every frame: keep the State and read it in a lambda.",
            )

        /** `feature/<module>/src/<source set>/`, except test source sets (`test`, `androidTest`, `testFixtures`…). */
        private val FEATURE_PRODUCTION_SOURCE = Regex("""(^|/)feature/[^/]+/src/(?![^/]*[Tt]est)[^/]+/""")

        private val ANIMATE_AS_STATE = Regex("""animate\w*AsState""")

        /** Enter and exit transitions whose first parameter is their spec. */
        private val TRANSITIONS_WITH_SPEC_FIRST =
            setOf(
                "fadeIn",
                "fadeOut",
                "scaleIn",
                "scaleOut",
                "slideIn",
                "slideOut",
                "slideInHorizontally",
                "slideInVertically",
                "slideOutHorizontally",
                "slideOutVertically",
                "expandIn",
                "expandHorizontally",
                "expandVertically",
                "shrinkOut",
                "shrinkHorizontally",
                "shrinkVertically",
            )

        /** `AnimatedContentTransitionScope`'s slides, which take their direction first. */
        private val CONTAINER_SLIDES = setOf("slideIntoContainer", "slideOutOfContainer")

        private val SPRINGS = setOf("spring", "SpringSpec")
        private val DECAYS = setOf("exponentialDecay", "splineBasedDecay")
        private val LOOPS = setOf("infiniteRepeatable", "repeatable", "rememberInfiniteTransition")
        private val SCHEMES = setOf("standard", "expressive")
        private val OTHER_ANIMATORS = setOf("ValueAnimator", "ObjectAnimator", "ViewPropertyAnimator")
        private val ITEM_SPECS = setOf("fadeInSpec", "placementSpec", "fadeOutSpec")

        /** `AnimatedVisibility(visible, modifier, enter, exit, …)`: both given by position from the fourth on. */
        private const val ANIMATED_VISIBILITY_EXIT_POSITION = 4

        private val WHITESPACE = Regex("""\s+""")
        private const val MAX_SNIPPET_LENGTH = 60
    }
}

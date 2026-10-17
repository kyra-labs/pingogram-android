package dev.kyralabs.pingogram.detekt

import io.github.detekt.psi.absolutePath
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Reports literal colors, text sizes, sizes, corner shapes and animation specs in the production
 * sources of `:feature:*` modules (CLAUDE.md hard rule 4, `docs/DESIGN_SYSTEM.md` §1). Features read
 * them from `MaterialTheme.colorScheme`, `typography`, `spacing`, `shapes` and `motionScheme`; only
 * `:core:ui` holds the literal values behind those tokens.
 */
class PingogramHardcodedToken(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue =
        Issue(
            id = "PingogramHardcodedToken",
            severity = Severity.Style,
            description = "Feature code must take colors, type, sizes, shapes and motion from MaterialTheme tokens.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitCondition(root: KtFile): Boolean =
        super.visitCondition(root) &&
            FEATURE_PRODUCTION_SOURCE.containsMatchIn(root.absolutePath().invariantSeparatorsPathString)

    // Each visitor reports the outermost offending expression once and then skips its children, so
    // `RoundedCornerShape(16.dp)` or `tween(300, easing = LinearEasing)` give one finding, not three.

    override fun visitCallExpression(expression: KtCallExpression) {
        val token =
            when (expression.calleeExpression?.text) {
                "Color" -> Token.Color
                in SHAPES -> Token.Shape.takeIf { expression.hasNumberArgument() }
                in LITERAL_MOTION -> Token.Motion
                in PARAMETERIZED_MOTION -> Token.Motion.takeIf { expression.hasNumberArgument() }
                else -> null
            }
        if (token != null) report(expression, token) else super.visitCallExpression(expression)
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        val receiver = expression.receiverExpression
        val receiverName = receiver.simpleName()
        val member = (expression.selectorExpression as? KtNameReferenceExpression)?.getReferencedName()
        val function = (expression.selectorExpression as? KtCallExpression)?.calleeExpression?.text
        val token =
            when {
                member == "sp" && receiver.numberLiteral() != null -> Token.TextSize
                member == "dp" && receiver.numberLiteral()?.isZero() == false -> Token.Size
                receiverName == "Color" && (member in NAMED_COLORS || function in COLOR_FACTORIES) -> Token.Color
                receiverName == "Spring" && member != null -> Token.Motion
                else -> null
            }
        if (token != null) report(expression, token) else super.visitDotQualifiedExpression(expression)
    }

    override fun visitArgument(argument: KtValueArgument) {
        val name = argument.getArgumentName()?.asName?.asString()
        if (name in TIMING_NAMES && argument.getArgumentExpression().hasNumberLiteral()) {
            report(argument, Token.Motion)
        } else {
            super.visitArgument(argument)
        }
    }

    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        // `durationMillis = 300` inside a keyframes-style builder.
        val isTimingAssignment = expression.operationToken == KtTokens.EQ && expression.left?.text in TIMING_NAMES
        if (isTimingAssignment && expression.right.hasNumberLiteral()) {
            report(expression, Token.Motion)
        } else {
            super.visitBinaryExpression(expression)
        }
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        super.visitSimpleNameExpression(expression)
        val isDirective =
            expression.getStrictParentOfType<KtImportDirective>() != null ||
                expression.getStrictParentOfType<KtPackageDirective>() != null
        if (NAMED_EASING.matches(expression.getReferencedName()) && !isDirective) report(expression, Token.Motion)
    }

    private fun report(
        element: KtElement,
        token: Token,
    ) {
        val code = element.text.replace(WHITESPACE, " ")
        val snippet = if (code.length > MAX_SNIPPET_LENGTH) code.take(MAX_SNIPPET_LENGTH - 1) + "…" else code
        val message = "Hard-coded ${token.kind} `$snippet` in feature code; use ${token.replacement} instead."
        report(CodeSmell(issue, Entity.from(element), message))
    }

    /** The literal behind `16`, `-4`, `(1.5f)`; null for anything else. */
    private fun KtExpression.numberLiteral(): KtConstantExpression? {
        val expression = KtPsiUtil.safeDeparenthesize(this)
        val unsigned = (expression as? KtPrefixExpression)?.takeIf { it.operationToken in SIGNS }?.baseExpression
        val literal = unsigned ?: expression
        return (literal as? KtConstantExpression)?.takeIf { it.node.elementType in NUMBER_LITERALS }
    }

    private fun KtConstantExpression.isZero(): Boolean = text.replace("_", "").trimEnd('f', 'F').toDoubleOrNull() == 0.0

    private fun PsiElement?.hasNumberLiteral(): Boolean =
        this != null && anyDescendantOfType<KtConstantExpression> { it.node.elementType in NUMBER_LITERALS }

    private fun KtCallExpression.hasNumberArgument(): Boolean = valueArguments.any { it.hasNumberLiteral() }

    /** `Color` for both `Color` and `androidx.compose.ui.graphics.Color`. */
    private fun KtExpression.simpleName(): String? =
        when (this) {
            is KtNameReferenceExpression -> getReferencedName()
            is KtDotQualifiedExpression -> selectorExpression?.simpleName()
            else -> null
        }

    private enum class Token(
        val kind: String,
        val replacement: String,
    ) {
        Color("color", "a MaterialTheme.colorScheme role or LocalPingogramColors"),
        TextSize("text size", "a MaterialTheme.typography style"),
        Size("size", "a MaterialTheme.spacing token"),
        Shape("shape", "MaterialTheme.shapes or MaterialShapes"),
        Motion("animation spec", "a MaterialTheme.motionScheme spec"),
    }

    private companion object {
        /** `feature/<module>/src/<source set>/`, except test source sets (`test`, `androidTest`, `testFixtures`…). */
        val FEATURE_PRODUCTION_SOURCE = Regex("""(^|/)feature/[^/]+/src/(?![^/]*[Tt]est)[^/]+/""")

        /** Compose's named colors; `Color.Transparent` and `Color.Unspecified` are not design values. */
        val NAMED_COLORS =
            setOf(
                "Black",
                "DarkGray",
                "Gray",
                "LightGray",
                "White",
                "Red",
                "Green",
                "Blue",
                "Yellow",
                "Cyan",
                "Magenta",
            )
        val COLOR_FACTORIES = setOf("hsv", "hsl")
        val SHAPES =
            setOf("RoundedCornerShape", "AbsoluteRoundedCornerShape", "CutCornerShape", "AbsoluteCutCornerShape")

        /** Duration-based specs and custom curves; Material 3 Expressive motion is the scheme's springs. */
        val LITERAL_MOTION =
            setOf(
                "tween",
                "TweenSpec",
                "keyframes",
                "KeyframesSpec",
                "keyframesWithSpline",
                "CubicBezierEasing",
            )

        /** Reported only with literal arguments: a plain `snap()` is the reduced-motion fallback (§5). */
        val PARAMETERIZED_MOTION = setOf("snap", "SnapSpec", "spring", "SpringSpec")
        val TIMING_NAMES = setOf("durationMillis", "delayMillis")
        val NAMED_EASING =
            Regex(
                "(Linear|FastOutSlowIn|LinearOutSlowIn|FastOutLinearIn)Easing|" +
                    "Ease(In|Out|InOut)?(Sine|Cubic|Quint|Circ|Quad|Quart|Expo|Back|Elastic|Bounce)?",
            )

        val NUMBER_LITERALS = setOf(KtNodeTypes.INTEGER_CONSTANT, KtNodeTypes.FLOAT_CONSTANT)
        val SIGNS = setOf(KtTokens.MINUS, KtTokens.PLUS)
        val WHITESPACE = Regex("""\s+""")
        const val MAX_SNIPPET_LENGTH = 60
    }
}

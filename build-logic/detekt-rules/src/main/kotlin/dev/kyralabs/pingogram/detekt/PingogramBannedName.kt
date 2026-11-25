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
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Reports a name banned from production sources (every module's `src/<source set>/` but its test source sets), where
 * the project's rules ban it by discipline only. Like [PingogramHardcodedToken] it matches by name, so a call is
 * reported however it is imported. With [callsOnly], only calls of the name are, not other references to it.
 */
abstract class PingogramBannedName(
    config: Config,
    id: String,
    private val banned: String,
    private val reason: String,
    private val callsOnly: Boolean,
) : Rule(config) {
    override val issue = Issue(id, Severity.Defect, "`$banned` is banned from production code.", Debt.FIVE_MINS)

    override fun visitCondition(root: KtFile): Boolean =
        super.visitCondition(root) &&
            PRODUCTION_SOURCE.containsMatchIn(root.absolutePath().invariantSeparatorsPathString)

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        super.visitSimpleNameExpression(expression)
        if (expression.getReferencedName() != banned) return
        val isDirective =
            expression.getStrictParentOfType<KtImportDirective>() != null ||
                expression.getStrictParentOfType<KtPackageDirective>() != null
        val isCall = (expression.parent as? KtCallExpression)?.calleeExpression == expression
        if (isDirective || (callsOnly && !isCall)) return
        report(CodeSmell(issue, Entity.from(expression), "`$banned` $reason"))
    }

    private companion object {
        /** `src/<source set>/` of any module, except test source sets (`test`, `androidTest`, `testFoss`…). */
        val PRODUCTION_SOURCE = Regex("""(^|/)src/(?![^/]*[Tt]est)[^/]+/""")
    }
}

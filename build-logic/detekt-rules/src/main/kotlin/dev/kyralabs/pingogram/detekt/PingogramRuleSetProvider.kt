package dev.kyralabs.pingogram.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/** The `pingogram` detekt rule set; its defaults live in `config/config.yml` of this jar. */
class PingogramRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "pingogram"

    override fun instance(config: Config): RuleSet = RuleSet(ruleSetId, listOf(PingogramHardcodedToken(config)))
}

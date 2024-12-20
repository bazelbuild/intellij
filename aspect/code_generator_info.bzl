"""Data required for the code-generator system"""

# The following is a list of the languages to the set of Rule names
# which can be considered code-generators for that language. Look
# for the `get_code_generator_rule_names` function in the aspect
# logic that integrates with this constant.

CODE_GENERATOR_RULE_NAMES = struct(
# TEMPLATE-INCLUDE-BEGIN
###foreach( $aLanguageClassRuleNames in $languageClassRuleNames )
##    $aLanguageClassRuleNames.languageClass.name = [
###foreach ( $aRuleName in $aLanguageClassRuleNames.ruleNames )
##        "$aRuleName",
###end
##    ],
###end
# TEMPLATE-INCLUDE-END
)

"""
This module declares a noop rule with analysis_test=True
"""

def _noop_analysis_test_impl(ctx):
    return [AnalysisTestResultInfo(success = True, message = "PASS")]

noop_analysis_test = rule(
    implementation = _noop_analysis_test_impl,
    analysis_test = True,
)

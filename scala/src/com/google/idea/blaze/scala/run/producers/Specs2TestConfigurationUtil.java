package com.google.idea.blaze.scala.run.producers;

import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.intellij.execution.PsiLocation;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil;
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider;

import java.util.Optional;

public class Specs2TestConfigurationUtil {
    private Specs2TestConfigurationUtil() {}

    @Nullable
    public static String getTestName(PsiLocation location) {
        scala.Tuple2<ScTypeDefinition, String> pair = TestConfigurationUtil
                                                        .specs2ConfigurationProducer()
                                                        .getLocationClassAndTest(location);

        return pair._2;
    }

    @NotNull
    public static String getParentScope(PsiElement element) {
        // Extracts the scope name from the "should" block, providing a complete name for test filter.
        // This is needed so the pattern match in Bazel succeeds against a fully qualified JUnit description

        Optional<PsiElement> parentScope = getParentScopeElement(element);
        return parentScope
                .map(elem -> extractScope(elem))
                .orElse("");
    }

    private static String extractScope(PsiElement element) {
        ScInfixExpr testSuite = (ScInfixExpr)element;
        if (testSuite == null)
            return null;

        String should = testSuite.operation().refName();
        if (should.equals("should")) {
            String scope =
                    TestConfigurationUtil.getStaticTestName(testSuite.lOp(), false).get()
                            + " "
                            + should
                            + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER;
            return scope;
        }
        return null;
    }

    private static Optional<PsiElement> getParentScopeElement(PsiElement element) {
        for (PsiElement elem = element; elem != null; elem = elem.getParent()) {
            if (TestNodeProvider.isSpecs2ScopeExpr(elem))
                return Optional.of(elem);
        }
        return Optional.empty();
    }
}

package com.google.idea.sdkcompat.java;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PatternConditionPlus;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;

public interface JavaSdkCompat {
    /** #api203: JavaClassQualifiedNameReference API changed in 2021.1 */
    static PatternCondition<PsiElementBase> nameCondition(final ElementPattern<?> pattern) {
        return new PatternConditionPlus<PsiElementBase, String>("_withPsiName", pattern) {
            @Override
            public boolean processValues(final PsiElementBase psiElementBase, final ProcessingContext processingContext,
                                         final PairProcessor<String, ProcessingContext> pairProcessor) {
                return pairProcessor.process(psiElementBase.getName(), processingContext);
            }
        };
    }
}

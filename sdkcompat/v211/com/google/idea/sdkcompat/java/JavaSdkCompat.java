package com.google.idea.sdkcompat.java;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PatternConditionPlus;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;

public interface JavaSdkCompat {
    /** #api203: PatternConditionPlus API changed in 2021.1 */
    static PatternCondition<PsiElementBase> nameCondition(final ElementPattern<?> pattern) {
        return new PatternConditionPlus<PsiElementBase, String>("_withPsiName", pattern) {
            @Override
            public boolean processValues(final PsiElementBase t, final ProcessingContext context,
                                         final PairProcessor<? super String, ? super ProcessingContext> processor) {
                return processor.process(t.getName(), context);
            }
        };
    }
}

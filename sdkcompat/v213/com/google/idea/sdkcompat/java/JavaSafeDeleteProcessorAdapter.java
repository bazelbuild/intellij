package com.google.idea.sdkcompat.java;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor;
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// #api223
public abstract class JavaSafeDeleteProcessorAdapter extends JavaSafeDeleteProcessor {
    public abstract boolean ignoreUsage(Object usage_);

    /**
     * Delegates to JavaSafeDeleteProcessor, then removes indirect glob references which we don't want
     * to block safe delete.
     */
    @Override
    public @Nullable NonCodeUsageSearchInfo findUsages(@NotNull PsiElement element,
                                                       PsiElement [] allElementsToDelete,
                                                       @NotNull List<UsageInfo> usages) {
        NonCodeUsageSearchInfo superResult = super.findUsages(element, allElementsToDelete, usages);
        usages.removeIf(this::ignoreUsage);
        return superResult;
    }
}

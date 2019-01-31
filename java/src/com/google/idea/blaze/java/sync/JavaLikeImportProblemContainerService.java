package com.google.idea.blaze.java.sync;

import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.ui.problems.ImportIssue;
import com.google.idea.blaze.base.ui.problems.ImportProblemContainerServiceBase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class JavaLikeImportProblemContainerService extends ImportProblemContainerServiceBase {
    @Override
    protected IntentionAction createImportIssueFix(ImportIssue importIssue, String tooltip, List<Label> importClassTargets, Optional<Label> currentClassTarget) {
        return new ImportIssueQuickFix(
                tooltip.substring(7),
                importIssue,
                importClassTargets,
                currentClassTarget.get()
        );

    }

    @Override
    public List<PsiClass> getImportsFromWildCard(@NotNull PsiElement element, String importedPackageName) {
        return WildCardImportExtractor.getImportsFromWildCard(element, importedPackageName);
    }
}

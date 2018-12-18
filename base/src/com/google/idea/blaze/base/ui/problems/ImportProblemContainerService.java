package com.google.idea.blaze.base.ui.problems;

import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Optional;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR;

public class ImportProblemContainerService {
    private HashMap<String, ImportIssue>  issues = new HashMap();

    public void resetIssues() {
        issues = new HashMap();
    }

    public void setIssue(IssueOutput issue, PsiFile file, ImportIssueType importIssueType) {
        String originalLine = file.getText().split("\n")[issue.getLine() - 1];
        ImportIssue importIssue = new ImportIssue(issue, file, originalLine, importIssueType);
        String importClassKey = originalLine.replace("import", "").trim();

        issues.put(importClassKey, importIssue);
    }

    public Optional<ImportIssue> findIssue(PsiElement psiElement) {
        String importClass = psiElement.
                getText().
                replace(";", "").
                replace("import", "").trim()
                ;

        if(issues.containsKey(importClass)){
            ImportIssue importIssue = issues.get(importClass);
            return Optional.of(importIssue);
        }

        return Optional.empty();

    }

    public void createImportErrorAnnotation(@NotNull PsiElement element, @NotNull AnnotationHolder holder, Optional<ImportIssue> issue) {
        ImportIssue importIssue = issue.get();
        String tooltip = importIssue.getOriginalIssue().getMessage();
        Annotation errorAnnotation = holder.createErrorAnnotation(element, tooltip);
        errorAnnotation.setHighlightType(GENERIC_ERROR);
//                errorAnnotation.registerFix(new ImportIssueQuickFix(tooltip.substring(7), importIssue));
    }
}


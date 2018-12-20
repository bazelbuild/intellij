package com.google.idea.blaze.base.ui.problems;

import com.google.idea.blaze.base.actions.DependencyLabelFinder;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.project.Project;
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
        String importClassKey = getImportIssueKey(originalLine);

        issues.put(importClassKey, importIssue);
    }

    @NotNull
    private String getImportIssueKey(String originalLine) {
        return originalLine.replace("import", "").trim();
    }

    public Optional<ImportIssue> findIssue(PsiElement psiElement) {
        String importClass = getImportIssueKey(psiElement.
                getText().
                replace(";", ""))
                ;

        if(issues.containsKey(importClass)){
            ImportIssue importIssue = issues.get(importClass);
            return Optional.of(importIssue);
        }

        return Optional.empty();

    }

    public void createImportErrorAnnotation(@NotNull PsiElement element, @NotNull AnnotationHolder holder, ImportIssue importIssue) {
        String tooltip = importIssue.getOriginalIssue().getMessage();
        Annotation errorAnnotation = holder.createErrorAnnotation(element, tooltip);
        errorAnnotation.setHighlightType(GENERIC_ERROR);
        String originalLine = importIssue.getOriginalLine();
        Project project = element.getProject();
        Optional<Label> importClassTarget = findClassTarget(originalLine, project);
        Optional<Label> currentClassTarget = Optional.of(
                DependencyLabelFinder.findTarget(project, element, element.getContainingFile().getVirtualFile())
        );
        if(importClassTarget.isPresent() && currentClassTarget.isPresent()){
            errorAnnotation.registerFix(
                    new ImportIssueQuickFix(
                            tooltip.substring(7),
                            importIssue,
                            importClassTarget.get(),
                            currentClassTarget.get()
                    )
            );
        }
    }


    private Optional<Label> findClassTarget(String importLine, Project project) {
        String lineWithoutImportKeyword = getImportIssueKey(importLine);
        int indexOfClassNameSeperator = lineWithoutImportKeyword.lastIndexOf(".");
        String packageOnly = lineWithoutImportKeyword.substring(0, indexOfClassNameSeperator);
        PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageOnly);
        String simpleClassName = lineWithoutImportKeyword.substring(indexOfClassNameSeperator + 1);

        return findImportClassTargetLabel(project, aPackage, simpleClassName);
    }

    private Optional<Label> findImportClassTargetLabel(Project project, PsiPackage aPackage, String simpleClassName) {
        Optional<Label> target = Optional.empty();
        for (PsiClass psiClass : aPackage.getClasses()) {
            if (psiClass.getName().equals(simpleClassName)) {
                Label targetLabel = DependencyLabelFinder.findTarget(
                        project,
                        psiClass.getOriginalElement(),
                        psiClass.getContainingFile().getVirtualFile()
                );
                target = Optional.of(targetLabel);
            }
        }
        return target;
    }

    public void removeIssue(ImportIssue issue) {
        String importIssueKey = getImportIssueKey(issue.getOriginalLine());
        if(issues.containsKey(importIssueKey)){
            issues.remove(importIssueKey);
        }
    }
}


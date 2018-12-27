package com.google.idea.blaze.java.sync;

import com.google.idea.blaze.base.ui.problems.ImportIssue;
import com.google.idea.blaze.base.ui.problems.ImportProblemContainerService;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStaticStatement;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class ImportIssueAnnotator implements Annotator {

    ImportProblemContainerService importProblemContainerService =
            ServiceManager.getService(ImportProblemContainerService.class);

    @Override
    public void annotate(@NotNull final PsiElement element, @NotNull AnnotationHolder holder) {
        if (element instanceof PsiImportStatement || element instanceof PsiImportStaticStatement) {
            Optional<ImportIssue> issue = importProblemContainerService.findIssue(element);
            if(issue.isPresent()){
                importProblemContainerService.createImportErrorAnnotation(element, holder, issue.get());
            }
        }
    }
}

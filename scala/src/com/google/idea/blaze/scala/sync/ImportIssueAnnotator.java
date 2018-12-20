package com.google.idea.blaze.scala.sync;

import com.google.idea.blaze.base.ui.problems.ImportIssue;
import com.google.idea.blaze.base.ui.problems.ImportProblemContainerService;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt;

import java.util.Optional;


public class ImportIssueAnnotator implements Annotator {

    ImportProblemContainerService importProblemContainerService =
            ServiceManager.getService(ImportProblemContainerService.class);

    @Override
    public void annotate(@NotNull final PsiElement element, @NotNull AnnotationHolder holder) {
        if (element instanceof ScImportStmt) {
            Optional<ImportIssue> issue = importProblemContainerService.findIssue(element);
            if(issue.isPresent()){
                importProblemContainerService.createImportErrorAnnotation(element, holder, issue.get());
            }
        }
    }
}

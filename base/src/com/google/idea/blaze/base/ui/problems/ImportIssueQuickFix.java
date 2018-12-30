package com.google.idea.blaze.base.ui.problems;

import com.google.idea.blaze.base.buildmodifier.BuildFileModifier;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ImportIssueQuickFix implements IntentionAction {
    private final String key;
    private final ImportIssue importIssue;
    private List<Label> importClassTargets;
    private Label currentClassTarget;
    ImportProblemContainerService importProblemContainerService =
            ServiceManager.getService(ImportProblemContainerService.class);

    public ImportIssueQuickFix(String key, ImportIssue importIssue, List<Label> importClassTargets, Label currentClassTarget) {
        this.key = key;
        this.importIssue = importIssue;
        this.importClassTargets = importClassTargets;
        this.currentClassTarget = currentClassTarget;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getText() {
        if (importIssue.getImportIssueType() == ImportIssueType.DEPENDENCY_MISSING_IN_BUILD_FILE) {
            return "Add dependency to build file";
        }
        return "Import issue";
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
        return "Import Issues";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
        BuildFileModifier buildFileModifier = BuildFileModifier.getInstance();
        VirtualFile containingVirtualFile = importIssue.getFile().getVirtualFile();
        importClassTargets.forEach( importClassTarget -> {
            Runnable runnable = () -> {
                Document document = FileDocumentManager.getInstance().getDocument(psiFile.getVirtualFile());
                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
                buildFileModifier.addDepToRule(project, importClassTarget, containingVirtualFile);
                PsiDocumentManager.getInstance(project).commitDocument(document);
            };
            WriteCommandAction.runWriteCommandAction(project, runnable);
        });

        importProblemContainerService.removeIssue(importIssue);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public String toString() {
        return "ImportIssueQuickFix{" +
                "key='" + key + '\'' +
                ", importIssue=" + importIssue +
                ", importClassTargets=" + importClassTargets +
                ", currentClassTarget=" + currentClassTarget +
                '}';
    }
}

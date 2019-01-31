package com.google.idea.blaze.base.ui.problems;

import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.psi.PsiFile;

public class ImportIssue {
    private final IssueOutput originalIssue;
    private final PsiFile file;
    private final String originalLine;
    private final ImportIssueType importIssueType;

    public ImportIssue(IssueOutput originalIssue, PsiFile file, String originalLine, ImportIssueType importIssueType) {

        this.originalIssue = originalIssue;
        this.file = file;
        this.originalLine = originalLine;
        this.importIssueType = importIssueType;
    }

    public IssueOutput getOriginalIssue() {
        return originalIssue;
    }

    public PsiFile getFile() {
        return file;
    }

    public String getOriginalLine() {
        return originalLine;
    }

    public ImportIssueType getImportIssueType() {
        return importIssueType;
    }
}

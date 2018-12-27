package com.google.idea.blaze.base.ui.problems;

import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.regex.Pattern;

public class ImportIssueResolver {

    private static final String NEW_LINE = "\n";
    private static String missingImportRegEx = "object .* is not a member of package .*";
    private static String wildCardOrStaticRegEx = "package .* does not exist";
    private static String javaStaticImport = "static import only from classes and interfaces";

    private static final String IMPORT_STATIC_IDENTIFIER = "import static";

    public static boolean isImportIssue(IssueOutput issue, VirtualFile file, Project project) {
        boolean importIssue = false;
        if (file != null) {
            boolean missingImportDependencyInBuildFile =
                    Pattern.compile(missingImportRegEx).matcher(issue.getMessage()).find();
            boolean missingImportDependencyWildOrStaticJava =
                    Pattern.compile(wildCardOrStaticRegEx).matcher(issue.getMessage()).find();
            boolean missingImportDependencyJavaStaticImport =
                    Pattern.compile(javaStaticImport).matcher(issue.getMessage()).find();

            PsiManager psiManager = PsiManager.getInstance(project);
            PsiFile psiFile = psiManager.findFile(file);
            String originalLine = getOriginalLineByIssue(issue, psiFile);

            importIssue = missingImportDependencyJavaStaticImport ||
                    missingImportDependencyInBuildFile ||
                    missingWildCardImport(originalLine, missingImportDependencyWildOrStaticJava) ||
                    isStaticImport(originalLine, missingImportDependencyWildOrStaticJava);
        }

        return importIssue;
    }

    private static boolean isStaticImport(String originalLine, boolean missingImportDependencyWildOrStaticJava) {
        return missingImportDependencyWildOrStaticJava && originalLine.contains(IMPORT_STATIC_IDENTIFIER);
    }

    private static boolean missingWildCardImport(String originalLine,
                                                 boolean missingImportDependencyWildcardOrStaticJava) {
        return missingImportDependencyWildcardOrStaticJava && ImportLineUtils.isWildCardImportLine(originalLine);
    }


    public static String getOriginalLineByIssue(IssueOutput issue, PsiFile psiFile) {
        return psiFile.getText().split(NEW_LINE)[issue.getLine() - 1];
    }
}

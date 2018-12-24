package com.google.idea.blaze.base.ui.problems;

import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.regex.Pattern;

import static com.google.idea.blaze.base.ui.problems.ImportIssueConstants.*;

public class ImportIssueResolver {

    private static final String NEW_LINE = "\n";
    private static String missingImportRegEx = "object .* is not a member of package .*";
    private static String wildCardOrStaticRegEx = "package .* does not exist";
    private static final String IMPORT_STATIC_IDENTIFIER = "import static";

    public static boolean isImportIssue(IssueOutput issue, VirtualFile file, Project project){
        boolean importIssue = false;
        if(file != null){
            boolean missingImportDependencyInBuildFile =
                    Pattern.compile(missingImportRegEx).matcher(issue.getMessage()).find();
            boolean missingImportDependencyWildOrStaticJava =
                    Pattern.compile(wildCardOrStaticRegEx).matcher(issue.getMessage()).find();

            PsiManager psiManager = PsiManager.getInstance(project);
            PsiFile psiFile =  psiManager.findFile(file);
            String originalLine = getOriginalLineByIssue(issue, psiFile);

            importIssue =  missingImportDependencyInBuildFile ||
                    missingWildCardImport(originalLine, missingImportDependencyWildOrStaticJava) ||
                    (missingImportDependencyWildOrStaticJava && isStaticImport(originalLine));
        }

        return importIssue;
    }

    private static boolean isStaticImport(String originalLine) {
        return originalLine.contains(IMPORT_STATIC_IDENTIFIER);
    }

    private static boolean missingWildCardImport(String originalLine,
                                          boolean missingImportDependencyWildcardOrStaticJava) {
        return missingImportDependencyWildcardOrStaticJava && isWildCardImportLine(originalLine);
    }

    public static boolean isWildCardImportLine(String originalLine) {
        String lineWithoutDotComma = originalLine.replace(JAVA_EOFL_IDENTIFIER, "");
        return lineWithoutDotComma.endsWith(JAVA_WILDCARD_KEYWORD) ||
                lineWithoutDotComma.endsWith(SCALA_WILDCARD_KEYWORD);
    }

    public static String getOriginalLineByIssue(IssueOutput issue, PsiFile psiFile) {
        return psiFile.getText().split(NEW_LINE)[issue.getLine() - 1];
    }
}

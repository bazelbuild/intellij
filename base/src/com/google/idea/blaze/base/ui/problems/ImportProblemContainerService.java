package com.google.idea.blaze.base.ui.problems;

import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.idea.blaze.base.actions.DependencyLabelFinder.findTarget;
import static com.google.idea.blaze.base.ui.problems.ImportIssueResolver.getOriginalLineByIssue;
import static com.google.idea.blaze.base.ui.problems.ImportLineUtils.*;
import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR;

public class ImportProblemContainerService {
    public static final String EMPTY_STRING = "";
    private HashMap<String, ImportIssue>  issues = new HashMap();

    public void resetIssues() {
        issues = new HashMap();
    }

    public void setIssue(IssueOutput issue, PsiFile file, ImportIssueType importIssueType) {
        String originalLine = getOriginalLineByIssue(issue, file);
        ImportIssue importIssue = new ImportIssue(issue, file, originalLine, importIssueType);
        Optional<String> importClassKey = getImportIssueKey(originalLine, file);

        if(importClassKey.isPresent()) {
            issues.put(importClassKey.get(), importIssue);
        }

    }

    @NotNull
    private Optional<String> getImportIssueKey(String originalLine, PsiFile file) {
        return Optional.ofNullable(file).flatMap(psiFile -> {
            String directoryName = file.getParent().getName();
            Optional<String> maybePackageName = getPackageName(originalLine);
            return maybePackageName.map(packageName ->  directoryName+"\\/"+packageName);
        });
    }


    public Optional<ImportIssue> findIssue(PsiElement psiElement) {
        PsiFile file = psiElement.getContainingFile();
        Optional<String> importClass = getImportIssueKey(psiElement.getText(), file);

        if(doesIssueExist(importClass)){
            ImportIssue importIssue = issues.get(importClass.get());
            return Optional.of(importIssue);
        } else {
            return Optional.empty();
        }
    }

    public void createImportErrorAnnotation(@NotNull PsiElement element, @NotNull AnnotationHolder holder, ImportIssue importIssue) {
        String tooltip = importIssue.getOriginalIssue().getMessage();
        Annotation errorAnnotation = holder.createErrorAnnotation(element, tooltip);
        errorAnnotation.setHighlightType(GENERIC_ERROR);
        String originalLine = importIssue.getOriginalLine();
        Project project = element.getProject();

        List<Label> importClassTargets = getImportClassTargets(element, originalLine, project).stream().distinct().collect(Collectors.toList());
        Optional<Label> currentClassTarget = Optional.of(
                findTarget(project, element, element.getContainingFile().getVirtualFile())
        );

        if(!importClassTargets.isEmpty() && currentClassTarget.isPresent()){
            errorAnnotation.registerFix(
                    new ImportIssueQuickFix(
                            tooltip.substring(7),
                            importIssue,
                            importClassTargets,
                            currentClassTarget.get()
                    )
            );
        }
    }

    @NotNull
    private List<Label> getImportClassTargets(@NotNull PsiElement element, String originalLine, Project project) {
        List<Label> importClassTargets = Lists.newArrayList();
        ImportType importType = getImportType(originalLine);
        switch (importType) {
            case REGULAR:
                addSingleClassImportTargetIssue(originalLine, project, importClassTargets);
                break;
            case SCALA_WILDCARD:
            case JAVA_WILDCARD:
                Optional<String> importedPackageName = getPackageName(originalLine);
                if(importedPackageName.isPresent()) {
                    addWildCardTargets(element, project, importClassTargets, importedPackageName.get());
                }
                break;
            case MULTIPLE_SCALA:
                List<String> classNames = getClassNames(originalLine, importType);
                Optional<String> packageName = getPackageName(originalLine);
                if(packageName.isPresent()){
                    classNames.stream().forEach(className ->
                            addSingleClassImportTargetIssue(
                                    packageName.get() + ImportIssueConstants.PACKAGE_SEPARATOR + className,
                                    project,
                                    importClassTargets
                            )
                    );
                }

                break;
            default:
                break;
        }

        return importClassTargets;
    }

    private void addSingleClassImportTargetIssue(String originalLine, Project project, List<Label> importClassTargets) {
        Optional<Label> classTarget = findClassTarget(originalLine, project);
        if(classTarget.isPresent()) {
            importClassTargets.add(classTarget.get());
        }
    }

    private void addWildCardTargets(@NotNull PsiElement element, Project project, List<Label> importClassTargets, String importedPackageName) {
        List<PsiClass> importsFromWildCard = WildCardImportExtractor.getImportsFromWildCard(element, importedPackageName);
        for (PsiClass importFromWildCard : importsFromWildCard) {
            VirtualFile virtualFile = importFromWildCard.getContainingFile().getVirtualFile();
            Optional<Label> classTarget = Optional.of(findTarget(project, element, virtualFile));
            if(classTarget.isPresent()) {
                importClassTargets.add(classTarget.get());
            }
        }
    }


    private Optional<Label> findClassTarget(String importLine, Project project) {
        Optional<Label> importClassTargetLabel = Optional.empty();
        Optional<String> packageName = getPackageName(importLine);

        if(packageName.isPresent()){
            PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName.get());
            int indexOfClassNameSeparator = importLine.lastIndexOf(".");
            String simpleClassName = importLine.substring(indexOfClassNameSeparator + 1);

            importClassTargetLabel = findImportClassTargetLabel(project, aPackage, simpleClassName);
        }

        return importClassTargetLabel;
    }

    private Optional<Label> findImportClassTargetLabel(Project project, PsiPackage psiPackage, String simpleClassName) {
        Optional<Label> target = Optional.empty();

        if(psiPackage != null){
            for (PsiClass psiClass : psiPackage.getClasses()) {
                if (psiClass.getName().equals(simpleClassName)) {
                    Label targetLabel = findTarget(
                            project,
                            psiClass.getOriginalElement(),
                            psiClass.getContainingFile().getVirtualFile()
                    );
                    target = Optional.of(targetLabel);
                }
            }
        }

        return target;
    }

    public void removeIssue(ImportIssue issue) {
        PsiFile file = issue.getFile();
        Optional<String> importIssueKey = getImportIssueKey(issue.getOriginalLine(), file);
        if(doesIssueExist(importIssueKey)){
            issues.remove(importIssueKey.get());
        }
    }

    private boolean doesIssueExist(Optional<String> importIssueKey) {
        return importIssueKey.isPresent() && issues.containsKey(importIssueKey.get());
    }
}


package com.google.idea.blaze.base.actions;

import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

import javax.annotation.Nullable;
import java.io.File;

public class DependencyLabelFinder {
    @Nullable
    public static Label findTarget(Project project, PsiElement psiElement, VirtualFile virtualFile) {
        Label target = getSelectedTarget(psiElement);
        return target != null ? target : getTargetBuildingFile(project, virtualFile);
    }

    /** Find a BUILD target building the selected file, if relevant. */
    @Nullable
    private static Label getTargetBuildingFile(Project project, VirtualFile virtualFile) {
        BlazePackage parentPackage = BuildFileUtils.getBuildFile(project, virtualFile);
        if (parentPackage == null) {
            return null;
        }
        PsiElement target =
                BuildFileUtils.findBuildTarget(project, parentPackage, new File(virtualFile.getPath()));
        return target instanceof FuncallExpression
                ? ((FuncallExpression) target).resolveBuildLabel()
                : null;
    }

    @Nullable
    private static Label getSelectedTarget(PsiElement psiElement) {
        if (!(psiElement instanceof FuncallExpression)) {
            return null;
        }
        return ((FuncallExpression) psiElement).resolveBuildLabel();
    }
}

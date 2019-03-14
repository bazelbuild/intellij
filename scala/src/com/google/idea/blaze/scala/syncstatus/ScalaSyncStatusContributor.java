package com.google.idea.blaze.scala.syncstatus;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.syncstatus.SyncStatusContributor;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

import javax.annotation.Nullable;

public class ScalaSyncStatusContributor implements SyncStatusContributor {

    @Nullable
    @Override
    public PsiFileAndName toPsiFileAndName(BlazeProjectData projectData, ProjectViewNode<?> node) {
        if (!(node instanceof ClassTreeNode)) {
            return null;
        }
        PsiClass psiClass = ((ClassTreeNode) node).getPsiClass();
        if (psiClass == null) {
            return null;
        }
        PsiFile file = psiClass.getContainingFile();
        return file != null ? new PsiFileAndName(file, psiClass.getName()) : null;
    }

    @Override
    public boolean handlesFile(BlazeProjectData projectData, VirtualFile file) {
        return projectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.SCALA)
                && (file.getName().endsWith(".scala") || file.getName().endsWith(".sc"));
    }
}

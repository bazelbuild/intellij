package com.google.idea.blaze.base.ui;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.intellij.ide.util.ModuleRendererFactory;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.TextWithIcon;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class BlazeModuleRendererFactory extends ModuleRendererFactory {
    @Override
    protected boolean handles(Object element) {
        if (element instanceof PsiElement psiElement && psiElement.isValid()) {
            var project = psiElement.getProject();
            if (Blaze.getProjectType(project) == BlazeImportSettings.ProjectType.UNKNOWN) {
                return false;
            }
            var module = ModuleUtilCore.findModuleForPsiElement(psiElement);

            return module != null && module.getName().equals(".workspace");
        }
        return false;
    }

    @Override
    public @Nullable TextWithIcon getModuleTextWithIcon(Object element) {
        if (element instanceof PsiElement psiElement && psiElement.isValid()) {
            var project = psiElement.getProject();
            var file = PsiUtilCore.getVirtualFile(psiElement);
            var module = Optional.ofNullable(ModuleUtilCore.findModuleForPsiElement(psiElement));

            ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
            if (file == null) {
                return null;
            }

            var sourceRoot = index.getSourceRootForFile(file);
            if (sourceRoot == null) {
                return null;
            }

            var relativeSourceRoot = WorkspaceRoot.fromProject(project).relativize(sourceRoot);
            if (relativeSourceRoot == null) {
                return null;
            }
            return new TextWithIcon(
                    relativeSourceRoot.toString(),
                    module.map(m -> ModuleType.get(m).getIcon()).orElse(null));
        }
        return null;
    }
}

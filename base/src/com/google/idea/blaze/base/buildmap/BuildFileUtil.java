package com.google.idea.blaze.base.buildmap;

import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;

import javax.annotation.Nullable;
import java.io.File;

public class BuildFileUtil {
  private BuildFileUtil() {
  }

  @Nullable
  public static BlazePackage getBuildFile(Project project, @Nullable VirtualFile vf) {
    if (vf == null) {
      return null;
    }
    PsiManager manager = PsiManager.getInstance(project);
    PsiFileSystemItem psiFile = vf.isDirectory() ? manager.findDirectory(vf) : manager.findFile(vf);
    if (psiFile == null) {
      return null;
    }
    return BlazePackage.getContainingPackage(psiFile);
  }

  @Nullable
  public static PsiElement findBuildTarget(
      Project project, BlazePackage parentPackage, File file) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    File parentFile = parentPackage.buildFile.getFile().getParentFile();
    WorkspacePath packagePath =
        parentFile != null
            ? blazeProjectData.workspacePathResolver.getWorkspacePath(parentFile)
            : null;
    if (packagePath == null) {
      return null;
    }
    Label label =
        SourceToTargetMap.getInstance(project)
            .getTargetsToBuildForSourceFile(file)
            .stream()
            .filter(l -> l.blazePackage().equals(packagePath))
            .findFirst()
            .orElse(null);
    if (label == null) {
      return null;
    }
    return BuildReferenceManager.getInstance(project).resolveLabel(label);
  }
}

/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.golang.resolve;

import com.goide.psi.GoImportSpec;
import com.goide.psi.impl.imports.GoImportResolver;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.golang.BlazeGoSupport;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import java.io.File;
import javax.annotation.Nullable;

/**
 * Resolves go imports in a blaze workspace, of the form:
 *
 * <p>"[workspace_name]/path/to/blaze/package/[go_library target]"
 *
 * <p>Only the first non-null import candidate is considered, so all blaze-specific import handling
 * is done in this {@link GoImportResolver}, to more easily manage priority.
 */
public class BlazeGoImportResolver implements GoImportResolver {

  @Nullable
  @Override
  public PsiDirectory resolve(GoImportSpec goImportSpec) {
    Project project = goImportSpec.getProject();
    if (!Blaze.isBlazeProject(project) || !BlazeGoSupport.blazeGoSupportEnabled.getValue()) {
      return null;
    }
    // TODO: Handle go packages whose sources are in multiple directories (requires upstream change)
    String pathString = goImportSpec.getPath();
    String packageName = getPackageName(pathString);

    GoTarget target = findGoTarget(project, pathString, packageName);
    if (target == null) {
      return null;
    }
    switch (target.kind) {
      case GO_LIBRARY:
      case GO_APPENGINE_LIBRARY:
        return resolveFile(PsiManager.getInstance(project), target.buildFile.getParentFile());
      case PROTO_LIBRARY:
      case GO_WRAP_CC:
        return resolveGenfilesPath(project, target.label.blazePackage().relativePath());
      default:
        return null;
    }
  }

  private static String getPackageName(String pathString) {
    int ix = pathString.lastIndexOf('/');
    return ix == -1 ? pathString : pathString.substring(ix + 1);
  }

  @Nullable
  private static GoTarget findGoTarget(Project project, String importPath, String packageName) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    TargetName targetName = TargetName.createIfValid(packageName);
    if (targetName == null) {
      return null;
    }
    WorkspacePath workspacePath = blazePackageWorkspacePath(project, importPath);
    if (workspacePath == null) {
      return null;
    }
    Label label = Label.create(workspacePath, targetName);
    GoTarget goTarget = GoTarget.fromTargetIdeInfo(projectData, label);
    if (goTarget != null) {
      return goTarget;
    }
    // if the target wasn't indexed, try parsing the BUILD file manually
    return GoTarget.manuallyParseBuildFile(project, label);
  }

  @Nullable
  private static PsiDirectory resolveGenfilesPath(Project project, String relativePath) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    File genfiles = projectData.blazeInfo.getGenfilesDirectory();
    return resolveFile(PsiManager.getInstance(project), new File(genfiles, relativePath));
  }

  @Nullable
  private static PsiDirectory resolveFile(PsiManager manager, File file) {
    VirtualFile vf =
        VirtualFileSystemProvider.getInstance().getSystem().findFileByPath(file.getPath());
    return vf != null && vf.isDirectory() ? manager.findDirectory(vf) : null;
  }

  @Nullable
  private static WorkspacePath blazePackageWorkspacePath(Project project, String importPath) {
    String workspaceName = WorkspaceRoot.fromProject(project).directory().getName();
    if (!importPath.startsWith(workspaceName + "/")) {
      return null;
    }
    // strip first and last path components (workspace name, go package name)
    importPath = importPath.substring(workspaceName.length() + 1);
    int lastSeparator = importPath.lastIndexOf('/');
    if (lastSeparator <= 0) {
      return null;
    }
    return WorkspacePath.createIfValid(importPath.substring(0, lastSeparator));
  }

  private static class GoTarget {
    final File buildFile;
    final Kind kind;
    final Label label;

    GoTarget(File buildFile, Kind kind, Label label) {
      this.buildFile = buildFile;
      this.kind = kind;
      this.label = label;
    }

    @Nullable
    static GoTarget fromTargetIdeInfo(BlazeProjectData projectData, Label label) {
      TargetIdeInfo target = projectData.targetMap.get(TargetKey.forPlainTarget(label));
      if (target == null) {
        return null;
      }
      File buildFile = projectData.artifactLocationDecoder.decode(target.buildFile);
      return new GoTarget(buildFile, target.kind, target.key.label);
    }

    @Nullable
    static GoTarget manuallyParseBuildFile(Project project, Label label) {
      PsiElement psiElement = BuildReferenceManager.getInstance(project).resolveLabel(label);
      if (!(psiElement instanceof FuncallExpression)) {
        return null;
      }
      FuncallExpression funcall = (FuncallExpression) psiElement;
      Kind kind = funcall.getRuleKind();
      BuildFile parentFile = funcall.getContainingFile();
      return kind == null || parentFile == null
          ? null
          : new GoTarget(parentFile.getFile(), kind, label);
    }
  }
}

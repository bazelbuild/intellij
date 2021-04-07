/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.producers;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.io.File;
import javax.annotation.Nullable;

/** Runs all tests in a single selected blaze package directory. */
class AllInPackageTestContextProvider implements TestContextProvider {

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    WorkspaceRoot root = WorkspaceRoot.fromProject(context.getProject());
    PsiElement location = context.getPsiLocation();

    // A BUILD file is selected.
    if (location instanceof PsiFile) {
      PsiFile file = (PsiFile) location;
      if (!isBuildFile(context, file) || file.getParent() == null) {
        return null;
      }
      return fromDirectoryNonRecursive(root, file.getParent());
    }

    // A Blaze package is selected.
    // TODO(giathuan): Figure out how to do this for non-Blaze package without interfering with Java
    //  rules (e.g. MultipleJavaClassesTestContextProvider).
    if ((location instanceof PsiDirectory)) {
      PsiDirectory dir = (PsiDirectory) location;
      // if (!BlazePackage.isBlazePackage(dir)) {
      //   return null;
      // }
      return fromDirectoryRecursive(root, dir);
    }

    return null;
  }

  private static boolean isBuildFile(ConfigurationContext context, PsiFile file) {
    return Blaze.getBuildSystemProvider(context.getProject()).isBuildFile(file.getName());
  }

  @Nullable
  private static RunConfigurationContext fromDirectoryRecursive(
      WorkspaceRoot root, PsiDirectory dir) {
    WorkspacePath packagePath = getWorkspaceRelativePath(root, dir.getVirtualFile());
    if (packagePath == null) {
      return null;
    }
    return RunConfigurationContext.fromKnownTarget(
        TargetExpression.allFromPackageRecursive(packagePath), BlazeCommandName.TEST, dir);
  }

  @Nullable
  private static RunConfigurationContext fromDirectoryNonRecursive(
      WorkspaceRoot root, PsiDirectory dir) {
    WorkspacePath packagePath = getWorkspaceRelativePath(root, dir.getVirtualFile());
    if (packagePath == null) {
      return null;
    }
    return RunConfigurationContext.fromKnownTarget(
        TargetExpression.allFromPackageNonRecursive(packagePath), BlazeCommandName.TEST, dir);
  }

  @Nullable
  private static WorkspacePath getWorkspaceRelativePath(WorkspaceRoot root, VirtualFile vf) {
    return root.workspacePathForSafe(new File(vf.getPath()));
  }
}

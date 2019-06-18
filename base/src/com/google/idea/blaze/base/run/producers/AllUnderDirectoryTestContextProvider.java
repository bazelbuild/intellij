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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.ExecutorType;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import java.io.File;
import javax.annotation.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * Runs all tests underneath the selected directory. Ignores directories without child blaze
 * packages.
 */
class AllUnderDirectoryTestContextProvider implements TestContextProvider {

  private static final ListeningExecutorService EXECUTOR =
      ApplicationManager.getApplication().isUnitTestMode()
          ? MoreExecutors.newDirectExecutorService()
          : MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);

  private static final int MAX_DEPTH_TO_SEARCH = 8;

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    PsiElement location = context.getPsiLocation();
    if (!(location instanceof PsiDirectory)) {
      return null;
    }
    WorkspaceRoot root = WorkspaceRoot.fromProject(context.getProject());
    PsiDirectory dir = (PsiDirectory) location;
    if (!isInWorkspace(root, dir)) {
      return null;
    }
    // quick check if the directory itself is a blaze package, otherwise check the subdirectories
    // recursively, asynchronously
    if (BlazePackage.isBlazePackage(dir)) {
      return fromDirectory(root, dir);
    }
    ListenableFuture<RunConfigurationContext> future =
        EXECUTOR.submit(
            () ->
                ReadAction.compute(
                    () ->
                        BlazePackage.hasBlazePackageChild(
                                dir, d -> isInWorkspace(root, d), MAX_DEPTH_TO_SEARCH)
                            ? fromDirectory(root, dir)
                            : null));
    return TestContext.builder(dir, ExecutorType.DEBUG_SUPPORTED_TYPES)
        .setContextFuture(future)
        .setDescription(String.format("all under directory '%s'", dir.getName()))
        .build();
  }

  @Nullable
  private static RunConfigurationContext fromDirectory(WorkspaceRoot root, PsiDirectory dir) {
    WorkspacePath packagePath = getWorkspaceRelativePath(root, dir.getVirtualFile());
    if (packagePath == null) {
      return null;
    }
    return RunConfigurationContext.fromKnownTarget(
        TargetExpression.allFromPackageRecursive(packagePath), BlazeCommandName.TEST, dir);
  }

  @Nullable
  private static WorkspacePath getWorkspaceRelativePath(WorkspaceRoot root, VirtualFile vf) {
    return root.workspacePathForSafe(new File(vf.getPath()));
  }

  private static boolean isInWorkspace(WorkspaceRoot root, PsiDirectory dir) {
    return root.isInWorkspace(dir.getVirtualFile());
  }
}

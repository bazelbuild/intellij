/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.workspace;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

/** Uses the package path locations to resolve a workspace path. */
public class WorkspacePathResolverImpl implements WorkspacePathResolver {
  private static final long serialVersionUID = 2L;

  private final WorkspaceRoot workspaceRoot;
  private final List<File> packagePaths;

  public WorkspacePathResolverImpl(WorkspaceRoot workspaceRoot, BlazeRoots blazeRoots) {
    this(workspaceRoot, blazeRoots.packagePaths);
  }

  public WorkspacePathResolverImpl(WorkspaceRoot workspaceRoot) {
    this(workspaceRoot, ImmutableList.of(workspaceRoot.directory()));
  }

  public WorkspacePathResolverImpl(WorkspaceRoot workspaceRoot, List<File> packagePaths) {
    this.workspaceRoot = workspaceRoot;
    this.packagePaths = packagePaths;
  }

  @Override
  public ImmutableList<File> resolveToIncludeDirectories(ExecutionRootPath executionRootPath) {
    File trackedLocation = executionRootPath.getFileRootedAt(workspaceRoot.directory());
    return ImmutableList.of(trackedLocation);
  }

  @Override
  @Nullable
  public File findPackageRoot(String relativePath) {
    if (packagePaths.size() == 1) {
      return packagePaths.get(0);
    }
    // fall back to manually checking each one
    FileAttributeProvider existenceChecker = FileAttributeProvider.getInstance();
    for (File pkg : packagePaths) {
      if (existenceChecker.exists(new File(pkg, relativePath))) {
        return pkg;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public WorkspacePath getWorkspacePath(File absoluteFile) {
    return workspaceRoot.workspacePathForSafe(absoluteFile);
  }
}

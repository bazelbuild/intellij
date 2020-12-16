/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.vcs;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryCreator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import javax.annotation.Nullable;

/**
 * Compatibility adapter for {@link VcsRepositoryCreator}. Changed to an interface in 2020.2.
 * #api201
 */
public abstract class VcsRepositoryCreatorAdapter extends VcsRepositoryCreator {
  private final Project myProject;

  public VcsRepositoryCreatorAdapter(Project myProject) {
    this.myProject = myProject;
  }

  // #api201: No-arg constructor necessary to avoid an exception from 2020.2 on.
  public VcsRepositoryCreatorAdapter() {
    throw new UnsupportedOperationException(
        "This version of IntelliJ should use the constructor with the project parameter.");
  }

  @Nullable
  public abstract Repository createRepository(
      Project project, VirtualFile root, Disposable parentDisposable);

  @Nullable
  @Override
  // #api201: Project parameter added in 2020.2
  public Repository createRepositoryIfValid(VirtualFile root, Disposable parentDisposable) {
    return createRepository(myProject, root, parentDisposable);
  }
}

/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.sdkcompat.fig.repo;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsKey;

/** Compat class for AbstractRepositoryManager constructor changes. */
public abstract class AbstractRepositoryManagerCompat<T extends Repository>
    extends AbstractRepositoryManager<T> {
  protected AbstractRepositoryManagerCompat(
      AbstractVcs abstractVcs, Project project, VcsKey vcsKey, String repoDirName) {
    super(project, vcsKey, repoDirName);
  }
}

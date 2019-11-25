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
package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.impl.FileStatusProvider;
import com.intellij.openapi.vcs.impl.VcsBaseContentProvider;
import com.intellij.openapi.vcs.impl.VcsFileStatusProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import javax.annotation.Nullable;

/** #api192: From 2019.3, constructor taking only Project is used */
public class VcsFileStatusProviderAdapter implements FileStatusProvider, VcsBaseContentProvider {
  private final VcsFileStatusProvider vcsFileStatusProvider;

  public VcsFileStatusProviderAdapter(Project project) {
    vcsFileStatusProvider = VcsFileStatusProvider.getInstance(project);
  }

  @Override
  public FileStatus getFileStatus(VirtualFile virtualFile) {
    return vcsFileStatusProvider.getFileStatus(virtualFile);
  }

  @Override
  public void refreshFileStatusFromDocument(VirtualFile virtualFile, Document document) {
    vcsFileStatusProvider.refreshFileStatusFromDocument(virtualFile, document);
  }

  @Override
  public ThreeState getNotChangedDirectoryParentingStatus(VirtualFile virtualFile) {
    return vcsFileStatusProvider.getNotChangedDirectoryParentingStatus(virtualFile);
  }

  @Nullable
  @Override
  public BaseContent getBaseRevision(VirtualFile virtualFile) {
    return vcsFileStatusProvider.getBaseRevision(virtualFile);
  }

  @Override
  public boolean isSupported(VirtualFile virtualFile) {
    return vcsFileStatusProvider.isSupported(virtualFile);
  }
}

/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp;

import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.CustomHeaderProvider;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveRootAndConfiguration;
import java.io.File;
import org.jetbrains.annotations.Nullable;

/**
 * Provides a quick path to resolving non-generated includes, without file-system operations.
 *
 * <p>In typical projects, only a tiny fraction of includes are generated files (~0.1%), so handling
 * non-generated files efficiently is very low hanging fruit.
 *
 * <p>Ideally our aspect would record which generated files are used, and we could avoid FS
 * operations entirely.
 */
public class BlazeCustomHeaderProvider extends CustomHeaderProvider {

  @Override
  public boolean accepts(@Nullable OCResolveRootAndConfiguration configuration) {
    return configuration != null
        && configuration.getConfiguration() instanceof BlazeResolveConfiguration;
  }

  @Nullable
  @Override
  public VirtualFile getCustomHeaderFile(
      String includeString,
      HeaderSearchStage stage,
      @Nullable OCResolveConfiguration configuration) {
    if (stage != HeaderSearchStage.BEFORE_START
        || includeString.startsWith("/")
        || configuration == null) {
      return null;
    }
    File file =
        ((BlazeResolveConfiguration) configuration)
            .getWorkspacePathResolver()
            .resolveToFile(includeString);
    return VirtualFileSystemProvider.getInstance().getSystem().findFileByIoFile(file);
  }

  @Nullable
  @Override
  public String provideSerializationPath(VirtualFile file) {
    return null;
  }

  @Nullable
  @Override
  public VirtualFile getCustomSerializedHeaderFile(
      String serializationPath, Project project, VirtualFile currentFile) {
    return null;
  }
}

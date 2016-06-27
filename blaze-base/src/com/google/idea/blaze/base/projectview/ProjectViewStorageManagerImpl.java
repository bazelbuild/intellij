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
package com.google.idea.blaze.base.projectview;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Project view storage implementation.
 */
final class ProjectViewStorageManagerImpl extends ProjectViewStorageManager {
  private static final Logger LOG = Logger.getInstance(ProjectViewManagerImpl.class);

  @Nullable
  @Override
  public String loadProjectView(@NotNull File projectViewFile) throws IOException {
    FileInputStream fis = new FileInputStream(projectViewFile);
    byte[] data = new byte[(int)projectViewFile.length()];
    fis.read(data);
    fis.close();
    return new String(data, Charsets.UTF_8);
  }

  @Override
  public void writeProjectView(
    @NotNull String projectViewText,
    @NotNull File projectViewFile) throws IOException {
    FileWriter fileWriter = new FileWriter(projectViewFile);
    try {
      fileWriter.write(projectViewText);
    } finally {
      fileWriter.close();
    }

    LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(projectViewFile));
  }
}

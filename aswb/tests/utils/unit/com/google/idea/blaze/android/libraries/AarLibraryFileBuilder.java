/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.libraries;

import static com.android.SdkConstants.FN_LINT_JAR;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.NonNull;
import com.google.common.base.Joiner;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Util class to generate temp aar file used during test */
public class AarLibraryFileBuilder {
  private static final Logger LOG = Logger.getInstance(AarLibraryFileBuilder.class);
  private final File aarFile;
  private final Map<String, byte[]> resourceNameToContent;
  private byte[] lintJar = new byte[0];

  public static AarLibraryFileBuilder aar(
      @NonNull WorkspaceRoot workspaceRoot, @NonNull String aarFilePath) {
    return new AarLibraryFileBuilder(workspaceRoot, aarFilePath);
  }

  AarLibraryFileBuilder(@NonNull WorkspaceRoot workspaceRoot, @NonNull String aarFilePath) {
    aarFile = workspaceRoot.fileForPath(new WorkspacePath(aarFilePath));
    this.resourceNameToContent = new HashMap<>();
  }

  public AarLibraryFileBuilder src(String relativePath, List<String> contentLines) {
    resourceNameToContent.put(relativePath, Joiner.on("\n").join(contentLines).getBytes(UTF_8));
    return this;
  }

  public AarLibraryFileBuilder src(String relativePath, byte[] content) {
    resourceNameToContent.put(relativePath, content);
    return this;
  }

  public AarLibraryFileBuilder setLintJar(byte[] lintJar) {
    this.lintJar = lintJar;
    return this;
  }

  public File build() {
    try {
      // create aarFilePath if it does not exist
      aarFile.getParentFile().mkdirs();
      aarFile.createNewFile();
      try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(aarFile))) {
        for (Map.Entry<String, byte[]> entry : resourceNameToContent.entrySet()) {
          out.putNextEntry(new ZipEntry(entry.getKey()));
          out.write(entry.getValue(), 0, entry.getValue().length);
          out.closeEntry();
        }
        if (lintJar.length > 0) {
          out.putNextEntry(new ZipEntry(FN_LINT_JAR));
          out.write(lintJar, 0, lintJar.length);
          out.closeEntry();
        }
      }
    } catch (Exception e) {
      LOG.error(e);
    }
    return aarFile;
  }
}

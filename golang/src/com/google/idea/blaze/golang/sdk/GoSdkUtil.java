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
package com.google.idea.blaze.golang.sdk;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * Go-lang SDK utility methods.
 *
 * <p>TODO: Remove this, and reference go-lang plugin source code directly.
 */
public class GoSdkUtil {

  @Nullable
  public static VirtualFile suggestSdkDirectory() {
    String fromEnv = suggestSdkDirectoryPathFromEnv();
    if (fromEnv != null) {
      return LocalFileSystem.getInstance().findFileByPath(fromEnv);
    }
    return LocalFileSystem.getInstance().findFileByPath("/usr/local/go");
  }

  @Nullable
  private static String suggestSdkDirectoryPathFromEnv() {
    File fileFromPath = PathEnvironmentVariableUtil.findInPath("go");
    if (fileFromPath != null) {
      File canonicalFile;
      try {
        canonicalFile = fileFromPath.getCanonicalFile();
        String path = canonicalFile.getPath();
        if (path.endsWith("bin/go")) {
          return StringUtil.trimEnd(path, "bin/go");
        }
      } catch (IOException e) {
        // if it can't be found, just silently return null
      }
    }
    return null;
  }
}

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
package com.google.idea.common.binaryhelper;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.URLUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/** Binaries provided to IntelliJ at runtime */
public final class HelperBinaryUtil {

  private static final Logger logger = Logger.getInstance(HelperBinaryUtil.class);

  private static File tempDirectory;
  private static final Map<String, File> cachedFiles = new HashMap<>();

  @Nullable
  public static synchronized File getHelperBinary(String binaryFilePath) {
    // Assume the binaries have unique names. This saves having
    // to recursively clean up directories
    String binaryName = new File(binaryFilePath).getName();

    File file = cachedFiles.get(binaryName);
    if (file != null) {
      return file;
    }
    if (tempDirectory == null) {
      tempDirectory = com.google.common.io.Files.createTempDir();
      tempDirectory.deleteOnExit();
    }
    file = new File(tempDirectory, binaryName);

    URL url = HelperBinaryUtil.class.getResource(binaryFilePath);
    if (url == null) {
      logger.error(String.format("Helper binary '%s' was not found", binaryFilePath));
      return null;
    }
    try (InputStream inputStream = URLUtil.openResourceStream(url)) {
      Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      file.setExecutable(true);
      file.deleteOnExit();
      cachedFiles.put(binaryName, file);
      return file;
    } catch (IOException e) {
      logger.error(String.format("Error loading helper binary '%s'", binaryFilePath));
      return null;
    }
  }
}

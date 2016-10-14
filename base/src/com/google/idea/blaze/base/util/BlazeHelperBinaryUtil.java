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
package com.google.idea.blaze.base.util;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Extracts binaries from the resource section of the jar for execution */
public final class BlazeHelperBinaryUtil {

  private static final Logger LOG = Logger.getInstance(BlazeHelperBinaryUtil.class);

  private static final File tempDirectory = com.google.common.io.Files.createTempDir();
  private static final Map<String, File> cachedFiles = new HashMap<>();

  @Nullable
  public static synchronized File getBlazeHelperBinary(@NotNull String binaryName) {
    File file = cachedFiles.get(binaryName);
    if (file != null) {
      return file;
    }
    file = new File(tempDirectory, binaryName);
    File directory = file.getParentFile();

    if (!directory.mkdirs()) {
      LOG.error("Could not create temporary dir: " + directory);
      return null;
    }

    URL url = BlazeHelperBinaryUtil.class.getResource(binaryName);
    if (url == null) {
      LOG.error(String.format("Blaze binary '%s' was not found", binaryName));
      return null;
    }
    try (InputStream inputStream = URLUtil.openResourceStream(url)) {
      Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      file.setExecutable(true);
      file.deleteOnExit();
      cachedFiles.put(binaryName, file);
      return file;
    } catch (IOException e) {
      LOG.error(String.format("Error loading blaze binary '%s'", binaryName));
      return null;
    }
  }
}

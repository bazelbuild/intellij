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
package com.google.idea.blaze.base.buildmodifier;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.intellij.openapi.diagnostic.Logger;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

/** Formats BUILD files using 'buildifier' */
public class BuildFileFormatter {

  private static final Logger logger = Logger.getInstance(BuildFileFormatter.class);
  // 10 seconds ought to be enough for formatting a BUILD file...
  private static final long TIMEOUT_SECS = 10;

  @Nullable
  private static File getBuildifierBinary() {
    for (BuildifierBinaryProvider provider : BuildifierBinaryProvider.EP_NAME.getExtensions()) {
      File file = provider.getBuildifierBinary();
      if (file != null) {
        return file;
      }
    }
    return null;
  }

  /**
   * Format the BUILD file with a timeout. The tool may be fetched from a network filesystem, and we
   * don't want to block the UI if there is a network issue.
   *
   * @return formatted text, or null if there is an error or timeout
   */
  @Nullable
  static String formatTextWithTimeout(String text) {
    BlazeExecutor executor = BlazeExecutor.getInstance();
    ListenableFuture<String> result = executor.submit(() -> formatText(text));
    try {
      return result.get(TIMEOUT_SECS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (ExecutionException | TimeoutException e) {
      logger.warn(e);
      return null;
    }
  }

  @Nullable
  private static String formatText(String text) {
    try {
      File buildifierBinary = getBuildifierBinary();
      if (buildifierBinary == null) {
        return null;
      }
      File file = createTempFile(text);
      formatFile(file, buildifierBinary.getPath());
      String formattedFile = readFile(file);
      file.delete();
      return formattedFile;
    } catch (IOException e) {
      logger.warn(e);
    }
    return null;
  }

  private static void formatFile(File file, String buildifierBinaryPath) throws IOException {
    ProcessBuilder builder = new ProcessBuilder(buildifierBinaryPath, file.getAbsolutePath());
    try {
      builder.start().waitFor();
    } catch (InterruptedException e) {
      throw new IOException("buildifier execution failed", e);
    }
  }

  private static String readFile(File file) throws IOException {
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      StringBuilder formattedFile = new StringBuilder();
      char[] buf = new char[1024];
      int numRead;
      while ((numRead = reader.read(buf)) >= 0) {
        formattedFile.append(buf, 0, numRead);
      }
      return formattedFile.toString();
    }
  }

  private static File createTempFile(String text) throws IOException {
    File file = File.createTempFile("ijwb", ".tmp");
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
      writer.write(text);
    }
    return file;
  }
}

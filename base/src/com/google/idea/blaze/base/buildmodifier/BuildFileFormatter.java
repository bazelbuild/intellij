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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.google.idea.common.formatter.ExternalFormatterCodeStyleManager.Replacements;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import javax.annotation.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/** Formats BUILD files using 'buildifier' */
public class BuildFileFormatter {

  private static final Logger logger = Logger.getInstance(BuildFileFormatter.class);

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
   * Calls buildifier for a given text and list of line ranges, and returns the formatted text, or
   * null if the formatting failed.
   *
   * <p>buildifier can be very slow, so this runs with a progress dialog, giving the user some
   * indication that their IDE hasn't died.
   */
  static ListenableFuture<Replacements> formatTextWithProgressDialog(
      Project project, BlazeFileType fileType, String text, Collection<TextRange> ranges) {
    ListenableFuture<Replacements> future =
        MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE)
            .submit(() -> getReplacements(fileType, text, ranges));
    ProgressWindow progressWindow =
        new BackgroundableProcessIndicator(
            project,
            "Running buildifier",
            PerformInBackgroundOption.DEAF,
            "Cancel",
            "Cancel",
            true);
    progressWindow.setIndeterminate(true);
    progressWindow.start();
    progressWindow.addStateDelegate(
        new AbstractProgressIndicatorExBase() {
          @Override
          public void cancel() {
            super.cancel();
            future.cancel(true);
          }
        });
    future.addListener(
        () ->
            ApplicationManager.getApplication()
                .invokeLater(
                    () -> {
                      if (progressWindow.isRunning()) {
                        progressWindow.stop();
                        progressWindow.processFinish();
                      }
                    }),
        MoreExecutors.directExecutor());
    return future;
  }

  /**
   * Calls buildifier for a given text and list of line ranges, and returns the formatted text, or
   * null if the formatting failed.
   */
  @Nullable
  private static Replacements getReplacements(
      BlazeFileType fileType, String text, Collection<TextRange> ranges) {
    File buildifierBinary = getBuildifierBinary();
    if (buildifierBinary == null) {
      return null;
    }
    Replacements output = new Replacements();
    try {
      for (TextRange range : ranges) {
        String input = range.substring(text);
        String result = formatText(buildifierBinary, fileType, input);
        if (result == null) {
          return null;
        }
        output.addReplacement(range, input, result);
      }
      return output;

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (IOException e) {
      logger.warn(e);
    }
    return null;
  }

  /**
   * Passes the input text to buildifier, returning the formatted output text, or null if formatting
   * failed.
   */
  @Nullable
  private static String formatText(File buildifierBinary, BlazeFileType fileType, String inputText)
      throws InterruptedException, IOException {
    Process process = new ProcessBuilder(buildifierBinary.getPath(), fileTypeArg(fileType)).start();
    process.getOutputStream().write(inputText.getBytes(UTF_8));
    process.getOutputStream().close();

    BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8));
    String formattedText = CharStreams.toString(reader);
    process.waitFor();
    return process.exitValue() != 0 ? null : formattedText;
  }

  private static String fileTypeArg(BlazeFileType fileType) {
    return fileType == BlazeFileType.SkylarkExtension ? "--type=bzl" : "--type=build";
  }
}

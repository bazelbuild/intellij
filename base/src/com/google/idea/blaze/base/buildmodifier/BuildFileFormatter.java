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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.formatter.FormatUtils.FileContentsProvider;
import com.google.idea.blaze.base.formatter.FormatUtils.Replacements;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import javax.annotation.Nullable;

/** Formats BUILD files using 'buildifier' */
public class BuildFileFormatter {

  private static final Logger logger = Logger.getInstance(BuildFileFormatter.class);

  @Nullable
  private static String getBuildifierBinaryPath() {
    for (BuildifierBinaryProvider provider : BuildifierBinaryProvider.EP_NAME.getExtensions()) {
      String path = provider.getBuildifierBinaryPath();
      if (!Strings.isNullOrEmpty(path)) {
        return path;
      }
    }
    return null;
  }

  /**
   * Calls buildifier for a given text and list of line ranges, and returns the formatted text, or
   * null if the formatting failed.
   */
  @Nullable
  static Replacements getReplacements(
      Project project,
      BlazeFileType fileType,
      FileContentsProvider fileContents,
      Collection<TextRange> ranges) {
    String buildifierBinaryPath = getBuildifierBinaryPath();
    if (buildifierBinaryPath == null) {
      return null;
    }
    String text = fileContents.getFileContentsIfUnchanged();
    if (text == null) {
      return null;
    }
    Replacements output = new Replacements();
      for (TextRange range : ranges) {
        String input = range.substring(text);
      String result = formatText(buildifierBinaryPath, fileType, input, project);
        if (result == null) {
          return null;
        }
        output.addReplacement(range, input, result);
      }
    return output;
  }

  /**
   * Passes the input text to buildifier, returning the formatted output text, or null if formatting
   * failed.
   */
  @Nullable
  private static String formatText(
      String buildifierBinaryPath, BlazeFileType fileType, String inputText, Project project) {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    var exitValue =
        ExternalTask.builder(ImmutableList.of(buildifierBinaryPath, fileTypeArg(fileType)), project)
            .input(inputText.getBytes(UTF_8))
            .stdout(stdout)
            .build()
            .run();
    String formattedText = stdout.toString(UTF_8);
    return exitValue != 0 ? null : formattedText;
  }

  private static String fileTypeArg(BlazeFileType fileType) {
    return fileType == BlazeFileType.SkylarkExtension ? "--type=bzl" : "--type=build";
  }
}

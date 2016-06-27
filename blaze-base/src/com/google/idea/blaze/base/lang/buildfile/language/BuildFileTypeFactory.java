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
package com.google.idea.blaze.base.lang.buildfile.language;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for BuildFileType
 */
public class BuildFileTypeFactory extends FileTypeFactory {

  private static ImmutableList<FileNameMatcher> DEFAULT_ASSOCIATIONS = ImmutableList.of(
    new ExactFileNameMatcher("BUILD"),
    new ExtensionFileNameMatcher("bzl")
  );

  @Override
  public void createFileTypes(@NotNull final FileTypeConsumer consumer) {
    consumer.consume(BuildFileType.INSTANCE, DEFAULT_ASSOCIATIONS.toArray(new FileNameMatcher[0]));
  }

  private static volatile boolean enabled = true;

  public static void updateBuildFileLanguageEnabled(boolean supportEnabled) {
    if (enabled == supportEnabled) {
      return;
    }
    enabled = supportEnabled;
    if (!supportEnabled) {
      FileTypeManagerEx.getInstanceEx().unregisterFileType(BuildFileType.INSTANCE);
    } else {
      FileTypeManager.getInstance().registerFileType(BuildFileType.INSTANCE, DEFAULT_ASSOCIATIONS);
    }
  }

}
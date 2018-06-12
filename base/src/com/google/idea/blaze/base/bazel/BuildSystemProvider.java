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
package com.google.idea.blaze.base.bazel;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.ExactFileNameMatcher;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import javax.annotation.Nullable;

/**
 * Extension points specify the build systems supported by this plugin.<br>
 * The order of the extension points establishes a priority (highest priority first), for situations
 * where we don't have an existing project to use for context (e.g. the 'import project' action).
 */
public interface BuildSystemProvider {

  ExtensionPointName<BuildSystemProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BuildSystemProvider");

  static BuildSystemProvider defaultBuildSystem() {
    return EP_NAME.getExtensions()[0];
  }

  @Nullable
  static BuildSystemProvider getBuildSystemProvider(BuildSystem buildSystem) {
    for (BuildSystemProvider provider : EP_NAME.getExtensions()) {
      if (provider.buildSystem() == buildSystem) {
        return provider;
      }
    }
    return null;
  }

  static boolean isBuildSystemAvailable(BuildSystem buildSystem) {
    return getBuildSystemProvider(buildSystem) != null;
  }

  static WorkspaceRootProvider getWorkspaceRootProvider(BuildSystem buildSystem) {
    BuildSystemProvider provider = getBuildSystemProvider(buildSystem);
    if (provider == null) {
      throw new RuntimeException(
          String.format("Build system '%s' not supported by this plugin", buildSystem));
    }
    return provider.getWorkspaceRootProvider();
  }

  /**
   * Returns the default build system for this application. This should only be called in situations
   * where it doesn't make sense to use the current project.<br>
   * Otherwise, use {@link com.google.idea.blaze.base.settings.Blaze#getBuildSystem}
   */
  BuildSystem buildSystem();

  /** @return The location of the blaze/bazel binary. */
  String getBinaryPath();

  /** @return The location of the blaze/bazel binary to use for syncing. */
  default String getSyncBinaryPath() {
    return getBinaryPath();
  }

  WorkspaceRootProvider getWorkspaceRootProvider();

  /** Directories containing artifacts produced during the build process. */
  ImmutableList<String> buildArtifactDirectories(WorkspaceRoot root);

  /** The URL providing the built-in BUILD rule's documentation, if one can be found. */
  @Nullable
  String getRuleDocumentationUrl(RuleDefinition rule);

  /** The URL providing documentation for project view files, if one can be found. */
  @Nullable
  String getProjectViewDocumentationUrl();

  /**
   * The URL providing documentation for language support, if one can be found.
   *
   * @param relativeDocName the path to the language doc, relative to the plugin documentation
   *     site's base URL, without the webpage's file extension.
   */
  @Nullable
  String getLanguageSupportDocumentationUrl(String relativeDocName);

  /** The BUILD filenames supported by this build system. */
  ImmutableSet<String> possibleBuildFileNames();

  /** Check if the given filename is a valid BUILD file name. */
  default boolean isBuildFile(String fileName) {
    return possibleBuildFileNames().contains(fileName);
  }

  /**
   * Check if the given directory has a child with a valid BUILD file name, and if so, returns the
   * first such file.
   */
  @Nullable
  default File findBuildFileInDirectory(File directory) {
    FileOperationProvider provider = FileOperationProvider.getInstance();
    for (String filename : possibleBuildFileNames()) {
      File child = new File(directory, filename);
      if (provider.exists(child)) {
        return child;
      }
    }
    return null;
  }

  /**
   * Check if the given directory has a child with a valid BUILD file name, and if so, returns the
   * first such file.
   */
  @Nullable
  default VirtualFile findBuildFileInDirectory(VirtualFile directory) {
    for (String filename : possibleBuildFileNames()) {
      VirtualFile child = directory.findChild(filename);
      if (child != null) {
        return child;
      }
    }
    return null;
  }

  /** Returns the list of file types recognized as build system files. */
  default ImmutableList<FileNameMatcher> buildLanguageFileTypeMatchers() {
    ImmutableList.Builder<FileNameMatcher> list = ImmutableList.builder();
    possibleBuildFileNames().forEach(s -> list.add(new ExactFileNameMatcher(s)));
    list.add(new ExtensionFileNameMatcher("bzl"), new ExactFileNameMatcher("WORKSPACE"));
    return list.build();
  }

  /** Populates the passed builder with version data. */
  void populateBlazeVersionData(
      BuildSystem buildSystem,
      WorkspaceRoot workspaceRoot,
      BlazeInfo blazeInfo,
      BlazeVersionData.Builder builder);
}

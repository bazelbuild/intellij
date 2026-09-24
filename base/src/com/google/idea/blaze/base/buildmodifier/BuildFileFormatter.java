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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BazelWorkspaceRootProvider;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import javax.annotation.Nullable;

/** Builds the 'buildifier' command line for a BUILD file. */
public class BuildFileFormatter {

  private BuildFileFormatter() {}

  static ImmutableList<String> getCommandLineArgs(String binary, BuildFile buildFile) {
    ImmutableList.Builder<String> cmd = ImmutableList.builder();
    cmd.add(binary);
    BlazeFileType type = buildFile.getBlazeFileType();
    return cmd.add(fileTypeArg(type)).addAll(pathArg(buildFile)).build();
  }

  @Nullable
  static File workingDirectory(BuildFile buildFile) {
    WorkspaceRoot workspaceRoot =
        BazelWorkspaceRootProvider.INSTANCE.findWorkspaceRoot(buildFile.getFile());
    return workspaceRoot == null ? null : workspaceRoot.directory();
  }

  private static String fileTypeArg(BlazeFileType fileType) {
    return "--type="
        + switch (fileType) {
          case SkylarkExtension -> "bzl";
          case BuildPackage -> "build";
          case Workspace -> "workspace";
          case MODULE -> "module";
        };
  }

  private static Iterable<String> pathArg(@Nullable BuildFile buildFile) {
    if (buildFile == null) {
      return Collections.emptyList();
    } else {
      Path pathToFormat = buildFile.getVirtualFile().toNioPath();
      WorkspaceRoot root = BazelWorkspaceRootProvider.INSTANCE.findWorkspaceRoot(pathToFormat.toFile());

      if (root == null) {
        return Collections.emptyList();
      } else {
        Path relativePath = root.path().relativize(pathToFormat);
        return Collections.singletonList("--path=" + relativePath);
      }
    }
  }
}

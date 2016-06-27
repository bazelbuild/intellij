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
package com.google.idea.blaze.base.sync.workspace;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.AndroidStudioIdeInfo;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.PackageManifestOuterClass;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Decodes android_studio_ide_info.proto ArtifactLocation file paths
 */
public class ArtifactLocationDecoder {

  private final BlazeRoots blazeRoots;
  private final WorkspacePathResolver pathResolver;

  public ArtifactLocationDecoder(BlazeRoots blazeRoots, WorkspacePathResolver pathResolver) {
    this.blazeRoots = blazeRoots;
    this.pathResolver = pathResolver;
  }

  /**
   * Decodes the ArtifactLocation proto, locates the absolute artifact file path.
   * Returns null if the file can't be found (presumably because it was removed
   * since the blaze build)
   */
  @Nullable
  public ArtifactLocation decode(AndroidStudioIdeInfo.ArtifactLocation loc) {
    return decode(loc.getRootPath(),
                  loc.getRootExecutionPathFragment(),
                  loc.getRelativePath(),
                  loc.getIsSource());
  }

  /**
   * Decodes the ArtifactLocation proto, locates the absolute artifact file path.
   * Returns null if the file can't be found (presumably because it was removed
   * since the blaze build)
   */
  @Nullable
  public ArtifactLocation decode(PackageManifestOuterClass.ArtifactLocation loc) {
    return decode(loc.getRootPath(),
                  loc.getRootExecutionPathFragment(),
                  loc.getRelativePath(),
                  loc.getIsSource());
  }

  @Nullable
  private ArtifactLocation decode(
    String rootPath,
    String rootExecutionPathFragment,
    String relativePath,
    boolean isSource) {
    File root;
    if (isSource) {
      root = pathResolver.findPackageRoot(relativePath);
    } else {
      if (rootExecutionPathFragment.isEmpty()) {
        // old format -- derive execution path fragment from the root path.
        // it's a backwards way of doing it -- but we want to test the new code,
        // and this will soon be removed
        rootExecutionPathFragment = deriveRootExecutionPathFragmentFromRoot(rootPath);
      }
      root = new File(blazeRoots.executionRoot, rootExecutionPathFragment);
    }
    if (root == null) {
      return null;
    }
    return ArtifactLocation.builder()
      .setRootPath(root.toString())
      .setRootExecutionPathFragment(rootExecutionPathFragment)
      .setRelativePath(relativePath)
      .setIsSource(isSource)
      .build();
  }

  @Deprecated
  private String deriveRootExecutionPathFragmentFromRoot(String rootPath) {
    String execRoot = blazeRoots.executionRoot.toString();
    if (rootPath.startsWith(execRoot)) {
      return rootPath.substring(execRoot.length());
    }
    return "";
  }

}

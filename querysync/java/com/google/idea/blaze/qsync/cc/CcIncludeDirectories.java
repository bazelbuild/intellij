/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.cc;

import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectPath.Root;
import java.nio.file.Path;

/** Static helpers for managing C/C++ include paths. */
public class CcIncludeDirectories {

  public static final ProjectPath GEN_INCLUDE_BASE =
      ProjectPath.create(Root.PROJECT, Path.of("buildout"));

  private CcIncludeDirectories() {}

  /**
   * Constructs a project path for a given include dir flag value. This can then be used to ensure
   * that the flag passed to the IDE points to the correct location.
   */
  public static ProjectPath projectPathFor(String includeDir) {
    Path includePath = Path.of(includeDir);
    if (includePath.startsWith("blaze-out") || includePath.startsWith("bazel-out")) {
      // The directories given by blaze include the "bazel-out" component, but that is not present
      // in the paths of the generated headers themselves due to the legacy semantics of
      // OutputArtifactInfo which strips it. Since that determines their location in the
      // artifact store, remove here too it to ensure consistency:
      return GEN_INCLUDE_BASE.resolveChild(includePath.getName(0).relativize(includePath));
    } else {
      return ProjectPath.WORKSPACE_ROOT.resolveChild(includePath);
    }
  }
}

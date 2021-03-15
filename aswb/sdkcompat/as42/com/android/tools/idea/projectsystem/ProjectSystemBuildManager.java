/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

package com.android.tools.idea.projectsystem;

import com.intellij.openapi.Disposable;

/**
 * Backport of ProjectSystemBuildManager interface that is only available in AS 2020.3+. This
 * interface is not used in prior versions and is only there to eliminate compilation errors.
 */
public interface ProjectSystemBuildManager {
  void compileProject();

  BuildResult getLastBuildResult();

  void addBuildListener(Disposable parentDisposable, BuildListener buildListener);

  /** Status of a build. */
  enum BuildStatus {
    SUCCESS,
    FAILED,
    UNKNOWN
  };

  /** Type of build invocation. */
  enum BuildMode {
    COMPILE,
    UNKNOWN
  };

  /** Result of a build invocation. */
  public static class BuildResult {
    public static BuildResult createUnknownBuildResult() {
      return new BuildResult(BuildMode.UNKNOWN, BuildStatus.UNKNOWN, System.currentTimeMillis());
    }

    public BuildResult(BuildMode buildMode, BuildStatus buildStatus, long timestampMillis) {}
  }

  /** Callbacks on build events. */
  public interface BuildListener {
    void buildStarted(BuildMode mode);

    void beforeBuildCompleted(BuildResult result);

    void buildCompleted(BuildResult result);
  }
}

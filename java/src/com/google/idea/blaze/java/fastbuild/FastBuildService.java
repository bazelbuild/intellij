/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.fastbuild;

import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.concurrent.Future;

/**
 * A service that performs incremental compilations without using Blaze.
 *
 * <p>The first call to {@link #createBuild(Label, String, List)} will use blaze to build a deploy
 * jar. Subsequent calls to {@link #createBuild(Label, String, List)} will only rebuild this deploy
 * jar if it doesn't exist or needs to be refreshed (because blaze flags have changed, for example.)
 * Otherwise, it will perform incremental compilation of changed files, without using Blaze. These
 * builds are likely to fail or be incorrect, but when they work they will take much less time than
 * using Blaze to perform the compilation.
 */
public interface FastBuildService {

  static FastBuildService getInstance(Project project) {
    return project.getComponent(FastBuildService.class);
  }

  boolean supportsFastBuilds(BuildSystemName buildSystemName, Kind kind);

  /**
   * Create a fast build.
   *
   * <p>Clients should make sure {@link #supportsFastBuilds} returns true for label's {@link Kind}
   * first.
   */
  Future<FastBuildInfo> createBuild(
      BlazeContext context, Label label, String blazeBinaryPath, List<String> blazeFlags)
      throws FastBuildException;

  void resetBuild(Label label);
}

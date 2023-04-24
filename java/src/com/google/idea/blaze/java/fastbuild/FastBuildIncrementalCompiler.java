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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.java.fastbuild.FastBuildState.BuildOutput;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Set;

/**
 * Given a base deploy jar and some target, perform a series of javac compilations that allows an
 * updated version of the target to be run without using Blaze.
 */
interface FastBuildIncrementalCompiler {

  static FastBuildIncrementalCompiler getInstance(Project project) {
    return project.getService(FastBuildIncrementalCompiler.class);
  }

  /**
   * Compile the label using the previous FastBuildState. The {@code completedBuildOutput} field of
   * {@code buildState} must be present.
   */
  ListenableFuture<BuildOutput> compile(
      BlazeContext context, Label label, FastBuildState buildState, Set<File> modifiedFiles);
}

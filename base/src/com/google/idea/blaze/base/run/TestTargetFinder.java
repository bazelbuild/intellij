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
package com.google.idea.blaze.base.run;

import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;

/** Locates test rules for a given file. */
public interface TestTargetFinder {

  static TestTargetFinder getInstance(Project project) {
    return ServiceManager.getService(project, TestTargetFinder.class);
  }

  /**
   * Finds all test rules 'reachable' from source file (i.e. with source included in srcs, deps or
   * runtime_deps).
   */
  Collection<TargetInfo> testTargetsForSourceFile(File sourceFile);
}

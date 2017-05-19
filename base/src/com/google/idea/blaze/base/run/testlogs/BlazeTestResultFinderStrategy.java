/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.testlogs;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** A strategy for locating results from 'blaze test' invocation (e.g. output XML files). */
public interface BlazeTestResultFinderStrategy {

  ExtensionPointName<BlazeTestResultFinderStrategy> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BlazeTestXmlFinderStrategy");

  /**
   * Attempt to find all output test XML files produced by the most recent blaze invocation, grouped
   * by target label.
   */
  @Nullable
  static BlazeTestResults locateTestResults(Project project) {
    BuildSystem buildSystem = Blaze.getBuildSystem(project);
    for (BlazeTestResultFinderStrategy strategy : EP_NAME.getExtensions()) {
      if (strategy.handlesBuildSystem(buildSystem)) {
        return strategy.findTestResults(project);
      }
    }
    return null;
  }

  /**
   * Attempt to find test results corresponding to the most recent blaze invocation. Called after
   * the 'blaze test' process completes.
   */
  @Nullable
  BlazeTestResults findTestResults(Project project);

  /** Results are taken from the first strategy handling a given build system */
  boolean handlesBuildSystem(BuildSystem buildSystem);
}

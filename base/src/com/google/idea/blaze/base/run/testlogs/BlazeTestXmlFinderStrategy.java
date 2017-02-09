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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

/** A strategy for locating output test XML files. */
public interface BlazeTestXmlFinderStrategy {

  ExtensionPointName<BlazeTestXmlFinderStrategy> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BlazeTestXmlFinderStrategy");

  /**
   * Attempt to find all output test XML files associated with the given run configuration. Called
   * after the 'blaze test' process completes.
   */
  static ImmutableList<CompletedTestTarget> locateTestXmlFiles(Project project) {
    BuildSystem buildSystem = Blaze.getBuildSystem(project);
    ImmutableList.Builder<CompletedTestTarget> output = ImmutableList.builder();
    for (BlazeTestXmlFinderStrategy strategy : EP_NAME.getExtensions()) {
      if (strategy.handlesBuildSystem(buildSystem)) {
        output.addAll(strategy.findTestXmlFiles(project));
      }
    }
    return output.build();
  }

  /**
   * Attempt to find all output test XML files associated with the given run configuration using a
   * particular strategy. Called after the 'blaze test' process completes.
   */
  ImmutableList<CompletedTestTarget> findTestXmlFiles(Project project);

  boolean handlesBuildSystem(BuildSystem buildSystem);
}

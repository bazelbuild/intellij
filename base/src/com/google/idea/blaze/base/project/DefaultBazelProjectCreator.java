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
package com.google.idea.blaze.base.project;

import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.project.Project;

/** Default implementation of {@link ExtendableBazelProjectCreator}. */
public class DefaultBazelProjectCreator implements ExtendableBazelProjectCreator {

  /**
   * Creates a project with additional configuration.
   *
   * @param builder the project builder
   * @param name the name of the project
   * @param path the path to the project
   * @return the created project
   */
  @Override
  public Project createProject(ProjectBuilder builder, String name, String path) {
    return builder.createProject(name, path);
  }

  /** Returns true if the project can be created. */
  @Override
  public boolean canCreateProject() {
    return true;
  }
}

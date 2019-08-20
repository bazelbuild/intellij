/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.openapi;

import com.intellij.coverage.AbstractCoverageProjectViewNodeDecorator;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.openapi.project.Project;

/**
 * Compat for {@link AbstractCoverageProjectViewNodeDecorator}. Remove when #api191 is no longer
 * supported.
 */
public abstract class AbstractCoverageProjectViewNodeDecoratorCompat
    extends AbstractCoverageProjectViewNodeDecorator {
  protected AbstractCoverageProjectViewNodeDecoratorCompat(
      Project project, CoverageDataManager coverageDataManager) {
    super(coverageDataManager);
  }

  protected CoverageDataManager getCoverageDataManager(Project project) {
    return super.getCoverageDataManager();
  }
}

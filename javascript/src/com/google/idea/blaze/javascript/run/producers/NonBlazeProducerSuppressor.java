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
package com.google.idea.blaze.javascript.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.javascript.jest.JestRunConfigurationProducer;
import com.intellij.javascript.protractor.ProtractorRunConfigurationProducer;
import com.intellij.lang.javascript.buildTools.grunt.rc.GruntRunConfigurationProducer;
import com.intellij.lang.javascript.buildTools.gulp.rc.GulpRunConfigurationProducer;
import com.intellij.lang.javascript.buildTools.npm.rc.NpmRunConfigurationProducer;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;

/** Suppresses certain non-Blaze configuration producers in Blaze projects. */
public class NonBlazeProducerSuppressor implements ProjectComponent {

  /**
   * Returns a list of run configuration producers to suppress for Blaze projects.
   *
   * <p>These classes must all be accessible from the Blaze plugin's classpath (e.g. they shouldn't
   * belong to any plugins not listed as dependencies of the Blaze plugin).
   */
  private static final ImmutableList<Class<? extends RunConfigurationProducer<?>>>
      PRODUCERS_TO_SUPPRESS =
          ImmutableList.of(
              JestRunConfigurationProducer.class,
              GruntRunConfigurationProducer.class,
              ProtractorRunConfigurationProducer.class,
              GulpRunConfigurationProducer.class,
              NpmRunConfigurationProducer.class);

  private final Project project;

  public NonBlazeProducerSuppressor(Project project) {
    this.project = project;
  }

  @Override
  public void projectOpened() {
    if (Blaze.isBlazeProject(project)) {
      suppressProducers(project);
    }
  }

  private static void suppressProducers(Project project) {
    RunConfigurationProducerService producerService =
        RunConfigurationProducerService.getInstance(project);
    PRODUCERS_TO_SUPPRESS.forEach(producerService::addIgnoredProducer);
  }
}

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
package com.google.idea.sdkcompat.golang;

import com.goide.execution.application.GoApplicationConfiguration;
import com.goide.execution.application.GoApplicationConfiguration.Kind;
import com.goide.execution.application.GoApplicationRunConfigurationType;
import com.intellij.execution.RunManager;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/**
 * Adapter to bridge different SDK versions.
 *
 * <p>#api173: package path of GoLand run configuration classes changed in 2018.1.
 */
public class GoApplicationConfigurationHolder {

  /** Adapter to bridge different SDK versions. */
  public enum KindAdapter {
    DIRECTORY(Kind.DIRECTORY),
    PACKAGE(Kind.PACKAGE),
    FILE(Kind.FILE);

    private final Kind kind;

    KindAdapter(Kind kind) {
      this.kind = kind;
    }
  }

  public static GoApplicationConfigurationHolder createTemplateConfiguration(Project project) {
    GoApplicationConfiguration config =
        (GoApplicationConfiguration)
            GoApplicationRunConfigurationType.getInstance()
                .getConfigurationFactories()[0]
                .createTemplateConfiguration(project, RunManager.getInstance(project));
    return new GoApplicationConfigurationHolder(config);
  }

  public final GoApplicationConfiguration config;

  private GoApplicationConfigurationHolder(GoApplicationConfiguration config) {
    this.config = config;
  }

  public void setKind(KindAdapter kind) {
    config.setKind(kind.kind);
  }

  public void setPackage(String packageString) {
    config.setPackage(packageString);
  }

  public void setOutputDirectory(@Nullable String outputDirectory) {
    config.setOutputDirectory(outputDirectory);
  }
}

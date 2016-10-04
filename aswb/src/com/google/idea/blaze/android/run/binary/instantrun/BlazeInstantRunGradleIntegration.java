/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.binary.instantrun;

import com.android.SdkConstants;
import com.google.common.base.Joiner;
import com.google.common.hash.Hashing;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.async.process.PrintOutputLineProcessor;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.common.experiments.DeveloperFlag;
import com.google.idea.common.experiments.StringExperiment;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import java.io.File;
import javax.annotation.Nullable;

/** Defines where instant run storage and artifacts go. */
class BlazeInstantRunGradleIntegration {
  private static final String INSTANT_RUN_SUBDIRECTORY = "instantrun";

  private static final StringExperiment LOCAL_GRADLE_VERSION =
      new StringExperiment("use.local.gradle.version");
  private static final DeveloperFlag REBUILD_LOCAL_GRADLE =
      new DeveloperFlag("rebuild.local.gradle");

  /** Gets a unique directory for a given target that can be used for the build process. */
  static File getInstantRunArtifactDirectory(Project project, Label target) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    assert importSettings != null;
    File dataSubDirectory = BlazeDataStorage.getProjectDataDir(importSettings);
    File instantRunDirectory = new File(dataSubDirectory, INSTANT_RUN_SUBDIRECTORY);
    String targetHash = Hashing.md5().hashUnencodedChars(target.toString()).toString();
    return new File(instantRunDirectory, targetHash);
  }

  @Nullable
  static String getGradleUrl(BlazeContext context) {
    String localGradleVersion = LOCAL_GRADLE_VERSION.getValue();
    boolean isDevMode = localGradleVersion != null;

    if (isDevMode) {
      String toolsIdeaPath = PathManager.getHomePath();
      File toolsDir = new File(toolsIdeaPath).getParentFile();
      File repoDir = toolsDir.getParentFile();
      File localGradleDirectory =
          new File(
              new File(repoDir, "out/repo/com/android/tools/build/builder"), localGradleVersion);
      if (REBUILD_LOCAL_GRADLE.getValue() || !localGradleDirectory.exists()) {
        // Build gradle
        context.output(new StatusOutput("Building local Gradle..."));
        int retVal =
            ExternalTask.builder(toolsDir)
                .args("./gradlew", ":init", ":publishLocal")
                .stdout(LineProcessingOutputStream.of(new PrintOutputLineProcessor(context)))
                .build()
                .run();

        if (retVal != 0) {
          IssueOutput.error("Gradle build failed.").submit(context);
          return null;
        }
      }
      return new File(repoDir, "out/repo").getPath();
    }

    // Not supported yet
    IssueOutput.error(
            "You must specify 'use.local.gradle.version' experiment, "
                + "non-local gradle not supported yet.")
        .submit(context);
    return null;
  }

  static String getGradlePropertiesString() {
    return Joiner.on('\n')
        .join("org.gradle.daemon=true", "org.gradle.jvmargs=-XX:MaxPermSize=1024m -Xmx4096m");
  }

  static String getGradleBuildInfoString(
      String gradleUrl, File executionRoot, File apkManifestFile) {
    String template =
        Joiner.on('\n')
            .join(
                "buildscript {",
                "  repositories {",
                "    jcenter()",
                "    maven { url '%s' }",
                "  }",
                "  dependencies {",
                "    classpath 'com.android.tools.build:gradle:%s'",
                "  }",
                "}",
                "apply plugin: 'com.android.external.build'",
                "externalBuild {",
                "  executionRoot = '%s'",
                "  buildManifestPath = '%s'",
                "}");
    String gradleVersion = LOCAL_GRADLE_VERSION.getValue();
    gradleVersion = gradleVersion != null ? gradleVersion : SdkConstants.GRADLE_LATEST_VERSION;

    return String.format(
        template, gradleUrl, gradleVersion, executionRoot.getPath(), apkManifestFile.getPath());
  }
}

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
package com.google.idea.blaze.java.run.fastbuild;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.project.Project;
import java.io.File;
import javax.annotation.Nullable;

final class BazelFastBuildTestEnvironmentCreator extends FastBuildTestEnvironmentCreator {

  // Bazel adds the Java launcher to the runfiles path when building a Java test target.
  private static final File STANDARD_JAVA_BINARY = new File("../local_jdk/bin/java");

  @Override
  String getTestClassProperty() {
    return "bazel.test_suite";
  }

  @Override
  String getTestRunner() {
    return "com.google.testing.junit.runner.BazelTestRunner";
  }

  @Override
  File getJavaBinFromLauncher(
      Project project,
      Label label,
      @Nullable Label javaLauncher,
      boolean swigdeps,
      String runfilesPath) {
    if (javaLauncher == null) {
      return getStandardJavaBinary(runfilesPath);
    } else {
      return new File(getTestBinary(label) + "_nativedeps");
    }
  }

  /**
   * Look for the directory containing Bazel local jdk and return the java binary.
   *
   * <p>Bazel adds the Java launcher to the runfiles path when building a Java test target. If
   * `bzlmod` is enabled, the directory name is formatted as
   * 'rules_java~{RULES_JAVA_VERSION}~toolchains~local_jdk' otherwise it is `local_jdk`.
   */
  private static File getStandardJavaBinary(String runfilesPath) {
    for (File file :
        new File(runfilesPath)
            .listFiles(fn -> fn.getName().matches("rules_java~.*~toolchains~local_jdk"))) {
      if (file.isDirectory()) {
        return file.toPath().resolve("bin/java").toFile();
      }
    }
    return STANDARD_JAVA_BINARY;
  }

  @Override
  public ImmutableSet<BuildSystemName> getSupportedBuildSystems() {
    return ImmutableSet.of(BuildSystemName.Bazel);
  }
}

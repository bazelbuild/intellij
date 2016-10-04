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
package com.google.idea.blaze.base.command;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.BuildFlagsSection;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformUtils;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/** The collection of all the Bazel flag strings we use. */
public final class BlazeFlags {

  // Build the maximum number of possible dependencies of the project and to show all the build
  // errors in single go.
  public static final String KEEP_GOING = "--keep_going";
  // Tells Blaze to open a debug port and wait for a connection while running tests
  // It expands to: --test_arg=--wrapper_script_flag=--debug --test_output=streamed
  //   --test_strategy=exclusive --test_timeout=9999 --nocache_test_results
  public static final String JAVA_TEST_DEBUG = "--java_debug";
  // Tells the Java wrapper stub to launch JVM in remote debugging mode, waiting for a connection
  public static final String JAVA_BINARY_DEBUG = "--debug";
  // Runs tests locally, in sequence (rather than parallel), and streams their results to stdout.
  public static final String TEST_OUTPUT_STREAMED = "--test_output=streamed";
  // Filters the unit tests that are run (used with regexp for Java/Robolectric tests).
  public static final String TEST_FILTER = "--test_filter";
  // Skips checking for output file modifications (reduced statting -> faster).
  public static final String NO_CHECK_OUTPUTS = "--noexperimental_check_output_files";
  // Ignores implicit dependencies (e.g. java_library rules depending implicitly on
  // "//transconsole/tools:aggregate_messages" in order to support translations).
  public static final String NO_IMPLICIT_DEPS = "--noimplicit_deps";
  // Ignores host dependencies.
  public static final String NO_HOST_DEPS = "--nohost_deps";
  // When used with mobile-install, deploys the an app incrementally.
  public static final String INCREMENTAL = "--incremental";
  // When used with mobile-install, deploys the an app incrementally
  // can be used for API 23 or higher, for which it is preferred to --incremental
  public static final String SPLIT_APKS = "--split_apks";
  // Re-run the test even if the results are cached.
  public static final String NO_CACHE_TEST_RESULTS = "--nocache_test_results";
  // Avoids node GC between ide_build_info and blaze build
  public static final String VERSION_WINDOW_FOR_DIRTY_NODE_GC =
      "--version_window_for_dirty_node_gc=-1";

  public static final String EXPERIMENTAL_SHOW_ARTIFACTS = "--experimental_show_artifacts";

  public static List<String> buildFlags(Project project, ProjectViewSet projectViewSet) {
    BuildSystem buildSystem = Blaze.getBuildSystem(project);
    List<String> flags = Lists.newArrayList();
    for (BuildFlagsProvider buildFlagsProvider : BuildFlagsProvider.EP_NAME.getExtensions()) {
      buildFlagsProvider.addBuildFlags(buildSystem, projectViewSet, flags);
    }
    flags.addAll(projectViewSet.listItems(BuildFlagsSection.KEY));
    return flags;
  }

  // Pass-through arg for sending adb options during mobile-install.
  public static final String ADB_ARG = "--adb_arg=";

  public static ImmutableList<String> adbSerialFlags(String serial) {
    return ImmutableList.of(ADB_ARG + "-s ", ADB_ARG + serial);
  }

  // Pass-through arg for sending test arguments.
  public static final String TEST_ARG = "--test_arg=";

  private static final String TOOL_TAG = "--tool_tag=ijwb:";

  // We add this to every single BlazeCommand instance. It's for tracking usage.
  public static String getToolTagFlag() {
    String platformPrefix = PlatformUtils.getPlatformPrefix();

    // IDEA Community Edition is "Idea", whereas IDEA Ultimate Edition is "idea".
    // That's dumb. Let's make them more useful.
    if (PlatformUtils.isIdeaCommunity()) {
      platformPrefix = "IDEA:community";
    } else if (PlatformUtils.isIdeaUltimate()) {
      platformPrefix = "IDEA:ultimate";
    }
    return TOOL_TAG + platformPrefix;
  }

  public static String testFilterFlagForClass(String className) {
    return testFilterFlagForClassAndMethod(className, null);
  }

  public static String testFilterFlagForClassAndMethod(
      String className, @Nullable String methodName) {
    StringBuilder output = new StringBuilder(TEST_FILTER);
    output.append('=');
    output.append(className);

    if (!Strings.isNullOrEmpty(methodName)) {
      output.append('#');
      output.append(methodName);
    }

    return output.toString();
  }

  public static String testFilterFlagForClassAndMethods(
      String className, Collection<String> methodNames, boolean isJUnit3Class) {
    if (methodNames.size() == 0) {
      return testFilterFlagForClass(className);
    } else if (methodNames.size() == 1) {
      return testFilterFlagForClassAndMethod(className, methodNames.iterator().next());
    }
    String methodNamePattern;
    if (isJUnit3Class) {
      methodNamePattern = String.join(",", methodNames);
    } else {
      methodNamePattern = String.format("(%s)", String.join("|", methodNames));
    }
    return testFilterFlagForClassAndMethod(className, methodNamePattern);
  }

  private BlazeFlags() {}
}

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
package com.google.idea.blaze.android.projectsystem;

import com.android.tools.idea.projectsystem.ClassFileFinder;
import com.google.common.collect.ImmutableMap;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.experiments.StringExperiment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.SystemInfo;
import java.util.function.Function;

/** Factory to create a {@link BlazeClassFileFinder}. */
public class BlazeClassFileFinderFactory {
  /**
   * The name of the implementation of {@link ClassFileFinder} that BlazeModuleSystem should use.
   * This defaults to PsiBasedClassFileFinder if the experiment isn't set or if an invalid name is
   * given.
   */
  public static final StringExperiment CLASS_FILE_FINDER_NAME =
      new StringExperiment("blaze.class.file.finder.name");

  /**
   * Experiment to enable non-default {@link BlazeClassFileFinder} in MacOS. This is a temporary
   * patch to allow disabling {@link RenderJarClassFileFinder} on MacOS until we stop refreshing
   * {@link com.google.idea.blaze.android.libraries.RenderJarCache} at the end of syncs.
   *
   * <p>TODO(b/191680137): Remove MacOS specific logic after b/191680137 is fixed.
   */
  public static final BoolExperiment allowNonDefaultFinderOnMac =
      new BoolExperiment("aswb.allow.non.default.finder.mac", true);

  private static final String DEFAULT_CLASS_FILE_FINDER_NAME =
      PsiBasedClassFileFinder.CLASS_FINDER_KEY;

  private static final ImmutableMap<String, Function<Module, BlazeClassFileFinder>>
      CLASS_FILE_FINDER_CONSTRUCTORS =
          ImmutableMap.<String, Function<Module, BlazeClassFileFinder>>builder()
              .put(
                  TransitiveClosureClassFileFinder.CLASS_FINDER_KEY,
                  TransitiveClosureClassFileFinder::new)
              .put(PsiBasedClassFileFinder.CLASS_FINDER_KEY, PsiBasedClassFileFinder::new)
              .put(RenderJarClassFileFinder.CLASS_FINDER_KEY, RenderJarClassFileFinder::new)
              .build();

  /**
   * Returns value of {@link #CLASS_FILE_FINDER_NAME} if it's a key in {@link
   * #CLASS_FILE_FINDER_CONSTRUCTORS} or {@link #DEFAULT_CLASS_FILE_FINDER_NAME} otherwise.
   */
  public static String getClassFileFinderName() {
    // Return the default class file finder if non-default class file finder is not allowed on macs
    if (SystemInfo.isMac && !allowNonDefaultFinderOnMac.getValue()) {
      return DEFAULT_CLASS_FILE_FINDER_NAME;
    }

    String finderName = CLASS_FILE_FINDER_NAME.getValue();
    if (!CLASS_FILE_FINDER_CONSTRUCTORS.containsKey(finderName)) {
      finderName = DEFAULT_CLASS_FILE_FINDER_NAME;
    }

    return finderName;
  }

  /**
   * Returns a new BlazeClassFileFinder for the given module. The particular implementation used is
   * determined by the value of the CLASS_FILE_FINDER_NAME flag.
   */
  public static BlazeClassFileFinder createBlazeClassFileFinder(Module module) {
    return CLASS_FILE_FINDER_CONSTRUCTORS.get(getClassFileFinderName()).apply(module);
  }
}

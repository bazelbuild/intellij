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
import com.google.idea.common.experiments.StringExperiment;
import com.intellij.openapi.module.Module;
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

  private static final String DEFAULT_CLASS_FILE_FINDER_NAME = "PsiBasedClassFileFinder";

  private static final ImmutableMap<String, Function<Module, BlazeClassFileFinder>>
      CLASS_FILE_FINDER_CONSTRUCTORS =
          ImmutableMap.<String, Function<Module, BlazeClassFileFinder>>builder()
              .put("TransitiveClosureClassFileFinder", TransitiveClosureClassFileFinder::new)
              .put("PsiBasedClassFileFinder", PsiBasedClassFileFinder::new)
              .build();

  /**
   * Returns a new BlazeClassFileFinder for the given module. The particular implementation used is
   * determined by the value of the CLASS_FILE_FINDER_NAME flag.
   */
  public static BlazeClassFileFinder createBlazeClassFileFinder(Module module) {
    String finderName = CLASS_FILE_FINDER_NAME.getValue();

    if (!CLASS_FILE_FINDER_CONSTRUCTORS.containsKey(finderName)) {
      finderName = DEFAULT_CLASS_FILE_FINDER_NAME;
    }

    return CLASS_FILE_FINDER_CONSTRUCTORS.get(finderName).apply(module);
  }
}

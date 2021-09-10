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
import com.intellij.openapi.module.Module;

/**
 * Factory to create a {@link BlazeClassFileFinder}. This class provides a level of indirection for
 * the times when we experiment with alternative class file finding schemes. But for now, it only
 * returns the RenderJarClassFileFinder.
 */
public class BlazeClassFileFinderFactory {
  /**
   * Returns a unique string identifying the {@link ClassFileFinder} created by {@link
   * #createBlazeClassFileFinder}.
   */
  public static String getClassFileFinderName() {
    return RenderJarClassFileFinder.CLASS_FINDER_KEY;
  }

  /** Returns a new BlazeClassFileFinder for the given module. */
  public static BlazeClassFileFinder createBlazeClassFileFinder(Module module) {
    return new RenderJarClassFileFinder(module);
  }
}

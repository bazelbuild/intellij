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
 * A BlazeClassFileFinder is an implementation of {@link ClassFileFinder} for Blaze projects that
 * sits behind the "blaze.class.file.finder.name" experimental flag. Calling {@link
 * BlazeClassFileFinderFactory#createBlazeClassFileFinder(Module)} selects the appropriate
 * implementation of this interface to use based on the flag's value.
 *
 * <p>This layer of abstraction lets us explore several strategies for finding class files in ASWB
 * simultaneously, while also allowing us to preserve the original implementation just in case
 * something breaks.
 *
 * <p>TODO: Once we've settled on a particular method for finding class files, we can remove this
 * interface, BlazeClassFileFinderFactory, and the other experimental implementations.
 */
public interface BlazeClassFileFinder extends ClassFileFinder {
  /**
   * Returns whether or not BlazeClassJarProvider#getExternalJars should skip registering the
   * resource package of each transitive dependency of the module with ResourceClassRegistry. This
   * side-effect doesn't really belong in BlazeClassJarProvider and new strategies for finding class
   * files shouldn't require it, but we provide this method as a way of preserving the original
   * implementation.
   *
   * @return true if this implementation of BlazeClassFileFinder doesn't require the resource
   *     registration side-effect in BlazeClassJarProvider, or false if it does.
   */
  boolean shouldSkipResourceRegistration();
}

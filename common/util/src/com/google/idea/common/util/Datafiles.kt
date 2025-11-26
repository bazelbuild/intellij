/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.common.util

import com.intellij.util.PathUtil
import java.nio.file.Path

/**
 * Utility object for resolving data files, i.e. files list in the data map of the
 * intellij_plugin_library rule.
 */
object Datafiles {

  private val TEST_SRCDIR by lazy { System.getenv("TEST_SRCDIR") }

  private val TEST_WORKSPACE by lazy { System.getenv("TEST_WORKSPACE") }

  /**
   * Load data files from runfiles during testing.
   */
  val fromRunfiles get() = TEST_SRCDIR != null && TEST_WORKSPACE != null

  /**
   * Load data files from the plugin directory in production.
   */
  val fromPluginDir get() = !fromRunfiles

  /**
   * Resolve a data file path relative to the correct location for the current environment.
   */
  fun resolve(relativePath: String): Path {
    return if (fromRunfiles) {
      Path.of(TEST_SRCDIR, TEST_WORKSPACE, relativePath)
    } else {
      val jarPath = Path.of(PathUtil.getJarPathForClass(Datafiles::class.java))
      // go two up from the plugin jar to find the plugin's root directory
      jarPath.parent.parent.resolve(relativePath)
    }
  }

  fun resolveLazy(relativePath: String): Lazy<Path> {
    return lazy { resolve(relativePath) }
  }
}
/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.clwb.radler

import com.intellij.execution.Location
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.rider.model.RadTestElementModel
import com.jetbrains.rider.model.RadTestFramework

interface RadTestFrameworkSupport {

  val framework: RadTestFramework

  /** Resolves a test path to its declaration or an empty list if this framework does not know the test. */
  fun locate(path: String, project: Project, scope: GlobalSearchScope): List<Location<*>>

  /**
   * The complete `--test_filter` value selecting exactly [tests], or null if no filter can be built.
   *
   * Joining is up to the implementation, since every framework spells a list of tests differently.
   */
  fun createTestFilter(tests: List<RadTestElementModel>): String?

  companion object {
    val EP_NAME: ExtensionPointName<RadTestFrameworkSupport> =
      ExtensionPointName.create("com.google.idea.blaze.clwb.radTestSupport")

    fun forFramework(framework: RadTestFramework): RadTestFrameworkSupport? {
      return EP_NAME.extensionList.firstOrNull { it.framework == framework }
    }
  }
}

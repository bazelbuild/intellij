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
package com.google.idea.blaze.clwb.radler.test

import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils
import com.google.idea.blaze.clwb.radler.RadTestFrameworkSupport
import com.intellij.execution.Location
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.cidr.execution.testing.tcatch.CidrCatchTestLinker
import com.jetbrains.cidr.execution.testing.tcatch.CidrCatchTestLocationProvider
import com.jetbrains.rider.model.RadTestElementModel
import com.jetbrains.rider.model.RadTestFramework

class CatchTestSupport : RadTestFrameworkSupport {

  override val framework: RadTestFramework = RadTestFramework.Catch

  override fun locate(path: String, project: Project, scope: GlobalSearchScope): List<Location<*>> {
    val test = path.substringAfter(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER)

    // a test name which is not a valid catch pattern belongs to another framework, not to a missing test
    val linker = runCatching { CidrCatchTestLinker.create(test) }.getOrNull() ?: return emptyList()

    return CidrCatchTestLocationProvider.INSTANCE.getLocation(
      CidrCatchTestLocationProvider.PROTOCOL, linker.findPath, project, scope
    )
  }

  /** Catch2 accepts a comma-separated list of test case names. */
  override fun createTestFilter(tests: List<RadTestElementModel>): String? {
    return tests.mapNotNull { it.test }.ifEmpty { null }?.joinToString(",")
  }
}

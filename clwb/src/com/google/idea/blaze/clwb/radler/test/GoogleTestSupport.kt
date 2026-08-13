/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
import com.jetbrains.cidr.execution.testing.google.CidrGoogleTestLinker
import com.jetbrains.cidr.execution.testing.google.CidrGoogleTestLocationProvider
import com.jetbrains.rider.model.RadTestElementModel
import com.jetbrains.rider.model.RadTestFramework

class GoogleTestSupport : RadTestFrameworkSupport {

  override val framework: RadTestFramework = RadTestFramework.GTest

  override fun locate(path: String, project: Project, scope: GlobalSearchScope): List<Location<*>> {
    val linker = CidrGoogleTestLinker()
    linker.suite = path.substringBefore(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER, "").ifEmpty { null }
    linker.test = path.substringAfter(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER)

    return CidrGoogleTestLocationProvider.INSTANCE.getLocation(
      CidrGoogleTestLocationProvider.PROTOCOL, linker.path, project, scope
    )
  }

  /** Google Test accepts a colon-separated list of patterns. */
  override fun createTestFilter(tests: List<RadTestElementModel>): String? {
    if (tests.isEmpty()) {
      return null
    }

    return tests.joinToString(":") { createGoogleTestFilter(it.suites?.firstOrNull(), it.test) }
  }
}

private fun createGoogleTestFilter(suite: String?, name: String?): String {
  val suite = suite ?: "*"
  val name = name ?: "*"

  return listOf(
    // matches regular test
    "$suite.$name",

    // matches parameterized test without an installation prefix
    "$suite.$name/*",

    // matches parameterized test with an installation prefix
    "*/$suite.$name/*",

    // matches typed tests
    "$suite/*.$name"
  ).joinToString(":")
}

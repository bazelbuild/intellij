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
package com.google.idea.blaze.clwb

import com.google.common.truth.Truth.assertWithMessage
import com.google.idea.blaze.clwb.base.ClwbIntegrationTestCase
import com.google.idea.blaze.clwb.radler.RadGoogleTestContextProvider
import com.google.idea.blaze.clwb.radler.createGoogleTestFilter
import com.jetbrains.rider.model.RadTestElementModel
import com.jetbrains.rider.model.RadTestFramework
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RadGoogleTestFilterTest : ClwbIntegrationTestCase() {

  @Test
  fun `plain test selects exactly itself`() = doTest(
    suite = "SampleSuite",
    test = "SampleTest",
    selects = listOf("SampleSuite.SampleTest"),
    excludes = listOf("SampleSuite.OtherTest", "OtherSuite.SampleTest"),
  )

  @Test
  fun `value parameterized test selects all instantiations`() = doTest(
    suite = "ParamSuite",
    test = "ParamTest",
    selects = listOf(
      "ParamSuite.ParamTest",
      "Instantiation/ParamSuite.ParamTest/0",
    ),
  )

  @Test
  fun `typed test selects all type instantiations`() = doTest(
    suite = "TypedTestSuite",
    test = "SimpleTest",
    selects = listOf(
      "TypedTestSuite/0.SimpleTest",
      "TypedTestSuite/1.SimpleTest",
    ),
    excludes = listOf("SampleSuite.SampleTest"),
  )

  private fun doTest(
    suite: String,
    test: String?,
    selects: List<String> = emptyList(),
    excludes: List<String> = emptyList(),
  ) {
    val filter = createGoogleTestFilter(suite, test)

    for (name in selects) {
      assertWithMessage("filter '$filter' for $suite.$test should select '$name'")
        .that(matchesGoogleTestFilter(filter, name)).isTrue()
    }
    for (name in excludes) {
      assertWithMessage("filter '$filter' for $suite.$test should not select '$name'")
        .that(matchesGoogleTestFilter(filter, name)).isFalse()
    }
  }

  private fun matchesGoogleTestFilter(filter: String, testName: String): Boolean {
    val patterns = filter.split(':').filter { it.isNotEmpty() }
    return patterns.isEmpty() || patterns.any { filterMatches(it, testName) }
  }

  private fun filterMatches(pattern: String, name: String): Boolean {
    val regex = buildString {
      append('^')

      for (c in pattern) {
        when (c) {
          '*' -> append(".*")
          '?' -> append('.')
          else -> append(Regex.escape(c.toString()))
        }
      }

      append('$')
    }

    return Regex(regex).matches(name)
  }
}

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

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.clwb.base.ClwbIntegrationTestCase
import com.google.idea.blaze.clwb.radler.test.CatchTestSupport
import com.jetbrains.rider.model.RadTestElementModel
import com.jetbrains.rider.model.RadTestFramework
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RadCatchTestFilterTest : ClwbIntegrationTestCase() {

  @Test
  fun `single test selects exactly itself`() {
    assertThat(createTestFilter(catch("Test0"))).isEqualTo("Test0")
  }

  @Test
  fun `multiple tests are comma separated`() {
    assertThat(createTestFilter(catch("Test0"), catch("Test1"))).isEqualTo("Test0,Test1")
  }

  @Test
  fun `no tests select nothing`() {
    assertThat(createTestFilter()).isNull()
  }

  @Test
  fun `unnamed tests select nothing`() {
    assertThat(createTestFilter(catch(null), catch(null))).isNull()
  }

  private fun createTestFilter(vararg tests: RadTestElementModel): String? {
    return CatchTestSupport().createTestFilter(tests.toList())
  }

  private fun catch(test: String?) = RadTestElementModel(RadTestFramework.Catch, null, test, null)
}

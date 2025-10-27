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
package com.google.idea.blaze.aspect.cpp.foreigncc

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.aspect.IntellijAspectResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val REPOSITORY_NAME = "clwb_virtual_includes_project"

@RunWith(JUnit4::class)
class ForeignCcTest {

  @Rule
  @JvmField
  val aspect: IntellijAspectResource = IntellijAspectResource(this::class.java)

  @Test
  fun testIncludePaths() {
    val compilationCtx = aspect.findCIdeInfo("//main:main", REPOSITORY_NAME).compilationContext
    assertThat(compilationCtx.systemIncludesList.filter { it.endsWith("lib/cmake/lib/include")}).hasSize(1)
  }

  /**
   * Rules foreign_cc add the include path to the header files instead of the
   * individual header files themselves. We probably need proper support for
   * rules foreign_cc in the aspect to handle this.
   */
  @Test
  fun testHeadersContainIncludePath() {
    val compilationCtx = aspect.findCIdeInfo("//main:main", REPOSITORY_NAME).compilationContext
    assertThat(compilationCtx.headersList.filter { it.relativePath == "lib/cmake/lib/include" }).hasSize(1)
  }
}

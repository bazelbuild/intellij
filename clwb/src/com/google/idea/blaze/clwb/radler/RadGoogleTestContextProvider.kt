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
package com.google.idea.blaze.clwb.radler

import com.jetbrains.rider.model.RadTestElementModel
import com.jetbrains.rider.model.RadTestFramework
import org.jetbrains.annotations.VisibleForTesting

class RadGoogleTestContextProvider : RadTestContextProvider() {

  override val testFramework: RadTestFramework = RadTestFramework.GTest

  override fun createTestFilter(test: RadTestElementModel): String {
    return createGoogleTestFilter(test.suites?.firstOrNull(), test.test)
  }
}

@VisibleForTesting
fun createGoogleTestFilter(suite: String?, name: String?): String {
  val suite = suite ?: "*"
  val name = name ?: "*"

  return sequence {
    // matches regular test
    yield("$suite.$name")

    // matches parameterized thest without an installation prefix
    yield("$suite.$name/*")

    // matches parameterized test with an installation prefix
    yield("*/$suite.$name/*")

    // matches typed tests
    yield("$suite/*.$name")
  }.joinToString(":")
}

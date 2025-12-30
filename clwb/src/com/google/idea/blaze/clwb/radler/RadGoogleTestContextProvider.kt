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

class RadGoogleTestContextProvider : RadTestContextProvider() {

  override val testFramework: RadTestFramework = RadTestFramework.GTest

  override fun createTestFilter(test: RadTestElementModel): String {
    val suite = test.suites?.firstOrNull() ?: "*"
    val name = test.test ?: "*"

    // derive 3 patterns from the suite and test name:
    // 1. match regular test
    // 2. match parameterized thest without installation prefix
    // 3. match parameterized test with installation prefix
    return "$suite.$name:$suite.$name/*:*/$suite.$name/*"
  }
}

/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze;

import com.google.common.base.Splitter;
import com.google.idea.blaze.aspect.IntellijAspectTest;

/** Abstract test class for Bazel aspect tests */
public abstract class BazelIntellijAspectTest extends IntellijAspectTest {

  protected BazelIntellijAspectTest() {
    // Get the TEST_BINARY path from the environment variable and use only the directories
    // before the package
    super(Splitter.on("/com/").splitToList(System.getenv("TEST_BINARY")).get(0));
  }
}

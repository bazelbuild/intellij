/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.clwb.run;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeCppPathConsoleFilter}. */
@RunWith(JUnit4.class)
public class BlazeCppPathConsoleFilterTest {

  @Test
  public void testFilePathRegex() {
    assertThat(
            BlazeCppPathConsoleFilter.PATTERN
                .matcher("foo/bar/shard_assigner_test.cc:111: Failure")
                .matches())
        .isTrue();
    assertThat(
            BlazeCppPathConsoleFilter.PATTERN
                .matcher("foo/bar/shard_assigner_test.cc:111")
                .matches())
        .isTrue();
  }
}

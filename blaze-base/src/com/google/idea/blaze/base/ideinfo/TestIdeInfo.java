/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.ideinfo;

import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * Test info.
 */
public class TestIdeInfo implements Serializable {
  private static final long serialVersionUID = 1L;

  public enum TestSize {
    SMALL,
    MEDIUM,
    LARGE,
    ENORMOUS
  }

  // Rules are "medium" test size by default
  public static final TestSize DEFAULT_RULE_TEST_SIZE = TestSize.MEDIUM;

  // Non-annotated methods and classes are "small" by default
  public static final TestSize DEFAULT_NON_ANNOTATED_TEST_SIZE = TestSize.SMALL;

  public final TestSize testSize;

  public TestIdeInfo(TestSize testSize) {
    this.testSize = testSize;
  }

  @Nullable
  static public TestSize getTestSize(RuleIdeInfo rule) {
    TestIdeInfo testIdeInfo = rule.testIdeInfo;
    if (testIdeInfo == null) {
      return null;
    }
    return testIdeInfo.testSize;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private TestSize testSize = DEFAULT_RULE_TEST_SIZE;

    public Builder setTestSize(TestSize testSize) {
      this.testSize = testSize;
      return this;
    }

    public TestIdeInfo build() {
      return new TestIdeInfo(testSize);
    }
  }
}

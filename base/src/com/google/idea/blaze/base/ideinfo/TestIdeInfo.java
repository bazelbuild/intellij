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

import com.google.idea.blaze.base.dependencies.TestSize;
import java.io.Serializable;
import javax.annotation.Nullable;

/** Test info. */
public class TestIdeInfo implements Serializable {
  private static final long serialVersionUID = 1L;

  public final TestSize testSize;

  public TestIdeInfo(TestSize testSize) {
    this.testSize = testSize;
  }

  @Nullable
  public static TestSize getTestSize(TargetIdeInfo target) {
    TestIdeInfo testIdeInfo = target.testIdeInfo;
    if (testIdeInfo == null) {
      return null;
    }
    return testIdeInfo.testSize;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for test info */
  public static class Builder {
    private TestSize testSize = TestSize.DEFAULT_RULE_TEST_SIZE;

    public Builder setTestSize(TestSize testSize) {
      this.testSize = testSize;
      return this;
    }

    public TestIdeInfo build() {
      return new TestIdeInfo(testSize);
    }
  }
}

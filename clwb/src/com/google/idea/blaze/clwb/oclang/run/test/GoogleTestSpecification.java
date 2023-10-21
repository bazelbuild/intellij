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
package com.google.idea.blaze.clwb.oclang.run.test;

import com.google.common.base.Joiner;
import com.intellij.openapi.util.text.StringUtil;
import javax.annotation.Nullable;

/** A single gtest test case specification (https://github.com/google/googletest). */
public interface GoogleTestSpecification {

  /** The gtest filter string. Returns null if there is no filtering. */
  @Nullable
  String testFilter();

  /** A human-readable description for this test. Returns null if there is no filtering. */
  @Nullable
  String description();

  /**
   * Built from the raw gtest output, without separating parameter components, etc.<br>
   * This means there is no ambiguity -- guaranteed to be exactly what the gtest runner expects for
   * this test case.
   */
  class FromGtestOutput implements GoogleTestSpecification {

    private final String suiteComponent;
    @Nullable private final String methodComponent;

    public FromGtestOutput(String suiteComponent, @Nullable String methodComponent) {
      this.suiteComponent = suiteComponent;
      this.methodComponent = methodComponent;
    }

    @Override
    public String testFilter() {
      String method = methodComponent != null ? methodComponent : "*";
      return String.format("%s.%s", suiteComponent, method);
    }

    @Override
    public String description() {
      return methodComponent == null
          ? suiteComponent
          : String.format("%s.%s", suiteComponent, methodComponent);
    }
  }

  /**
   * We don't know whether it's parameterized / typed in this context, so need to provide a more
   * flexible filter.
   */
  class FromPsiElement implements GoogleTestSpecification {
    @Nullable private final String suiteOrClass;
    @Nullable private final String method;
    @Nullable private final String instantiation;
    @Nullable private final String param;

    public FromPsiElement(
        @Nullable String suiteOrClass,
        @Nullable String method,
        @Nullable String instantiation,
        @Nullable String param) {
      this.suiteOrClass = suiteOrClass;
      this.method = method;
      this.instantiation = instantiation;
      this.param = param;
    }

    @Override
    @Nullable
    public String testFilter() {
      if (suiteOrClass == null) {
        return null;
      }
      String method = StringUtil.notNullize(this.method, "*");
      String param = StringUtil.notNullize(this.param, "*");
      if (instantiation != null) {
        return Joiner.on(':')
            .join(
                String.format("%s/%s.%s/%s", instantiation, suiteOrClass, method, param),
                String.format("%s/%s/%s.%s", instantiation, suiteOrClass, param, method));
      }
      // we don't know whether it's parameterized and/or typed, so need to handle all cases
      return Joiner.on(':')
          .join(
              String.format("%s.%s", suiteOrClass, method),
              String.format("%s/%s.%s", suiteOrClass, param, method),
              String.format("*/%s.%s/*", suiteOrClass, method),
              String.format("*/%s/*.%s", suiteOrClass, method));
    }

    @Override
    @Nullable
    public String description() {
      if (suiteOrClass == null) {
        return null;
      }
      if (method == null) {
        return suiteOrClass;
      }
      if (instantiation == null) {
        return suiteOrClass + "." + method;
      }
      String param = StringUtil.notNullize(this.param, "*");
      return String.format("%s/%s.%s/%s", instantiation, suiteOrClass, method, param);
    }
  }
}

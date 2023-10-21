/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Represents reference to a blaze test. */
public class BlazeCppTestInfo {

  // Patterns (parts in [] are optional):
  // [instantiation/]suite[/suiteorder][::test[/testorder]]

  private static final ImmutableList<String> SUPPORTED_PROTOCOLS =
      ImmutableList.of(SmRunnerUtils.GENERIC_SUITE_PROTOCOL, SmRunnerUtils.GENERIC_TEST_PROTOCOL);

  private static final String TEST_NAME_COMPONENT_SEPARATOR = "/";

  private static final Pattern TEST_PATTERN =
      Pattern.compile(
          "(?:(?<instantiation>[a-zA-Z_]\\w*)/)?"
              + "(?<suite>[a-zA-Z_]\\w*)"
              + "(?:/(?<suiteOrder>\\d+))?"
              + "(?:"
              + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
              + "(?<method>[a-zA-Z_]\\w*)(?:/(?<methodOrder>\\d+))?)?");

  @Nullable final String instantiation;
  final String suite;
  @Nullable final String method;

  final int suiteOrder;
  final int methodOrder;

  private BlazeCppTestInfo(
      @Nullable String instantiation,
      String suite,
      @Nullable String suiteOrder,
      @Nullable String method,
      @Nullable String methodOrder) {
    this.instantiation = instantiation;
    this.suite = suite;
    this.method = method;

    int parseIntTemp = -1;
    if (suiteOrder != null) {
      try {
        parseIntTemp = Integer.parseInt(suiteOrder);
      } catch (NumberFormatException expected) {
      }
    }
    this.suiteOrder = parseIntTemp;

    parseIntTemp = -1;
    if (methodOrder != null) {
      try {
        parseIntTemp = Integer.parseInt(methodOrder);
      } catch (NumberFormatException expected) {
      }
    }
    this.methodOrder = parseIntTemp;
  }

  String suiteComponent() {
    StringBuilder s = new StringBuilder();
    if (instantiation != null) {
      s.append(instantiation).append(TEST_NAME_COMPONENT_SEPARATOR);
    }
    s.append(suite);
    if (suiteOrder != -1) {
      s.append(TEST_NAME_COMPONENT_SEPARATOR).append(suiteOrder);
    }
    return s.toString();
  }

  String methodComponent() {
    if (method == null) {
      return null;
    }
    StringBuilder s = new StringBuilder();
    s.append(method);
    if (methodOrder != -1) {
      s.append(TEST_NAME_COMPONENT_SEPARATOR).append(methodOrder);
    }
    return s.toString();
  }

  @Nullable
  static BlazeCppTestInfo fromPath(String protocol, String path) {
    if (!SUPPORTED_PROTOCOLS.contains(protocol)) {
      return null;
    }
    Matcher matcher = TEST_PATTERN.matcher(path);
    if (!matcher.matches()) {
      return null;
    }
    return new BlazeCppTestInfo(
        matcher.group("instantiation"),
        matcher.group("suite"),
        matcher.group("suiteOrder"),
        matcher.group("method"),
        matcher.group("methodOrder"));
  }
}

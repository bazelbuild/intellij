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
package com.google.idea.blaze.base.model.primitives;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;

/** Wrapper around a string for a blaze kind (android_library, android_test...) */
public enum Kind {
  ANDROID_BINARY("android_binary", LanguageClass.ANDROID),
  ANDROID_LIBRARY("android_library", LanguageClass.ANDROID),
  ANDROID_TEST("android_test", LanguageClass.ANDROID),
  ANDROID_ROBOLECTRIC_TEST("android_robolectric_test", LanguageClass.ANDROID),
  JAVA_LIBRARY("java_library", LanguageClass.JAVA),
  JAVA_TEST("java_test", LanguageClass.JAVA),
  JAVA_BINARY("java_binary", LanguageClass.JAVA),
  JAVA_IMPORT("java_import", LanguageClass.JAVA),
  JAVA_TOOLCHAIN("java_toolchain", LanguageClass.JAVA),
  PROTO_LIBRARY("proto_library", LanguageClass.GENERIC),
  JAVA_PLUGIN("java_plugin", LanguageClass.JAVA),
  ANDROID_RESOURCES("android_resources", LanguageClass.ANDROID),
  CC_LIBRARY("cc_library", LanguageClass.C),
  CC_BINARY("cc_binary", LanguageClass.C),
  CC_TEST("cc_test", LanguageClass.C),
  CC_INC_LIBRARY("cc_inc_library", LanguageClass.C),
  CC_TOOLCHAIN("cc_toolchain", LanguageClass.C),
  JAVA_WRAP_CC("java_wrap_cc", LanguageClass.JAVA),
  GWT_APPLICATION("gwt_application", LanguageClass.JAVA),
  GWT_HOST("gwt_host", LanguageClass.JAVA),
  GWT_MODULE("gwt_module", LanguageClass.JAVA),
  GWT_TEST("gwt_test", LanguageClass.JAVA),
  TEST_SUITE("test_suite", LanguageClass.GENERIC),
  PY_LIBRARY("py_library", LanguageClass.PYTHON),
  PY_BINARY("py_binary", LanguageClass.PYTHON),
  PY_TEST("py_test", LanguageClass.PYTHON),
  PY_APPENGINE_BINARY("py_appengine_binary", LanguageClass.PYTHON),
  PY_WRAP_CC("py_wrap_cc", LanguageClass.PYTHON),
  GO_TEST("go_test", LanguageClass.GO),
  GO_APPENGINE_TEST("go_appengine_test", LanguageClass.GO),
  GO_BINARY("go_binary", LanguageClass.GO),
  GO_APPENGINE_BINARY("go_appengine_binary", LanguageClass.GO),
  GO_LIBRARY("go_library", LanguageClass.GO),
  GO_APPENGINE_LIBRARY("go_appengine_library", LanguageClass.GO),
  GO_WRAP_CC("go_wrap_cc", LanguageClass.GO),
  ;

  static final ImmutableMap<String, Kind> STRING_TO_KIND = makeStringToKindMap();

  private static ImmutableMap<String, Kind> makeStringToKindMap() {
    ImmutableMap.Builder<String, Kind> result = ImmutableMap.builder();
    for (Kind kind : Kind.values()) {
      result.put(kind.toString(), kind);
    }
    return result.build();
  }

  public static Kind fromString(String kindString) {
    return STRING_TO_KIND.get(kindString);
  }

  private final String kind;
  private final LanguageClass languageClass;

  Kind(String kind, LanguageClass languageClass) {
    this.kind = kind;
    this.languageClass = languageClass;
  }

  @Override
  public String toString() {
    return kind;
  }

  public LanguageClass getLanguageClass() {
    return languageClass;
  }

  public boolean isOneOf(Kind... kinds) {
    return isOneOf(Arrays.asList(kinds));
  }

  public boolean isOneOf(List<Kind> kinds) {
    for (Kind kind : kinds) {
      if (this.equals(kind)) {
        return true;
      }
    }
    return false;
  }

  /** Uses the heuristic that test rules are either 'test_suite', or end in '_test' */
  public static boolean isTestRule(String ruleType) {
    return isTestSuite(ruleType) || ruleType.endsWith("_test");
  }

  public static boolean isTestSuite(String ruleType) {
    return "test_suite".equals(ruleType);
  }
}

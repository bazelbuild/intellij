/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.javascript.run.smrunner;

import com.intellij.javascript.testFramework.jasmine.JasmineSuiteStructure;

/** Utilities class for BlazeJavascriptTestEventsHandler tests */
public class BlazeJavascriptTestEventsHandlerTestUtils {

  private BlazeJavascriptTestEventsHandlerTestUtils() {}

  /**
   * #api203: JasmineSuiteStructure#findSuite was removed in 2021.1, we use
   * JasmineSuiteStructure#getChildren to work for all supported versions.
   *
   * <p>#api203: Remove this and replace calls for it with
   * JasmineSuiteStructure#findChildSuiteByName
   */
  public static JasmineSuiteStructure findSuite(JasmineSuiteStructure suite, String name) {
    return (JasmineSuiteStructure)
        suite.getChildren().stream().filter(s -> name.equals(s.getName())).findAny().orElse(null);
  }
}

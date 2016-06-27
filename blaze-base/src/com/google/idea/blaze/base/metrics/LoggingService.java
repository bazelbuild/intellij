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
package com.google.idea.blaze.base.metrics;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

import javax.annotation.Nullable;

/**
 * Logging service that handles logging timing, hit, and other events to an external sink for
 * later analysis.
 */
public interface LoggingService {

  ExtensionPointName<LoggingService> EP_NAME = ExtensionPointName.create("com.google.idea.blaze.LoggingService");

  /**
   * Report a value for an event to the available logging services.
   *  @param variable The variable to report to. Once a value is selected for a logical
   *                 measurement, the variable's name should never change, even if the colloquial name for the
   *                 variable changes.
   */
  static void reportEvent(Project project, Action variable) {
    reportEvent(project, variable, 0);
  }

  /**
   * Report a value for an event to the available logging services.
   *  @param variable The variable to report to. Once a value is selected for a logical
   *                 measurement, the variable's name should never change, even if the colloquial name for the
   *                 variable changes.
   * @param value    should be >= 0, set the value to 0 if the value is meaningless
   */
  static void reportEvent(Project project, Action variable, long value) {
    for (LoggingService service : EP_NAME.getExtensions()) {
      service.doReportEvent(project, variable, value);
    }
  }

  /**
   * Report a value for an event to the logging service
   *  @param variable The variable to report to. Once a value is selected for a logical
   *                 measurement, the variable's name should never change, even if the colloquial name for the
   *                 variable changes.
   * @param value    should be >= 0, set the value to 0 if the value is meaningless
   */
  void doReportEvent(@Nullable Project project, Action variable, long value);

}

/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base;

import com.google.idea.testing.ServiceHelper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Class to clean up any changes made to project or application service */
final class BlazeIntegrationTestCaseServiceHandler {

  // api193: It is no longer possible to register project/application services with arbitrary
  // disposables. They are attached to either project or application. Projects are reused in light
  // test cases, and Application is reused whenever possible.
  // This means application or project services can persist between tests.
  // These fields are used to store consumers that will reset services to their original values
  // during teardown.
  private Map<Class<?>, Consumer<Void>> resetProjectServicesMethods;
  private Map<Class<?>, Consumer<Void>> resetApplicationServicesMethods;

  void setUp() {
    resetProjectServicesMethods = new HashMap<>();
    resetApplicationServicesMethods = new HashMap<>();
  }

  void tearDown() {
    resetProjectServicesMethods.values().forEach(c -> c.accept(null));
    resetProjectServicesMethods = null;

    resetApplicationServicesMethods.values().forEach(c -> c.accept(null));
    resetApplicationServicesMethods = null;
  }

  <T> void registerApplicationService(Class<T> key, T implementation, Disposable parentDisposable) {
    // If overwriting an existing service, register a method to undo the overwrite.
    T currImpl = ServiceManager.getService(key);
    if (currImpl != null) {
      resetApplicationServicesMethods.putIfAbsent(
          key, unused -> ServiceHelper.registerApplicationService(key, currImpl, parentDisposable));
    }

    ServiceHelper.registerApplicationService(key, implementation, parentDisposable);
  }

  protected <T> void registerProjectService(
      Project project, Class<T> key, T implementation, Disposable parentDisposable) {
    // If overwriting an existing service, register a method to undo the overwrite.
    T currImpl = ServiceManager.getService(project, key);
    if (currImpl != null) {
      resetProjectServicesMethods.putIfAbsent(
          key,
          unused -> ServiceHelper.registerProjectService(project, key, currImpl, parentDisposable));
    }

    ServiceHelper.registerProjectService(project, key, implementation, parentDisposable);
  }
}

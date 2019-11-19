/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.testframework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.testFramework.ServiceContainerUtil;

/** Compat APIs used to replace components and services in tests. */
public class ServiceHelperCompat {
  /** Replaces the component under the given key with the provided implementation. #api192 */
  public static <T> void replaceComponentInstance(
      ComponentManagerImpl componentManager,
      Class<T> key,
      T implementation,
      Disposable parentDisposable) {
    ServiceContainerUtil.registerComponentInstance(
        componentManager, key, implementation, parentDisposable);
  }

  private ServiceHelperCompat() {}

  public static <T> void registerService(
      ComponentManager componentManager,
      Class<T> key,
      T implementation,
      Disposable parentDisposable) {
    ServiceContainerUtil.registerServiceInstance(componentManager, key, implementation);
  }
}

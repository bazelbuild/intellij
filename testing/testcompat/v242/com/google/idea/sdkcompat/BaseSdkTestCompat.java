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
package com.google.idea.sdkcompat;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.testFramework.UITestUtil;

/**
 * Provides SDK compatibility shims for base plugin API classes, available to all IDEs during
 * test-time.
 */
public final class BaseSdkTestCompat {
  private BaseSdkTestCompat() {}

  /** #api221: inline into ServiceHelper */
  public static void unregisterComponent(ComponentManager componentManager, Class<?> componentKey) {
    ((ComponentManagerImpl) componentManager).unregisterComponent(componentKey);
  }

  /** #api222 */
  public static void replaceIdeEventQueueSafely() {
      UITestUtil.replaceIdeEventQueueSafely();
  }
}

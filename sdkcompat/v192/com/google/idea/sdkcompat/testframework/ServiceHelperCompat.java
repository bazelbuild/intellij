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
import com.intellij.openapi.util.Disposer;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.defaults.UnsatisfiableDependenciesException;

/** Compat APIs used to replace components and services in tests. */
public class ServiceHelperCompat {
  /** Replaces the component under the given key with the provided implementation. #api192 */
  public static <T> void replaceComponentInstance(
      ComponentManagerImpl componentManager,
      Class<T> key,
      T implementation,
      Disposable parentDisposable) {
    T old = componentManager.registerComponentInstance(key, implementation);
    Disposer.register(parentDisposable, () -> componentManager.registerComponentInstance(key, old));
  }

  public static <T> void registerService(
      ComponentManager componentManager,
      Class<T> key,
      T implementation,
      Disposable parentDisposable) {
    registerComponentInstance(
        (MutablePicoContainer) componentManager.getPicoContainer(),
        key,
        implementation,
        parentDisposable);
  }

  private static <T> void registerComponentInstance(
      MutablePicoContainer container, Class<T> key, T implementation, Disposable parentDisposable) {
    Object old;
    try {
      old = container.getComponentInstance(key);
    } catch (UnsatisfiableDependenciesException e) {
      old = null;
    }
    container.unregisterComponent(key.getName());
    container.registerComponentInstance(key.getName(), implementation);
    Object finalOld = old;
    Disposer.register(
        parentDisposable,
        () -> {
          container.unregisterComponent(key.getName());
          if (finalOld != null) {
            container.registerComponentInstance(key.getName(), finalOld);
          }
        });
  }

  private ServiceHelperCompat() {}
}

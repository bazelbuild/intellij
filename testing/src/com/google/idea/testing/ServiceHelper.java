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
package com.google.idea.testing;

import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.mock.MockApplication;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.testFramework.ServiceContainerUtil;
import org.picocontainer.MutablePicoContainer;

/** Utility class for registering project services, application services and extensions. */
public class ServiceHelper {

  public static <T> void registerExtensionPoint(
      ExtensionPointName<T> name, Class<T> clazz, Disposable parentDisposable) {
    ExtensionsArea area = Extensions.getRootArea();
    String epName = name.getName();
    area.registerExtensionPoint(epName, clazz.getName(), ExtensionPoint.Kind.INTERFACE, false);
    Disposer.register(parentDisposable, () -> area.unregisterExtensionPoint(epName));
  }

  public static <T> void registerProjectExtensionPoint(
      Project project, ExtensionPointName<T> name, Class<T> clazz, Disposable parentDisposable) {
    ExtensionsArea area = project.getExtensionArea();
    String epName = name.getName();
    area.registerExtensionPoint(epName, clazz.getName(), ExtensionPoint.Kind.INTERFACE, false);
    Disposer.register(parentDisposable, () -> area.unregisterExtensionPoint(epName));
  }

  public static <T> void registerExtension(
      ExtensionPointName<T> name, T instance, Disposable parentDisposable) {
    ExtensionPoint<T> ep = Extensions.getRootArea().getExtensionPoint(name);
    ep.registerExtension(instance, parentDisposable);
  }

  public static <T> void unregisterExtension(
      ExtensionPointName<T> name, Class<? extends T> clazz, Disposable parentDisposable) {
    final var ep = name.getPoint();
    for (final var extension : name.getExtensions()) {
      if (!extension.getClass().equals(clazz)) {
        continue;
      }

      ep.unregisterExtension(extension);
      Disposer.register(parentDisposable, () -> ep.registerExtension(extension));
    }
  }

  public static <T> void registerProjectExtension(
      Project project, ExtensionPointName<T> name, T instance, Disposable parentDisposable) {
    ExtensionPoint<T> ep = project.getExtensionArea().getExtensionPoint(name);
    ep.registerExtension(instance, parentDisposable);
  }

  public static <T> void registerExtensionFirst(
      ExtensionPointName<T> name, T instance, Disposable parentDisposable) {
    ExtensionPoint<T> ep =
        ApplicationManager.getApplication().getExtensionArea().getExtensionPoint(name);
    ep.registerExtension(instance, LoadingOrder.FIRST, parentDisposable);
  }

  /** Unregister all extensions of the given class, for the given extension point. */
  public static <T> void unregisterLanguageExtensionPoint(
      String extensionPointKey, Class<T> clazz, Disposable parentDisposable) {
    ExtensionPoint<LanguageExtensionPoint<T>> ep =
        Extensions.getRootArea().getExtensionPoint(extensionPointKey);
    LanguageExtensionPoint<T>[] existingExtensions = ep.getExtensions();
    for (LanguageExtensionPoint<T> ext : existingExtensions) {
      if (clazz.getName().equals(ext.implementationClass)) {
        ep.unregisterExtension(ext);
        Disposer.register(parentDisposable, () -> ep.registerExtension(ext));
      }
    }
  }

  public static <T> void registerApplicationService(
      Class<T> key, T implementation, Disposable parentDisposable) {
    registerService(ApplicationManager.getApplication(), key, implementation, parentDisposable);
  }

  public static <T> void registerProjectService(
      Project project, Class<T> key, T implementation, Disposable parentDisposable) {
    registerService(project, key, implementation, parentDisposable);
  }

  private static <T> void registerService(
      ComponentManager componentManager,
      Class<T> key,
      T implementation,
      Disposable parentDisposable) {
    if (componentManager.hasComponent(key)) {
      // upstream code can do it all for us
      ServiceContainerUtil.replaceService(componentManager, key, implementation, parentDisposable);
      return;
    }

    // otherwise we should manually unregister on disposal
    ServiceContainerUtil.registerServiceInstance(componentManager, key, implementation);
    if (implementation instanceof Disposable) {
      Disposer.register(parentDisposable, (Disposable) implementation);
    }
    Disposer.register(
        parentDisposable, () -> ((ComponentManagerImpl) componentManager).unregisterComponent(key));
  }
}

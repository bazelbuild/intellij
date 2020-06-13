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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ServiceContainerUtil;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.defaults.UnsatisfiableDependenciesException;

/** Utility class for registering project services, application services and extensions. */
public class ServiceHelper {

  public static <T> void registerExtensionPoint(
      ExtensionPointName<T> name, Class<T> clazz, Disposable parentDisposable) {
    ExtensionsArea area = Extensions.getRootArea();
    String epName = name.getName();
    area.registerExtensionPoint(epName, clazz.getName());
    Disposer.register(parentDisposable, () -> area.unregisterExtensionPoint(epName));
  }

  public static <T> void registerExtension(
      ExtensionPointName<T> name, T instance, Disposable parentDisposable) {
    ExtensionPoint<T> ep = Extensions.getRootArea().getExtensionPoint(name);
    ep.registerExtension(instance);
    Disposer.register(parentDisposable, () -> ep.unregisterExtension(instance));
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

  public static <T> void registerApplicationComponent(
      Class<T> key, T implementation, Disposable parentDisposable) {
    Application application = ApplicationManager.getApplication();
    // #api193 (or #api201?): ComponentManagerImpl moved in 2020.1 dot releases. Check
    // ComponentManagerImpl directly when earlier releases are no longer supported
    boolean isComponentManagerImpl = !(application instanceof MockApplication);
    if (isComponentManagerImpl) {
      ServiceContainerUtil.registerComponentInstance(
          application, key, implementation, parentDisposable);
    } else {
      registerComponentInstance(
          (MutablePicoContainer) application.getPicoContainer(),
          key,
          implementation,
          parentDisposable);
    }
  }

  public static <T> void registerProjectComponent(
      Project project, Class<T> key, T implementation, Disposable parentDisposable) {
    // #api193 (or #api201?): ComponentManagerImpl moved in 2020.1 dot releases. Check
    // ComponentManagerImpl directly when earlier releases are no longer supported
    boolean isComponentManagerImpl = project instanceof ProjectImpl;
    if (isComponentManagerImpl) {
      ServiceContainerUtil.registerComponentInstance(
          project, key, implementation, parentDisposable);
    } else {
      registerComponentInstance(
          (MutablePicoContainer) project.getPicoContainer(), key, implementation, parentDisposable);
    }
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

  private static <T> void registerService(
      ComponentManager componentManager,
      Class<T> key,
      T implementation,
      Disposable parentDisposable) {
    boolean exists = componentManager.getService(key) != null;
    if (exists) {
      // upstream code can do it all for us
      ServiceContainerUtil.replaceService(componentManager, key, implementation, parentDisposable);
      return;
    }

    // otherwise we should manually unregister on disposal
    ServiceContainerUtil.registerServiceInstance(componentManager, key, implementation);
    Disposer.register(
        parentDisposable,
        () ->
            ((MutablePicoContainer) componentManager.getPicoContainer())
                .unregisterComponent(key.getName()));
  }
}

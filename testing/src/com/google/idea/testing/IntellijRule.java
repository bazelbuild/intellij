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

import com.google.idea.sdkcompat.openapi.ExtensionsCompat;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.junit.rules.ExternalResource;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.defaults.UnsatisfiableDependenciesException;

/**
 * A lightweight IntelliJ test rule.
 *
 * <p>Provides a mock application and a mock project.
 */
public final class IntellijRule extends ExternalResource {

  private MockProject project;
  private Disposable testDisposable;

  @Override
  protected void before() {
    testDisposable = Disposer.newDisposable();

    TestUtils.createMockApplication(testDisposable);
    project =
        TestUtils.mockProject(
            ApplicationManager.getApplication().getPicoContainer(), testDisposable);
    ExtensionsCompat.cleanRootArea(testDisposable);
  }

  @Override
  protected void after() {
    Disposer.dispose(testDisposable);
  }

  public Project getProject() {
    return project;
  }

  public <T> void registerApplicationService(Class<T> klass, T instance) {
    registerComponentInstance(
        (MutablePicoContainer) ApplicationManager.getApplication().getPicoContainer(),
        klass,
        instance,
        testDisposable);
  }

  public <T> void registerApplicationComponent(Class<T> klass, T instance) {
    registerComponentInstance(
        (MutablePicoContainer) ApplicationManager.getApplication().getPicoContainer(),
        klass,
        instance,
        testDisposable);
  }

  public <T> void registerProjectService(Class<T> klass, T instance) {
    registerComponentInstance(
        (MutablePicoContainer) getProject().getPicoContainer(), klass, instance, testDisposable);
  }

  public <T> void registerProjectComponent(Class<T> klass, T instance) {
    registerComponentInstance(
        (MutablePicoContainer) getProject().getPicoContainer(), klass, instance, testDisposable);
  }

  public <T> void registerExtensionPoint(ExtensionPointName<T> name, Class<T> type) {
    ServiceHelper.registerExtensionPoint(name, type, testDisposable);
  }

  public <T> void registerExtension(ExtensionPointName<T> name, T instance) {
    ServiceHelper.registerExtension(name, instance, testDisposable);
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
}

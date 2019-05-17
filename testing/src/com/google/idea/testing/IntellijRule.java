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

import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.junit.rules.ExternalResource;
import org.picocontainer.MutablePicoContainer;

/**
 * A lightweight IntelliJ test rule.
 *
 * <p>Provides a mock application and a mock project.
 */
public final class IntellijRule extends ExternalResource {

  private MockProject project;
  private MutablePicoContainer applicationContainer;
  private ExtensionsAreaImpl extensionsArea;
  private Disposable testDisposable;

  private static class RootDisposable implements Disposable {
    @Override
    public void dispose() {}
  }

  @Override
  protected void before() {
    testDisposable = new RootDisposable();
    TestUtils.createMockApplication(testDisposable);
    applicationContainer =
        (MutablePicoContainer) ApplicationManager.getApplication().getPicoContainer();
    project = TestUtils.mockProject(applicationContainer, testDisposable);

    Extensions.cleanRootArea(testDisposable);
    extensionsArea = (ExtensionsAreaImpl) Extensions.getRootArea();
  }

  @Override
  protected void after() {
    Disposer.dispose(testDisposable);
  }

  public Project getProject() {
    return project;
  }

  public <T> IntellijRule registerApplicationService(Class<T> klass, T instance) {
    applicationContainer.registerComponentInstance(klass.getName(), instance);
    return this;
  }

  public <T> IntellijRule registerProjectService(Class<T> klass, T instance) {
    project.getPicoContainer().registerComponentInstance(klass.getName(), instance);
    return this;
  }

  public <T> ExtensionPointImpl<T> registerExtensionPoint(
      ExtensionPointName<T> name, Class<T> type) {
    extensionsArea.registerExtensionPoint(name.getName(), type.getName());
    return extensionsArea.getExtensionPoint(name.getName());
  }
}

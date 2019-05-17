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

import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.mock.MockApplicationEx;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.util.pico.DefaultPicoContainer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.picocontainer.PicoContainer;

/** Test utilities. */
public class TestUtils {

  private static class MockApplication extends MockApplicationEx {
    private final ExecutorService executor = MoreExecutors.newDirectExecutorService();

    MockApplication(Disposable parentDisposable) {
      super(parentDisposable);
    }

    @Override
    public Future<?> executeOnPooledThread(Runnable action) {
      return executor.submit(action);
    }

    @Override
    public <T> Future<T> executeOnPooledThread(Callable<T> action) {
      return executor.submit(action);
    }
  }

  static void createMockApplication(Disposable parentDisposable) {
    final MockApplication instance = new MockApplication(parentDisposable);

    // If there was no previous application, ApplicationManager leaves the MockApplication in place,
    // which can break future tests.
    Application oldApplication = ApplicationManager.getApplication();
    if (oldApplication == null) {
      Disposer.register(
          parentDisposable,
          new Disposable() {
            @Override
            public void dispose() {
              new ApplicationManager() {
                {
                  ourApplication = null;
                }
              };
            }
          });
    }

    ApplicationManager.setApplication(
        instance,
        new Getter<FileTypeRegistry>() {
          @Override
          public FileTypeRegistry get() {
            return FileTypeManager.getInstance();
          }
        },
        parentDisposable);
    instance.registerService(EncodingManager.class, EncodingManagerImpl.class);
  }

  static MockProject mockProject(@Nullable PicoContainer container, Disposable parentDisposable) {
    Extensions.registerAreaClass("IDEA_PROJECT", null);
    container = container != null ? container : new DefaultPicoContainer();
    return new MockProject(container, parentDisposable);
  }
}

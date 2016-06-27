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
package com.google.idea.blaze.base;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.mock.MockApplicationEx;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.io.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static org.junit.Assert.fail;

/**
 * Test utilities.
 */
public class TestUtils {

  static class BlazeMockApplication extends MockApplicationEx {
    private final ListeningExecutorService executor = MoreExecutors.sameThreadExecutor();

    public BlazeMockApplication(@NotNull Disposable parentDisposable) {
      super(parentDisposable);
    }

    @NotNull
    @Override
    public Future<?> executeOnPooledThread(@NotNull Runnable action) {
      return executor.submit(action);
    }

    @NotNull
    @Override
    public <T> Future<T> executeOnPooledThread(@NotNull Callable<T> action) {
      return executor.submit(action);
    }
  }

  public static void createMockApplication(Disposable parentDisposable) {
    final BlazeMockApplication instance = new BlazeMockApplication(parentDisposable);

    // If there was no previous application, ApplicationManager leaves the MockApplication in place, which can break future tests.
    Application oldApplication = ApplicationManager.getApplication();
    if (oldApplication == null) {
      Disposer.register(parentDisposable, () -> {
        new ApplicationManager() {
          { ourApplication = null; }
        };
      });
    }

    ApplicationManager.setApplication(instance,
                                      FileTypeManager::getInstance,
                                      parentDisposable);
    instance.registerService(EncodingManager.class, EncodingManagerImpl.class);
  }

  @NotNull
  public static MockProject mockProject(@Nullable PicoContainer container,
                                        Disposable parentDisposable) {
    Extensions.registerAreaClass("IDEA_PROJECT", null);
    container = container != null
                ? container
                : new DefaultPicoContainer();
    return new MockProject(container, parentDisposable);
  }

  public static void assertIsSerializable(@NotNull Serializable object) {
    ObjectOutputStream out = null;
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try {
      out = new ObjectOutputStream(byteArrayOutputStream);
      out.writeObject(object);
    }
    catch (NotSerializableException e) {
      fail("An object is not serializable: " + e.getMessage());
    }
    catch (IOException e) {
      fail("Could not serialize object: " + e.getMessage());
    }
    finally {
      if (out != null) {
        try {
          out.close();
        }
        catch (IOException e) {
          // ignore
        }
      }
      try {
        byteArrayOutputStream.close();
      }
      catch (IOException e) {
        // ignore
      }
    }
  }

}

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

import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import java.io.File;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;
import org.junit.rules.TemporaryFolder;

/**
 * Provides SDK compatibility shims for base plugin API classes, available to all IDEs during
 * test-time.
 */
public final class BaseSdkTestCompat {
  private BaseSdkTestCompat() {}
  /** #api202 {@link TemporaryFolder#getRoot} mock call needs to return Path since 2020.3 */
  @Nullable
  public static Path getRootWrapper(@Nullable File file) {
    return file == null ? null : file.toPath();
  }

  /** #api202 Creating a StubVirtualFile requires a filesystem parameter in 2020.3 */
  public static StubVirtualFile newValidStubVirtualFile(VirtualFileSystem fileSystem) {
    return new StubVirtualFile(fileSystem) {
      @Override
      public boolean isValid() {
        return true;
      }
    };
  }
}

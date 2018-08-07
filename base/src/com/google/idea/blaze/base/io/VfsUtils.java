/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.io;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Collection;
import javax.annotation.Nullable;

/** A helper class */
public final class VfsUtils {

  /**
   * Attempts to resolve the given file path to a {@link VirtualFile}. If called on the event
   * thread, will refresh if not already cached.
   */
  @Nullable
  public static VirtualFile resolveVirtualFile(File file) {
    LocalFileSystem fileSystem = VirtualFileSystemProvider.getInstance().getSystem();
    VirtualFile vf = fileSystem.findFileByPathIfCached(file.getPath());
    if (vf != null) {
      return vf;
    }
    vf = fileSystem.findFileByIoFile(file);
    if (vf != null && vf.isValid()) {
      return vf;
    }
    boolean shouldRefresh = ApplicationManager.getApplication().isDispatchThread();
    return shouldRefresh ? fileSystem.refreshAndFindFileByIoFile(file) : null;
  }

  /**
   * Attempts to asynchronously resolve the given files to {@link VirtualFile}. Calls the given
   * {@link Runnable} when finished.
   */
  public static void asyncRefreshIoFiles(
      Collection<File> files, boolean recursive, Runnable onFinish) {
    Runnable refresh =
        () ->
            VirtualFileSystemProvider.getInstance()
                .getSystem()
                .refreshIoFiles(files, /* async */ true, recursive, onFinish);
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    if (app.holdsReadLock()) {
      // THE async IS A LIE!
      // The VirtualFiles are still found synchronously,
      // they're just refreshed asynchronously after that.
      app.executeOnPooledThread(refresh);
    } else {
      refresh.run();
    }
  }
}

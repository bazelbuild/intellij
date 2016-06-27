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
package com.google.idea.blaze.base.async.process;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class ProcessUtil {
  private static final Logger LOG = Logger.getInstance(ProcessUtil.class);

  public static Thread forwardAsync(final InputStream input, final OutputStream output) {
    Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        int bufferSize = 4096;
        byte[] buffer = new byte[bufferSize];

        int read = 0;
        try {
          read = input.read(buffer);
          while (read != -1) {
            output.write(buffer, 0, read);
            read = input.read(buffer);
          }
        }
        catch (IOException e) {
          LOG.warn("Error redirecting output", e);
        }
      }
    });
    thread.start();
    return thread;
  }

  @NotNull
  public static String runCommand(
    @NotNull WorkspaceRoot workspaceRoot,
    @NotNull List<String> command
  ) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ExternalTask.Builder builder = ExternalTask.builder(workspaceRoot, command);
    ExternalTask task = builder
      .redirectStderr(true)
      .stdout(output)
      .build();
    task.run();
    return output.toString();
  }
}

/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.base.util;

import com.google.idea.blaze.base.sync.aspects.storage.AspectWriter;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import java.nio.file.Path;
import java.util.Map;

public class TemplateWriter {

  public static void evaluate(
      Path destinationDirectory,
      String destinationFile,
      String templateDirectory,
      String templateFile,
      Map<String, ?> options
  ) throws IOException {
    if (Files.notExists(destinationDirectory)) {
      Files.createDirectories(destinationDirectory);
    }

    final var template = AspectWriter.readAspect(TemplateWriter.class, templateDirectory, templateFile);

    final var dstStream = Files.newOutputStream(
        destinationDirectory.resolve(destinationFile),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
    );

    final var ctx = new VelocityContext();
    options.forEach(ctx::put);

    try (final var writer = new OutputStreamWriter(dstStream)) {
      try (final var reader = new InputStreamReader(template.openStream())) {
        final var success = Velocity.evaluate(ctx, writer, templateFile, reader);

        if (!success) {
          throw new IOException("Failed to evaluate template: " + templateFile);
        }
      }
    }
  }
}

package com.google.idea.blaze.base.util;

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
      VirtualFile templateDirectory,
      String templateFile,
      Map<String, ?> options
  ) throws IOException {
    if (Files.notExists(destinationDirectory)) {
      Files.createDirectories(destinationDirectory);
    }

    final var dstStream = Files.newOutputStream(
        destinationDirectory.resolve(destinationFile),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
    );

    final var template = templateDirectory.findChild(templateFile);
    if (template == null || !template.isValid()) {
      throw new IOException("Could not find valid template file: " + templateFile);
    }

    final var ctx = new VelocityContext();
    options.forEach(ctx::put);

    try (final var writer = new OutputStreamWriter(dstStream)) {
      try (final var reader = new InputStreamReader(template.getInputStream())) {
        final var success = Velocity.evaluate(ctx, writer, templateFile, reader);

        if (!success) {
          throw new IOException("Failed to evaluate template: " + templateFile);
        }
      }
    }
  }
}

package com.google.idea.blaze.base.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

public class TempFile {
  public static File create() {
    return create(new byte[0]);
  }

  public static File create(byte[] contents) {
    return create(UUID.randomUUID().toString(), contents);
  }

  public static File create(String name, byte[] contents) {
    try {
      final var file = File.createTempFile(name, "test");

      final var writer = new FileOutputStream(file);
      writer.write(contents);
      writer.close();

      return file;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

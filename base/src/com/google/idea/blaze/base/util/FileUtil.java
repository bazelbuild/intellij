package com.google.idea.blaze.base.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtil {
  public static void writeIfDifferent(Path path, String fileContent) throws IOException {
    if (!Files.exists(path) || !getFileContent(path).equals(fileContent)) {
      Files.writeString(path, fileContent);
    }
  }
  private static String getFileContent(Path path) throws IOException {
    return Files.readString(path);
  }
}

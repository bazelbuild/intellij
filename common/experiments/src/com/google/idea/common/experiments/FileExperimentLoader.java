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
package com.google.idea.common.experiments;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Reads experiments from a property file. */
final class FileExperimentLoader extends HashingExperimentLoader {

  private static final Logger logger = Logger.getInstance(FileExperimentLoader.class);

  private final String filename;
  private Map<String, String> experiments = ImmutableMap.of();
  private FileTime lastModified = FileTime.fromMillis(0);

  FileExperimentLoader(String filename) {
    this.filename = filename;
  }

  @SuppressWarnings("unchecked") // Properties is Map<Object, Object>, we cast to strings
  @Override
  Map<String, String> getUnhashedExperiments() {
    Properties properties = new Properties();

    File file = new File(filename);
    if (!file.exists()) {
      experiments = ImmutableMap.of();
      return experiments;
    }

    try {
      FileTime lastModified =
          Files.readAttributes(file.toPath(), BasicFileAttributes.class).lastModifiedTime();
      if (Objects.equals(lastModified, this.lastModified)) {
        return experiments;
      }

      try (InputStream fis = new FileInputStream(filename);
          BufferedInputStream bis = new BufferedInputStream(fis)) {
        properties.load(bis);
        experiments = ImmutableMap.copyOf((Map) properties);
        this.lastModified = lastModified;
      }
    } catch (IOException e) {
      logger.warn("Could not load experiments from file: " + filename, e);
    }

    return experiments;
  }

  @Override
  public void initialize() {
    // Reads the file into memory.
    getUnhashedExperiments();
  }
}

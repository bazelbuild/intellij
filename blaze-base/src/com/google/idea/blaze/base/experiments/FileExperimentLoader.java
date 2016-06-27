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
package com.google.idea.blaze.base.experiments;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

/**
 * Reads experiments from a property file.
 */
class FileExperimentLoader implements ExperimentLoader {

  private static final Logger LOG = Logger.getInstance(FileExperimentLoader.class);

  private final String filename;

  public FileExperimentLoader(String filename) {
    this.filename = filename;
  }

  @Override
  public Map<String, String> getExperiments() {
    Properties properties = new Properties();

    File file = new File(filename);
    if (!file.exists()) {
      LOG.info("File " + filename + " does not exist, skipping.");
      return ImmutableMap.of();
    }

    Map<String, String> result = Maps.newHashMap();
    try (InputStream inputStream = new FileInputStream(filename)) {
      properties.load(inputStream);
      Enumeration<?> enumeration = properties.propertyNames();
      while (enumeration.hasMoreElements()) {
        String key = (String)enumeration.nextElement();
        String value = properties.getProperty(key);
        result.put(key, value);
      }
    } catch (IOException e) {
      LOG.warn("Could not load experiments from file: " + filename, e);
    }
    return result;
  }
}

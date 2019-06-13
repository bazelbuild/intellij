/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.Nullable;

/**
 * Transforms support-lib class names to their corresponding androidx class names. Users of this
 * class should call {@link JetifierClassNameTransformer#loadJetpackTransformations(Project)} before
 * using it to perform class name transformations. Transformations are loaded from jetpack migration
 * config files. One can set the file's path relative to project workspace with the system property
 * "android.debug.jetpack.MigrationConfigFilePath". If no path is provided then no transformations
 * will be loaded.
 */
class JetifierClassNameTransformer {
  private static final Logger LOG = Logger.getInstance(JetifierClassNameTransformer.class);
  private Map<String, String> jetifierTransformations = new TreeMap<>();

  /**
   * Loads jetpack class name transformations from a config file in the user's workspace. Location
   * of the config file can be defined with the system property
   * "android.debug.jetpack.MigrationConfigFilePath". If no config file is defined then this method
   * fails silently, effectively performing a no-op on the known list of transformations.
   */
  void loadJetpackTransformations(Project project) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return;
    }

    String sourceMigrationConfigFilePath =
        System.getProperty("android.debug.jetpack.MigrationConfigFilePath", null);
    if (sourceMigrationConfigFilePath == null) {
      return;
    }

    File configFile =
        blazeProjectData.getWorkspacePathResolver().resolveToFile(sourceMigrationConfigFilePath);
    StringBuilder configFileContents = new StringBuilder();
    try {
      BufferedReader configReader = Files.newBufferedReader(configFile.toPath(), UTF_8);
      String line = null;
      while ((line = configReader.readLine()) != null) {
        if (line.trim().startsWith("#")) {
          continue;
        }
        configFileContents.append(line);
      }

      Gson gson = new Gson();
      JetpackMigrationConfig config =
          gson.fromJson(configFileContents.toString(), JetpackMigrationConfig.class);
      jetifierTransformations.clear();
      for (Map.Entry<String, String> transformationEntry :
          config.getTransformationsMap().entrySet()) {
        // Transformations map can use "/" instead of "." to represent transformations.
        jetifierTransformations.put(
            transformationEntry.getKey().replaceAll("/", "."),
            transformationEntry.getValue().replaceAll("/", "."));
      }
    } catch (IOException e) {
      LOG.warn("Jetpack migration config file path was provided but the file could not be read.");
    } catch (JsonParseException e) {
      LOG.warn("Jetpack migration config file path was not of the correct format.");
    }
  }

  /**
   * @return a jetifier transformed class name of the given support-lib class name if a
   *     transformation exists. Null if it doesn't.
   */
  @Nullable
  String getTransformedClassName(String originalClassName) {
    return jetifierTransformations.get(originalClassName);
  }

  /**
   * Container class used for loading migration configs. Migration configs are of the format {"map"
   * : { "types" : { "class1" : "transformed1", "class2", "transformed2", ... }}}
   */
  private static class JetpackMigrationConfig {
    public Map<String, Map<String, String>> map;

    Map<String, String> getTransformationsMap() {
      return map.get("types");
    }
  }
}

/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.ext;

import com.google.idea.blaze.ext.IntelliJExtService;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.Nullable;

/**
 * An application level manager that creates the IntelliJExtService.
 *
 * <p>To enable this service the system property `intellij.ext.binary` must be set to be pointing to
 * the executable that provides the extended services. This executable must implement the
 * intellij-ext grpc interface for the IDE to communicate with it.
 */
public class IntelliJExtManager {

  public static final String INTELLIJ_EXT_BINARY = "intellij.ext.binary";

  private IntelliJExtService service;

  private static final Logger logger = Logger.getInstance(IntelliJExtManager.class);

  // Keep all the experiments together for ease of maintenance
  private static final BoolExperiment ENABLED = new BoolExperiment("use.intellij.ext", false);

  private static final BoolExperiment ISSUETRACKER =
      new BoolExperiment("use.intellij.ext.issuetracker", false);

  private static final BoolExperiment BUILD_SERVICE =
      new BoolExperiment("use.intellij.ext.buildservice", false);

  public static IntelliJExtManager getInstance() {
    return ApplicationManager.getApplication().getService(IntelliJExtManager.class);
  }

  private final String binaryPath;

  public IntelliJExtManager() {
    String path = null;
    if (ENABLED.getValue()) {
      // If the VM option is set, override the path
      path = System.getProperty(INTELLIJ_EXT_BINARY);
      if (path == null) {
        path = SystemInfo.isMac ? "/usr/local/bin/intellij-ext" : "/opt/intellij-ext/intellij-ext";
      }
    }
    this.binaryPath = path;
  }

  public synchronized IntelliJExtService getService() {
    if (service == null) {
      Path path = getBinaryPath();
      if (path == null) {
        throw new IllegalStateException("No intellij-ext binary found");
      }
      service = new IntelliJExtService(path);
    }
    return service;
  }

  @Nullable
  private Path getBinaryPath() {
    if (binaryPath == null) {
      return null;
    }
    Path path = Paths.get(binaryPath);
    if (!Files.exists(path)) {
      logger.warn(String.format("intellij-ext binary path %s does not exist", path));
      return null;
    }
    return path;
  }

  public boolean isEnabled() {
    return getBinaryPath() != null;
  }

  public boolean isIssueTrackerEnabled() {
    return isEnabled() && ISSUETRACKER.getValue();
  }

  public boolean isBuildServiceEnabled() {
    return isEnabled() && BUILD_SERVICE.getValue();
  }
}

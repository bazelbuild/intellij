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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
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
  /**
   * Controls whether the intellij-ext binary is used at all. Ignored if EXPERIMENT_SERVICE_PROPERTY
   * is set.
   */
  private static final BoolExperiment ENABLED = new BoolExperiment("use.intellij.ext", false);

  private static final BoolExperiment ISSUETRACKER =
      new BoolExperiment("use.intellij.ext.issuetracker", false);

  private static final BoolExperiment LINTER = new BoolExperiment("use.intellij.ext.linter", false);

  private static final BoolExperiment CODESEARCH =
      new BoolExperiment("use.intellij.ext.codesearch", false);

  private static final BoolExperiment BUILD_SERVICE =
      new BoolExperiment("use.intellij.ext.buildservice", false);

  private static final BoolExperiment KYTHE = new BoolExperiment("use.intellij.ext.kythe", false);

  private static final BoolExperiment DEPSEREVR =
      new BoolExperiment("use.intellij.ext.depserver", false);

  /**
   * System property controlling the experiments service. If set to 1, forces intellij-ext binary to
   * be available (regardless of ENABLED value)
   */
  private static final String EXPERIMENT_SERVICE_PROPERTY = "use.intellij.ext.experiments";

  private static final BoolExperiment BUILD_CLEANER =
      new BoolExperiment("use.intellij.ext.buildcleaner", false);

  private static final BoolExperiment CHATBOT =
      new BoolExperiment("use.intellij.ext.chatbot", false);

  private static final BoolExperiment PIPER = new BoolExperiment("use.intellij.ext.piper", false);

  public static IntelliJExtManager getInstance() {
    return ApplicationManager.getApplication().getService(IntelliJExtManager.class);
  }

  private final String binaryPath;

  public IntelliJExtManager() {
    String path = null;
    if (isExperimentsServiceEnabled() || ENABLED.getValue()) {
      // If the VM option is set, override the path
      path = System.getProperty(INTELLIJ_EXT_BINARY);
      if (path == null) {
        path = SystemInfo.isMac ? "/usr/local/bin/intellij-ext" : "/opt/intellij-ext/intellij-ext";
      }
    }
    this.binaryPath = path;
  }

  private IntelliJExtManager(String path) {
    this.binaryPath = path;
    service = new IntelliJExtService(Paths.get(path), getLogDir());
  }

  /** Set up {@link IntelliJExtService} for test cases to avoid non-existence binary error. */
  public static IntelliJExtManager createForTest() {
    return new IntelliJExtManager("dummy");
  }

  public synchronized IntelliJExtService getService() {
    if (service == null) {
      Path path = getBinaryPath();
      if (path == null) {
        throw new IllegalStateException("No intellij-ext binary found");
      }
      service = new IntelliJExtService(path, getLogDir());
    }
    return service;
  }

  @Nullable
  private Path getLogDir() {
    Path logDir = null;
    try {
      logDir = PathManager.getLogDir();
    } catch (RuntimeException re) {
      // logDir remains null
    }
    if (logDir == null || !Files.exists(logDir)) {
      logger.warn(String.format("log directory %s does not exist; using default log dir", logDir));
      return null;
    }
    return logDir;
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

  public boolean isLinterEnabled() {
    return isEnabled() && LINTER.getValue();
  }

  public boolean isCodeSearchEnabled() {
    return isEnabled() && CODESEARCH.getValue();
  }

  public boolean isBuildServiceEnabled() {
    return isEnabled() && BUILD_SERVICE.getValue();
  }

  public boolean isDepserverEnabled() {
    return isEnabled() && DEPSEREVR.getValue();
  }

  public boolean isKytheEnabled() {
    return isEnabled() && KYTHE.getValue();
  }

  // This method cannot rely on reading any experiment values (such as those in the constructor of
  // this class), as they will cause circular service instantiation errors when the IDE tries to
  // obtain an experiments service client
  public static boolean isExperimentsServiceEnabled() {
    return Objects.equals(System.getProperty(EXPERIMENT_SERVICE_PROPERTY), "1");
  }

  public boolean isChatBotEnabled() {
    return isEnabled() && CHATBOT.getValue();
  }

  public boolean isBuildCleanerEnabled() {
    return isEnabled() && BUILD_CLEANER.getValue();
  }

  public boolean isPiperEnabled() {
    return isEnabled() && PIPER.getValue();
  }
}

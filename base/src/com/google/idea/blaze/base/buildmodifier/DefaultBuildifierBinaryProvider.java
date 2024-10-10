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
package com.google.idea.blaze.base.buildmodifier;

import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import java.io.File;
import javax.annotation.Nullable;

/** Provides the buildifier binary. */
public class DefaultBuildifierBinaryProvider implements BuildifierBinaryProvider {
  @Nullable
  @Override
  public String getBuildifierBinaryPath() {
    String binaryPath = BlazeUserSettings.getInstance().getBuildifierBinaryPath();

    // Check if the binary path obtained is absolute.
    File absoluteBinaryFile = new File(binaryPath);
    if (absoluteBinaryFile.isAbsolute() && absoluteBinaryFile.exists()) {
      return absoluteBinaryFile.getPath();
    }

    // Since the path is relative, check if the binary exists in any path.
    File relativeBinaryFile = PathEnvironmentVariableUtil.findInPath(binaryPath);
    if (relativeBinaryFile != null) {
      return relativeBinaryFile.getPath();
    }

    if (BuildifierDownloader.canDownload()) {
      BuildifierNotification.showDownloadNotification();
    } else {
      BuildifierNotification.showNotFoundNotification();
    }

    return null;
  }
}

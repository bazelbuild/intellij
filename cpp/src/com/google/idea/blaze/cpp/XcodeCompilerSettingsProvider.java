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
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import java.util.Optional;

/**
 * Interface for fetching Xcode information from the system.
 * This has to be an interface and accompanying global service to allow mocking in tests.
 */
public interface XcodeCompilerSettingsProvider {
  static XcodeCompilerSettingsProvider getInstance(Project project) {
    return ServiceManager.getService(project, XcodeCompilerSettingsProvider.class);
  }

  Optional<XcodeCompilerSettings> fromContext(BlazeContext context, Project project)
      throws XcodeCompilerSettingsException;

  default ImmutableMap<String, String> asEnvironmentVariables(
      Optional<XcodeCompilerSettings> xcodeCompilerSettings) {
    if (xcodeCompilerSettings.isEmpty()) {
      return ImmutableMap.of();
    }
    XcodeCompilerSettings settings = xcodeCompilerSettings.get();
    return ImmutableMap.of(
        "DEVELOPER_DIR", settings.getDeveloperDir().toString(),
        "SDKROOT", settings.getSdkRoot().toString()
    );
  }

  class XcodeCompilerSettingsException extends Exception {

    final XcodeCompilerSettingsProvider.XcodeCompilerSettingsException.IssueKind kind;

    public XcodeCompilerSettingsException(
        XcodeCompilerSettingsProvider.XcodeCompilerSettingsException.IssueKind kind, String message) {
      super(message);
      this.kind = kind;
    }

    public XcodeCompilerSettingsException(
        XcodeCompilerSettingsProvider.XcodeCompilerSettingsException.IssueKind kind, String message,
        Exception cause) {
      super(message, cause);
      this.kind = kind;
    }

    /**
     * Describes the failure mode of fetching Xcode information.
     */
    public enum IssueKind {
      QUERY_DEVELOPER_DIR,
      FETCH_XCODE_VERSION,
    }
  }
}

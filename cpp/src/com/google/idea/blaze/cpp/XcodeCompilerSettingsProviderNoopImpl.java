/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.project.Project;
import java.util.Optional;

/**
 * Empty implementation of the {@link XcodeCompilerSettingsProvider}, to use in OSes other than
 * macOS.
 * Always returns an empty setting, since Xcode doesn't make sense outside of macOS.
 */
public class XcodeCompilerSettingsProviderNoopImpl implements XcodeCompilerSettingsProvider {

  @Override
  public Optional<XcodeCompilerSettings> fromContext(BlazeContext context, Project project, BlazeProjectData projectData)
      throws XcodeCompilerSettingsException {
    return Optional.empty();
  }
}

/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import javax.annotation.Nullable;

/**
 * An Android-Studio-safe version of {@link OCWorkspaceManagerAdapter}, which handles the case where
 * no such manager is available (e.g. because the 'NDK WorkspaceManager Support' plugin isn't
 * enabled). #api173 once pre-2018.1 apis are not needed this can be removed
 */
public final class OCWorkspaceProvider {

  private OCWorkspaceProvider() {}

  @Nullable
  public static OCWorkspace getWorkspace(Project project) {
    OCWorkspaceManagerAdapter manager =
        ServiceManager.getService(project, OCWorkspaceManagerAdapter.class);
    return manager != null ? manager.getWorkspace() : null;
  }
}

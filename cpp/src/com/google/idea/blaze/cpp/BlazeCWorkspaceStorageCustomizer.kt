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
package com.google.idea.blaze.cpp

import com.google.idea.blaze.base.sync.data.BlazeDataStorage
import com.google.idea.sdkcompat.clion.OCWorkspaceStorageCustomizerAdapter
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId

/**
 * Without the module the file index drops the roots that the OCWorkspace commit stores.
 */
internal class BlazeCWorkspaceStorageCustomizer : OCWorkspaceStorageCustomizerAdapter {
  override fun getModuleId(project: Project): ModuleId? {
    val id = ModuleId(BlazeDataStorage.WORKSPACE_MODULE_NAME)
    return if (project.workspaceModel.currentSnapshot.resolve(id) != null) id else null
  }
}

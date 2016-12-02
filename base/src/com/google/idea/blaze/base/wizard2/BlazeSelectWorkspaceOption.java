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
package com.google.idea.blaze.base.wizard2;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import java.io.File;

/** Provides an option on the "Select workspace" screen */
public interface BlazeSelectWorkspaceOption extends BlazeWizardOption {
  /** @return The workspace root that will be created after commit. */
  WorkspaceRoot getWorkspaceRoot();

  /** @return A workspace path resolver to use during wizard validation. */
  WorkspacePathResolver getWorkspacePathResolver();

  /** @return A root directory to use for browsing workspace paths. */
  File getFileBrowserRoot();

  /** @return the name of the workspace. Used to generate default project names. */
  String getWorkspaceName();

  BuildSystem getBuildSystemForWorkspace();

  void commit() throws BlazeProjectCommitException;
}

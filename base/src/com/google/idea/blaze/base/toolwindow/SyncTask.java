/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.toolwindow;

import com.intellij.openapi.project.Project;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Blaze Outputs Tool Window Task for Sync-related operations, and can be hierarchical
 */
public class SyncTask extends Task {

  private final SubType subType;
  private String invocationId;

  public SyncTask(Project project, SubType subType) {
    super(project, subType.displayName, Type.SYNC);
    this.subType = subType;
  }

  public SyncTask(Project project, String name, SubType subType) {
    super(project, name, Type.SYNC);
    this.subType = subType;
  }

  public SyncTask(Project project, String name, @Nullable Task parent, SubType subType) {
    super(project, name, Type.SYNC, parent);
    this.subType = subType;
    if (subType.equals(SubType.BUILD_SHARD)) {
      this.setState(UUID.randomUUID().toString().substring(0, 8) + "...");
    }
  }

  public SubType getSubType() {
    return subType;
  }

  public String getInvocationId() {
    return invocationId;
  }

  public void setInvocationId(String invocationId) {
    this.invocationId = invocationId;
  }

  /** Subtype of the Sync task */
  public enum SubType {
    INCREMENTAL_SYNC("Incremental Sync"),
    STARTUP_SYNC("Startup Sync"),
    IMPORTING("Importing"),
    FULL_SYNC("Full Sync"),
    PARTIAL_SYNC("Partial Sync"),
    INITIAL_DIRECTORY_UPDATE("Initial Directory Update"),
    SYNC("Sync"),
    BUILD_SHARD("Build Shard");

    private final String displayName;

    SubType(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }
}

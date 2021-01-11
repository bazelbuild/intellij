/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

import java.time.Instant;
import java.util.Optional;
import javax.annotation.Nullable;

/** Represents a Blaze Outputs Tool Window Task, which can be hierarchical. */
public final class Task {
  private final String name;
  private final Type type;
  @Nullable private Task parent;
  private String status = "";
  @Nullable private Instant startTime;
  @Nullable private Instant endTime;

  public Task(String name, Type type) {
    this(name, type, null);
  }

  public Task(String name, Type type, @Nullable Task parent) {
    this.name = name;
    this.type = type;
    this.parent = parent;
  }

  String getName() {
    return name;
  }

  Type getType() {
    return type;
  }

  void setStartTime(Instant startTime) {
    this.startTime = startTime;
  }

  void setEndTime(Instant endTime) {
    this.endTime = endTime;
  }

  boolean isFinished() {
    return endTime != null;
  }

  String getStatus() {
    return status;
  }

  void setStatus(String status) {
    this.status = status;
  }

  Optional<Task> getParent() {
    return Optional.ofNullable(parent);
  }

  void setParent(@Nullable Task parent) {
    this.parent = parent;
  }

  Optional<Instant> getStartTime() {
    return Optional.ofNullable(startTime);
  }

  Optional<Instant> getEndTime() {
    return Optional.ofNullable(endTime);
  }

  /** Type of the task. */
  public enum Type {
    // TODO(olegsa) consider merging some categories
    G4_FIX("G4 Fix"),
    G4_LINT("G4 Lint"),
    BUILD_CLEANER("Build Cleaner"),
    FIX_DEPS("Fix Deps"),
    SUGGESTED_FIXES("Suggested Fixes"),
    FAST_BUILD("Fast Build"),
    DEPLOYABLE_JAR("DeployableJar"),
    BLAZE_MAKE("Blaze Make"),
    BLAZE_BEFORE_RUN("Blaze Before Run"),
    BLAZE_SYNC("Blaze Sync");

    private final String displayName;

    Type(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }
}

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

import com.google.common.base.MoreObjects;
import com.intellij.icons.AllIcons;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.text.DateFormatUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** Represents a Blaze Outputs Tool Window Task, which can be hierarchical. */
public final class Task extends PresentableNodeDescriptor<Task> {
  static final Icon NODE_ICON_RUNNING = new AnimatedIcon.Default();
  static final Icon NODE_ICON_OK = AllIcons.RunConfigurations.TestPassed;
  static final Icon NODE_ICON_ERROR = AllIcons.RunConfigurations.TestError;

  private final String name;
  private final Type type;
  @Nullable private Task parent;
  private String state = "";
  @Nullable private Instant startTime;
  @Nullable private Instant endTime;
  private boolean hasErrors;

  /**
   * Creates a new top level task without a parent.
   *
   * @param name name of new task
   * @param type type of new task
   */
  public Task(Project project, String name, Type type) {
    this(project, name, type, null);
  }

  /**
   * Creates a new task. If parent task is provided this new task will be listed in the parent's
   * children.
   *
   * @param name name of new task
   * @param type type of new task
   * @param parent parent of the new task, null if the new task is a top level task.
   */
  public Task(Project project, String name, Type type, @Nullable Task parent) {
    super(project, parent);
    this.name = name;
    this.type = type;
    this.parent = parent;
  }

  @Override
  protected void update(PresentationData presentationData) {
    presentationData.setPresentableText(getName());
    presentationData.addText(getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    if (!getState().isEmpty()) {
      presentationData.addText(" " + getState(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
    }
    if (!isFinished()) {
      presentationData.setIcon(NODE_ICON_RUNNING);
      return;
    }

    boolean hasErrors = getHasErrors();
    presentationData.setIcon(hasErrors ? NODE_ICON_ERROR : NODE_ICON_OK);

    if (getParent().isEmpty()) {
      presentationData.addText(
          hasErrors ? " failed" : " finished", SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);

      getEndTime()
          .map(s -> DateFormatUtil.formatTime(Date.from(s)))
          .ifPresent(
              endDateString ->
                  presentationData.addText(
                      " at " + endDateString, SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES));
    }
  }

  @Override
  public String getName() {
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

  boolean getHasErrors() {
    return hasErrors;
  }

  void setHasErrors(boolean hasErrors) {
    this.hasErrors = hasErrors;
  }

  boolean isFinished() {
    return endTime != null;
  }

  String getState() {
    return state;
  }

  void setState(String state) {
    this.state = state;
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

  Optional<String> getDurationString() {
    Optional<Instant> start = getStartTime();
    return start
        .map(
            startTime ->
                Duration.between(startTime, getEndTime().orElse(Instant.now())).toSeconds())
        .map(
            duration ->
                duration < 1
                    ? getEndTime().isEmpty() ? "0 sec" : "<1 sec"
                    : NlsMessages.formatDuration(duration * 1000));
  }

  @Override
  public Task getElement() {
    return this;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("parent", (parent == null ? null : parent.name))
        .add("startTime", startTime)
        .add("endTime", endTime)
        .toString();
  }

  /** Type of the task. */
  public enum Type {
    FORMAT("Format"),
    LINT("Lint"),
    BUILD_CLEANER("Build Cleaner"),
    FIX_DEPS("Fix Deps"),
    SUGGESTED_FIXES("Suggested Fixes"),
    FAST_BUILD("Fast Build"),
    DEPLOYABLE_JAR("DeployableJar"),
    MAKE("Make"),
    BEFORE_LAUNCH("Before Launch"),
    SYNC("Sync"),
    OTHER("Other");

    private final String displayName;

    Type(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }
}

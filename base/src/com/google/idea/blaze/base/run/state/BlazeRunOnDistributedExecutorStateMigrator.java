/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.state;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.run.DistributedExecutorSupport;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jdom.Element;

/**
 * A temporary class to handle migrating from the previous run configuration settings.
 *
 * <p>In particular, migrates from setting the local execution flag at runtime to including it in
 * the user-visible blaze flags UI field.
 *
 * <p>TODO(brendandouglas): Temporary migration code. Remove in 2017.09.XX+
 */
public class BlazeRunOnDistributedExecutorStateMigrator implements RunConfigurationState {

  private static final String RUN_ON_DISTRIBUTED_EXECUTOR_ATTR =
      "blaze-run-on-distributed-executor";

  private static final String ALREADY_MIGRATED_ATTR = "blaze-dist-executor-migrated";

  private final RunConfigurationFlagsState blazeFlags;
  @Nullable private final DistributedExecutorSupport executorInfo;

  @VisibleForTesting
  public BlazeRunOnDistributedExecutorStateMigrator(
      BuildSystem buildSystem, RunConfigurationFlagsState blazeFlags) {
    this.blazeFlags = blazeFlags;
    executorInfo = DistributedExecutorSupport.getAvailableExecutor(buildSystem);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    if (Boolean.parseBoolean(element.getAttributeValue(ALREADY_MIGRATED_ATTR))) {
      return;
    }
    String string = element.getAttributeValue(RUN_ON_DISTRIBUTED_EXECUTOR_ATTR);
    boolean runOnDistributedExecutor = Boolean.parseBoolean(string);
    if (runOnDistributedExecutor) {
      setDistributedTestExecution();
    }
  }

  private void setDistributedTestExecution() {
    if (executorInfo == null) {
      return;
    }
    List<String> flags = new ArrayList<>(blazeFlags.getRawFlags());
    for (String flag : executorInfo.getBlazeFlags(true)) {
      if (!flags.contains(flag)) {
        flags.add(flag);
      }
    }
    blazeFlags.setRawFlags(flags);
  }

  @Override
  public void writeExternal(Element element) {
    element.removeAttribute(RUN_ON_DISTRIBUTED_EXECUTOR_ATTR);
    element.setAttribute(ALREADY_MIGRATED_ATTR, Boolean.toString(true));
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    // a dummy implementation of RunConfigurationStateEditor.
    return new RunConfigurationStateEditor() {

      @Override
      public void resetEditorFrom(RunConfigurationState state) {}

      @Override
      public void applyEditorTo(RunConfigurationState state) {}

      @Override
      public JComponent createComponent() {
        return new JPanel();
      }

      @Override
      public void setComponentEnabled(boolean enabled) {}
    };
  }
}

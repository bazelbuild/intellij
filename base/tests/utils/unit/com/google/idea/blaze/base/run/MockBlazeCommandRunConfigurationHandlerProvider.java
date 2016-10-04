/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run;

import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerEditor;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.InvalidDataException;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.JComponent;
import org.jdom.Element;

/** A mock {@link BlazeCommandRunConfigurationHandlerProvider}. */
public class MockBlazeCommandRunConfigurationHandlerProvider
    implements BlazeCommandRunConfigurationHandlerProvider {
  @Override
  public boolean canHandleKind(Kind kind) {
    return true;
  }

  @Override
  public BlazeCommandRunConfigurationHandler createHandler(BlazeCommandRunConfiguration config) {
    return new MockBlazeCommandRunConfigurationHandler(config);
  }

  @Override
  public String getId() {
    return "MockBlazeCommandRunConfigurationHandlerProvider";
  }

  /** A mock {@link BlazeCommandRunConfigurationHandler}. */
  private static class MockBlazeCommandRunConfigurationHandler
      implements BlazeCommandRunConfigurationHandler {

    final BlazeCommandRunConfiguration configuration;

    MockBlazeCommandRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
      this.configuration = configuration;
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
      // Don't throw anything.
    }

    @Override
    public void readExternal(Element element) throws InvalidDataException {
      // Don't read anything.
    }

    @Override
    public void writeExternal(Element element) {
      // Don't write anything.
    }

    @Override
    public BlazeCommandRunConfigurationHandler cloneFor(
        BlazeCommandRunConfiguration configuration) {
      return new MockBlazeCommandRunConfigurationHandler(configuration);
    }

    @Override
    public RunProfileState getState(Executor executor, ExecutionEnvironment environment)
        throws ExecutionException {
      return null;
    }

    @Override
    public boolean executeBeforeRunTask(ExecutionEnvironment environment) {
      return true;
    }

    @Nullable
    @Override
    public String suggestedName() {
      return null;
    }

    @Override
    public boolean isGeneratedName(boolean hasGeneratedFlag) {
      return hasGeneratedFlag;
    }

    @Nullable
    @Override
    public String getCommandName() {
      return null;
    }

    @Override
    public String getHandlerName() {
      return "Mock Handler";
    }

    @Override
    @Nullable
    public Icon getExecutorIcon(RunConfiguration configuration, Executor executor) {
      return null;
    }

    @Override
    public BlazeCommandRunConfigurationHandlerEditor getHandlerEditor() {
      return new BlazeCommandRunConfigurationHandlerEditor() {
        @Override
        public void resetEditorFrom(BlazeCommandRunConfigurationHandler handler) {
          // Do nothing.
        }

        @Override
        public void applyEditorTo(BlazeCommandRunConfigurationHandler handler) {
          // Do nothing.
        }

        @Nullable
        @Override
        public JComponent createEditor() {
          return null;
        }
      };
    }
  }
}

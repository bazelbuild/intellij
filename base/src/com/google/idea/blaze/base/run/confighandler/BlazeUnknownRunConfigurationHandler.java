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
package com.google.idea.blaze.base.run.confighandler;

import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.InvalidDataException;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.JComponent;
import org.jdom.Attribute;
import org.jdom.Element;

/**
 * Fallback handler for {@link BlazeCommandRunConfiguration}s with uninitialized targets or unknown
 * handler providers.
 *
 * <p>Cannot be run and provides no editor. Writes all attributes and elements it initially read,
 * except those with names matching existing content written by the configuration itself.
 */
public class BlazeUnknownRunConfigurationHandler implements BlazeCommandRunConfigurationHandler {

  private final BlazeCommandRunConfiguration configuration;

  @Nullable private Element externalElementBackup;

  public BlazeUnknownRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    // No need to throw anything here; BlazeCommandRunConfiguration's
    // check will already detect any config with this handler as invalid
    // because its provider is null and all targets are handled by some provider.
    assert false;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    externalElementBackup = element.clone();
  }

  @Override
  public void writeExternal(Element element) {
    // Write back attributes and elements from externalElementBackup,
    // but take care not to write any which exist in the passed element.
    // Such attributes and elements belong to the configuration, not the handler!
    if (externalElementBackup != null) {
      Set<String> configurationAttributeNames = new HashSet<>();
      for (Attribute attribute : element.getAttributes()) {
        configurationAttributeNames.add(attribute.getName());
      }
      Set<String> configurationElementNames = new HashSet<>();
      for (Element child : element.getChildren()) {
        configurationElementNames.add(child.getName());
      }

      for (Attribute attribute : externalElementBackup.getAttributes()) {
        if (!configurationAttributeNames.contains(attribute.getName())) {
          element.setAttribute(attribute.clone());
        }
      }
      for (Element child : externalElementBackup.getChildren()) {
        if (!configurationElementNames.contains(child.getName())) {
          element.addContent(child.clone());
        }
      }
    }
  }

  @Override
  public BlazeUnknownRunConfigurationHandler cloneFor(BlazeCommandRunConfiguration configuration) {
    return new BlazeUnknownRunConfigurationHandler(configuration);
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
    return String.format(
        "Unknown %s Configuration", Blaze.buildSystemName(configuration.getProject()));
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
    return "no handler";
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
      public void resetEditorFrom(BlazeCommandRunConfigurationHandler handler) {}

      @Override
      public void applyEditorTo(BlazeCommandRunConfigurationHandler handler) {}

      @Override
      @Nullable
      public JComponent createEditor() {
        // Note: currently this will never be displayed,
        // as the handler editor is not shown for invalidated configurations.
        //return new JBLabel("Configuration could not be loaded "
        //    + "because its handler could not be found.");
        return null;
      }
    };
  }
}

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
package com.google.idea.blaze.java.run;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.BlazeRunConfiguration;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.execution.ParametersListUtil;
import org.jdom.Element;

import javax.annotation.Nullable;
import javax.swing.*;
import java.util.List;

/**
 * A run configuration which executes Blaze commands.
 */
public class BlazeCommandRunConfiguration extends LocatableConfigurationBase implements BlazeRunConfiguration {
  private static final String TARGET_TAG = "blaze-target";
  private static final String COMMAND_ATTR = "blaze-command";
  private static final String USER_BLAZE_FLAG_TAG = "blaze-user-flag";
  private static final String USER_EXE_FLAG_TAG = "blaze-user-exe-flag";

  protected final String buildSystemName;

  @Nullable private TargetExpression target;
  @Nullable private BlazeCommandName command;
  private ImmutableList<String> blazeFlags = ImmutableList.of();
  private ImmutableList<String> exeFlags = ImmutableList.of();

  public BlazeCommandRunConfiguration(Project project,
                                      ConfigurationFactory factory,
                                      String name) {
    super(project, factory, name);
    buildSystemName = Blaze.buildSystemName(project);
  }

  @Override
  @Nullable
  public TargetExpression getTarget() {
    return target;
  }

  @Nullable
  public BlazeCommandName getCommand() {
    return command;
  }

  /**
   * @return The list of blaze flags that the user specified manually.
   */
  protected List<String> getBlazeFlags() {
    return blazeFlags;
  }

  /**
   * @return The list of executable flags the user specified manually.
   */
  protected List<String> getExeFlags() {
    return exeFlags;
  }

  /**
   * @return The list of all flags to be used on the Blaze command line for blaze. Subclasses should override this method to add flags for
   * higher-level settings (e.g. "run locally").
   */
  public List<String> getAllBlazeFlags() {
    return getBlazeFlags();
  }

  /**
   * @return The list of all flags to be used for the executable on the Blaze command line. Subclasses should override this method to add
   * flags if desired.
   */
  public List<String> getAllExeFlags() {
    return getExeFlags();
  }

  public void setTarget(@Nullable TargetExpression target) {
    this.target = target;
  }

  public void setCommand(@Nullable BlazeCommandName command) {
    this.command = command;
  }

  public final void setBlazeFlags(List<String> flags) {
    this.blazeFlags = ImmutableList.copyOf(flags);
  }

  public final void setExeFlags(List<String> flags) {
    this.exeFlags = ImmutableList.copyOf(flags);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (target == null) {
      throw new RuntimeConfigurationError(String.format("You must specify a %s target expression.", buildSystemName));
    }
    if (command == null) {
      throw new RuntimeConfigurationError(String.format("You must specify a command.", buildSystemName));
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    // Target is persisted as a tag to permit multiple targets in the future.
    Element targetElement = element.getChild(TARGET_TAG);
    if (targetElement != null && !Strings.isNullOrEmpty(targetElement.getTextTrim())) {
      target = TargetExpression.fromString(targetElement.getTextTrim());
    }
    else {
      target = null;
    }
    String commandString = element.getAttributeValue(COMMAND_ATTR);
    command = Strings.isNullOrEmpty(commandString) ?
              null : BlazeCommandName.fromString(commandString);
    blazeFlags = loadUserFlags(element, USER_BLAZE_FLAG_TAG);
    exeFlags = loadUserFlags(element, USER_EXE_FLAG_TAG);
  }

  private static ImmutableList<String> loadUserFlags(Element root, String tag) {
    ImmutableList.Builder<String> flagsBuilder = ImmutableList.builder();
    for (Element e : root.getChildren(tag)) {
      String flag = e.getTextTrim();
      if (flag != null && !flag.isEmpty()) {
        flagsBuilder.add(flag);
      }
    }
    return flagsBuilder.build();
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    if (target != null) {
      // Target is persisted as a tag to permit multiple targets in the future.
      Element targetElement = new Element(TARGET_TAG);
      targetElement.setText(target.toString());
      element.addContent(targetElement);
    }
    if (command != null) {
      element.setAttribute(COMMAND_ATTR, command.toString());
    }
    saveUserFlags(element, blazeFlags, USER_BLAZE_FLAG_TAG);
    saveUserFlags(element, exeFlags, USER_EXE_FLAG_TAG);
  }

  private static void saveUserFlags(Element root, List<String> flags, String tag) {
    for (String flag : flags) {
      Element child = new Element(tag);
      child.setText(flag);
      root.addContent(child);
    }
  }

  @Override
  public BlazeCommandRunConfiguration clone() {
    final BlazeCommandRunConfiguration configuration = (BlazeCommandRunConfiguration)super.clone();
    configuration.target = target;
    configuration.command = command;
    configuration.blazeFlags = blazeFlags;
    configuration.exeFlags = exeFlags;
    return configuration;
  }

  @Override
  @Nullable
  public String suggestedName() {
    TargetExpression target = getTarget();
    if (!(target instanceof Label)) {
      return null;
    }
    return String.format("%s %s: %s", buildSystemName, command.toString(), ((Label)target).ruleName().toString());
  }


  @Nullable
  @Override
  public BlazeCommandRunProfileState getState(Executor executor,
                                              ExecutionEnvironment environment) throws ExecutionException {
    return new BlazeCommandRunProfileState(environment, executor instanceof DefaultDebugExecutor);
  }

  @Override
  public SettingsEditor<? extends BlazeCommandRunConfiguration> getConfigurationEditor() {
    return new BlazeCommandRunConfigurationSettingsEditor(buildSystemName);
  }

  @VisibleForTesting
  static class BlazeCommandRunConfigurationSettingsEditor extends SettingsEditor<BlazeCommandRunConfiguration> {
    private final String buildSystemName;
    private final JTextField targetField = new JTextField();
    private final ComboBox commandCombo;
    private final JTextArea blazeFlagsField = new JTextArea(5, 0);
    private final JTextArea exeFlagsField = new JTextArea(5, 0);

    public BlazeCommandRunConfigurationSettingsEditor(String buildSystemName) {
      this.buildSystemName = buildSystemName;
      commandCombo = new ComboBox(
        new DefaultComboBoxModel(BlazeCommandName.knownCommands().toArray()));
      // Allow the user to manually specify an unlisted command.
      commandCombo.setEditable(true);
    }

    @VisibleForTesting
    @Override
    public void resetEditorFrom(BlazeCommandRunConfiguration s) {
      targetField.setText(s.target == null ? null : s.target.toString());
      commandCombo.setSelectedItem(s.command);
      blazeFlagsField.setText(ParametersListUtil.join(s.blazeFlags));
      exeFlagsField.setText(ParametersListUtil.join(s.exeFlags));
    }

    @VisibleForTesting
    @Override
    public void applyEditorTo(BlazeCommandRunConfiguration s) throws ConfigurationException {
      String targetString = targetField.getText();
      s.target = Strings.isNullOrEmpty(targetString) ?
                 null : TargetExpression.fromString(targetString);
      Object selectedCommand = commandCombo.getSelectedItem();
      if (selectedCommand instanceof BlazeCommandName) {
        s.command = (BlazeCommandName)selectedCommand;
      }
      else {
        s.command = Strings.isNullOrEmpty((String)selectedCommand) ?
                    null : BlazeCommandName.fromString(selectedCommand.toString());
      }
      s.blazeFlags = ImmutableList.copyOf(ParametersListUtil.parse(Strings.nullToEmpty(blazeFlagsField.getText())));
      s.exeFlags = ImmutableList.copyOf(ParametersListUtil.parse(Strings.nullToEmpty(exeFlagsField.getText())));
    }

    @Override
    protected JComponent createEditor() {
      return UiUtil.createBox(
        new JLabel("Target expression:"),
        targetField,
        new JLabel(buildSystemName + " command:"),
        commandCombo,
        new JLabel(buildSystemName +" flags:"),
        blazeFlagsField,
        new JLabel("Executable flags:"),
        exeFlagsField
      );
    }
  }
}

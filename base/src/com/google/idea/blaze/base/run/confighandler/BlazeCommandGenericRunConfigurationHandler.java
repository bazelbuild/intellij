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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.issueparser.IssueOutputLineProcessor;
import com.google.idea.blaze.base.metrics.Action;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.processhandler.LineProcessingProcessAdapter;
import com.google.idea.blaze.base.run.processhandler.ScopedBlazeProcessHandler;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.IssuesScope;
import com.google.idea.blaze.base.scope.scopes.LoggedTimingScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.execution.ParametersListUtil;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * Generic handler for {@link BlazeCommandRunConfiguration}s, used as a fallback in the case where
 * no other handlers are more relevant.
 */
public class BlazeCommandGenericRunConfigurationHandler
    implements BlazeCommandRunConfigurationHandler {
  private static final String COMMAND_ATTR = "blaze-command";
  private static final String USER_BLAZE_FLAG_TAG = "blaze-user-flag";
  private static final String USER_EXE_FLAG_TAG = "blaze-user-exe-flag";
  private static final String BLAZE_BINARY_TAG = "blaze-binary";

  /** The configuration this handler is for. */
  protected final BlazeCommandRunConfiguration configuration;

  @Nullable private BlazeCommandName command;
  @Nullable private String blazeBinary;
  private ImmutableList<String> blazeFlags = ImmutableList.of();
  private ImmutableList<String> exeFlags = ImmutableList.of();

  public BlazeCommandGenericRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
    this.configuration = configuration;
  }

  protected BlazeCommandGenericRunConfigurationHandler(
      BlazeCommandGenericRunConfigurationHandler other,
      BlazeCommandRunConfiguration configuration) {
    this(configuration);
    command = other.command;
    blazeFlags = other.blazeFlags;
    exeFlags = other.exeFlags;
    blazeBinary = other.blazeBinary;
  }

  @Nullable
  public BlazeCommandName getCommand() {
    return command;
  }

  /** @return The list of blaze flags that the user specified manually. */
  public List<String> getBlazeFlags() {
    return blazeFlags;
  }

  /** @return The list of executable flags the user specified manually. */
  public List<String> getExeFlags() {
    return exeFlags;
  }

  /**
   * @return The list of all flags to be used on the Blaze command line for blaze. Subclasses should
   *     override this method to add flags for higher-level settings (e.g. "run locally").
   */
  public List<String> getAllBlazeFlags() {
    return getBlazeFlags();
  }

  @Nullable
  public String getBlazeBinary() {
    return blazeBinary;
  }

  /**
   * @return The list of all flags to be used for the executable on the Blaze command line.
   *     Subclasses should override this method to add flags if desired.
   */
  public List<String> getAllExeFlags() {
    return getExeFlags();
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

  public void setBlazeBinary(@Nullable String blazeBinary) {
    this.blazeBinary = blazeBinary;
  }

  /** Searches through all blaze flags for the first one beginning with '--test_filter' */
  @Nullable
  public String getTestFilterFlag() {
    for (String flag : getAllBlazeFlags()) {
      if (flag.startsWith(BlazeFlags.TEST_FILTER)) {
        return flag;
      }
    }
    return null;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (command == null) {
      throw new RuntimeConfigurationError("You must specify a command.");
    }
    if (blazeBinary != null && !(new File(blazeBinary).exists())) {
      throw new RuntimeConfigurationError(
          Blaze.buildSystemName(configuration.getProject()) + " binary does not exist");
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    String commandString = element.getAttributeValue(COMMAND_ATTR);
    command =
        Strings.isNullOrEmpty(commandString) ? null : BlazeCommandName.fromString(commandString);
    blazeFlags = loadUserFlags(element, USER_BLAZE_FLAG_TAG);
    exeFlags = loadUserFlags(element, USER_EXE_FLAG_TAG);
    blazeBinary = element.getAttributeValue(BLAZE_BINARY_TAG);
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
  public void writeExternal(Element element) {
    if (command != null) {
      element.setAttribute(COMMAND_ATTR, command.toString());
    }
    saveUserFlags(element, blazeFlags, USER_BLAZE_FLAG_TAG);
    saveUserFlags(element, exeFlags, USER_EXE_FLAG_TAG);
    if (!Strings.isNullOrEmpty(blazeBinary)) {
      element.setAttribute(BLAZE_BINARY_TAG, blazeBinary);
    }
  }

  private static void saveUserFlags(Element root, List<String> flags, String tag) {
    for (String flag : flags) {
      Element child = new Element(tag);
      child.setText(flag);
      root.addContent(child);
    }
  }

  @Override
  public BlazeCommandGenericRunConfigurationHandler cloneFor(
      BlazeCommandRunConfiguration configuration) {
    return new BlazeCommandGenericRunConfigurationHandler(this, configuration);
  }

  @Override
  public RunProfileState getState(Executor executor, ExecutionEnvironment environment) {
    return new BlazeCommandGenericRunConfigurationHandler.BlazeCommandRunProfileState(environment);
  }

  @Override
  public boolean executeBeforeRunTask(ExecutionEnvironment environment) {
    // Don't execute any tasks.
    return true;
  }

  @Override
  @Nullable
  public String suggestedName() {
    if (configuration.getTarget() == null) {
      return null;
    }
    return new BlazeConfigurationNameBuilder(configuration).build();
  }

  @Override
  @Nullable
  public String getCommandName() {
    return command != null ? command.toString() : null;
  }

  @Override
  public boolean isGeneratedName(boolean hasGeneratedFlag) {
    return hasGeneratedFlag;
  }

  @Override
  public String getHandlerName() {
    return "Generic Handler";
  }

  @Override
  @Nullable
  public Icon getExecutorIcon(RunConfiguration configuration, Executor executor) {
    return null;
  }

  @Override
  public BlazeCommandRunConfigurationHandlerEditor getHandlerEditor() {
    return new BlazeCommandGenericRunConfigurationHandler
        .BlazeCommandGenericRunConfigurationHandlerEditor(this);
  }

  /** {@link RunProfileState} for generic blaze commands. */
  private static class BlazeCommandRunProfileState extends CommandLineState {
    private final BlazeCommandRunConfiguration configuration;
    private final BlazeCommandGenericRunConfigurationHandler handler;

    BlazeCommandRunProfileState(ExecutionEnvironment environment) {
      super(environment);
      RunProfile runProfile = environment.getRunProfile();
      configuration = (BlazeCommandRunConfiguration) runProfile;
      handler = (BlazeCommandGenericRunConfigurationHandler) configuration.getHandler();
    }

    @Override
    @NotNull
    protected ProcessHandler startProcess() throws ExecutionException {
      Project project = configuration.getProject();
      BlazeImportSettings importSettings =
          BlazeImportSettingsManager.getInstance(project).getImportSettings();
      assert importSettings != null;

      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      assert projectViewSet != null;

      BlazeCommand blazeCommand =
          BlazeCommand.builder(Blaze.getBuildSystem(project), handler.getCommand())
              .setBlazeBinary(handler.getBlazeBinary())
              .addTargets(configuration.getTarget())
              .addBlazeFlags(BlazeFlags.buildFlags(project, projectViewSet))
              .addBlazeFlags(handler.getAllBlazeFlags())
              .addExeFlags(handler.getAllExeFlags())
              .build();

      WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
      return new ScopedBlazeProcessHandler(
          project,
          blazeCommand,
          workspaceRoot,
          new ScopedBlazeProcessHandler.ScopedProcessHandlerDelegate() {
            @Override
            public void onBlazeContextStart(BlazeContext context) {
              context
                  .push(new LoggedTimingScope(project, Action.BLAZE_COMMAND_USAGE))
                  .push(new IssuesScope(project))
                  .push(new IdeaLogScope());
            }

            @Override
            public ImmutableList<ProcessListener> createProcessListeners(BlazeContext context) {
              LineProcessingOutputStream outputStream =
                  LineProcessingOutputStream.of(
                      new IssueOutputLineProcessor(project, context, workspaceRoot));
              return ImmutableList.of(new LineProcessingProcessAdapter(outputStream));
            }
          });
    }
  }

  /** {@link BlazeCommandRunConfigurationHandlerEditor} for generic blaze commands. */
  static class BlazeCommandGenericRunConfigurationHandlerEditor
      implements BlazeCommandRunConfigurationHandlerEditor {
    private final String buildSystemName;

    private final ComboBox commandCombo;
    private final JTextArea blazeFlagsField = new JTextArea(5, 1);
    private final JTextArea exeFlagsField = new JTextArea(5, 1);
    private final JBTextField blazeBinaryField = new JBTextField(1);

    public BlazeCommandGenericRunConfigurationHandlerEditor(
        BlazeCommandGenericRunConfigurationHandler handler) {
      buildSystemName = Blaze.buildSystemName(handler.configuration.getProject());
      commandCombo =
          new ComboBox(new DefaultComboBoxModel(BlazeCommandName.knownCommands().toArray()));
      // Allow the user to manually specify an unlisted command.
      commandCombo.setEditable(true);
      blazeBinaryField.getEmptyText().setText("(Use global)");
    }

    private static String makeArgString(List<String> arguments) {
      StringBuilder flagString = new StringBuilder();
      for (String flag : arguments) {
        if (flagString.length() > 0) {
          flagString.append('\n');
        }
        if (flag.isEmpty() || flag.contains(" ") || flag.contains("|")) {
          flagString.append('"');
          flagString.append(flag);
          flagString.append('"');
        } else {
          flagString.append(flag);
        }
      }
      return flagString.toString();
    }

    @Override
    public void resetEditorFrom(BlazeCommandRunConfigurationHandler h) {
      BlazeCommandGenericRunConfigurationHandler handler =
          (BlazeCommandGenericRunConfigurationHandler) h;

      commandCombo.setSelectedItem(handler.command);

      // Normally we could just use ParametersListUtils.join, but that will only space-delimit args
      blazeFlagsField.setText(makeArgString(handler.getBlazeFlags()));
      exeFlagsField.setText(makeArgString(handler.getExeFlags()));

      blazeBinaryField.setText(Strings.nullToEmpty(handler.blazeBinary));
    }

    @Override
    public void applyEditorTo(BlazeCommandRunConfigurationHandler h) {
      BlazeCommandGenericRunConfigurationHandler handler =
          (BlazeCommandGenericRunConfigurationHandler) h;
      Object selectedCommand = commandCombo.getSelectedItem();
      if (selectedCommand instanceof BlazeCommandName) {
        handler.command = (BlazeCommandName) selectedCommand;
      } else {
        handler.command =
            Strings.isNullOrEmpty((String) selectedCommand)
                ? null
                : BlazeCommandName.fromString(selectedCommand.toString());
      }
      handler.blazeFlags =
          ImmutableList.copyOf(
              ParametersListUtil.parse(Strings.nullToEmpty(blazeFlagsField.getText())));
      handler.exeFlags =
          ImmutableList.copyOf(
              ParametersListUtil.parse(Strings.nullToEmpty(exeFlagsField.getText())));

      String blazeBinary = blazeBinaryField.getText();
      handler.blazeBinary = Strings.emptyToNull(blazeBinary);
    }

    @Override
    @NotNull
    public JComponent createEditor() {
      return UiUtil.createBox(
          new JLabel(buildSystemName + " command:"),
          commandCombo,
          new JLabel(buildSystemName + " flags:"),
          new JScrollPane(
              blazeFlagsField,
              JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED),
          new JLabel("Executable flags:"),
          new JScrollPane(
              exeFlagsField,
              JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED),
          new JLabel(buildSystemName + " binary:"),
          blazeBinaryField);
    }
  }
}

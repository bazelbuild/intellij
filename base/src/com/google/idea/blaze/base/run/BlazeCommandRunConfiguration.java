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
package com.google.idea.blaze.base.run;

import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.google.idea.blaze.base.run.targetfinder.FuturesUtil;
import com.google.idea.blaze.base.run.targetfinder.TargetFinder;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.ui.UiUtil;
import com.google.idea.sdkcompat.run.RunManagerCompatUtils;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerIconProvider;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletion.StringsCompletionProvider;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import java.util.Collection;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JComponent;
import org.jdom.Element;

/** A run configuration which executes Blaze commands. */
public class BlazeCommandRunConfiguration extends LocatableConfigurationBase
    implements BlazeRunConfiguration,
        RunnerIconProvider,
        ModuleRunProfile,
        RunConfigurationWithSuppressedDefaultDebugAction {

  private static final Logger logger = Logger.getInstance(BlazeCommandRunConfiguration.class);

  /**
   * Attributes or tags which are common to all run configuration types. We don't want to interfere
   * with the (de)serialization of these.
   *
   * <p>TODO(brendandouglas): remove once we are fully migrated to serializing blaze-specific
   * settings under a common parent.
   */
  private static final ImmutableSet<String> COMMON_SETTINGS =
      ImmutableSet.of(
          "name",
          "nameIsGenerated",
          "default",
          "temporary",
          "method",
          "type",
          "factoryName",
          "selected",
          "option",
          "folderName",
          "editBeforeRun",
          "activateToolWindowBeforeRun",
          "tempConfiguration");

  /**
   * All blaze-specific settings are serialized under this tag, to distinguish them from common
   * settings.
   */
  private static final String BLAZE_SETTINGS_TAG = "blaze-settings";

  private static final String HANDLER_ATTR = "handler-id";
  private static final String TARGET_TAG = "blaze-target";
  private static final String KIND_ATTR = "kind";
  private static final String KEEP_IN_SYNC_TAG = "keep-in-sync";

  /** The blaze-specific parts of the last serialized state of the configuration. */
  private Element blazeElementState = new Element(BLAZE_SETTINGS_TAG);

  @Nullable private String targetPattern;
  // Null if the target is null, not a Label, or not a known rule.
  @Nullable private Kind targetKind;

  // for keeping imported configurations in sync with their source XML
  @Nullable private Boolean keepInSync = null;

  private BlazeCommandRunConfigurationHandlerProvider handlerProvider;
  private BlazeCommandRunConfigurationHandler handler;

  public BlazeCommandRunConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(project, factory, name);
    // start with whatever fallback is present
    handlerProvider = BlazeCommandRunConfigurationHandlerProvider.findHandlerProvider(null);
    handler = handlerProvider.createHandler(this);
    try {
      handler.getState().readExternal(blazeElementState);
    } catch (InvalidDataException e) {
      logger.error(e);
    }
  }

  /** @return The configuration's {@link BlazeCommandRunConfigurationHandler}. */
  public BlazeCommandRunConfigurationHandler getHandler() {
    return handler;
  }

  /**
   * Gets the configuration's handler's {@link RunConfigurationState} if it is an instance of the
   * given class; otherwise returns null.
   */
  @Nullable
  public <T extends RunConfigurationState> T getHandlerStateIfType(Class<T> type) {
    RunConfigurationState handlerState = handler.getState();
    if (type.isInstance(handlerState)) {
      return type.cast(handlerState);
    } else {
      return null;
    }
  }

  @Override
  public void setKeepInSync(@Nullable Boolean keepInSync) {
    this.keepInSync = keepInSync;
  }

  @Override
  @Nullable
  public Boolean getKeepInSync() {
    return keepInSync;
  }

  @Override
  @Nullable
  public TargetExpression getTarget() {
    return parseTarget(targetPattern);
  }

  public void setTargetInfo(TargetInfo target) {
    targetPattern = target.label.toString().trim();
    updateTargetKind(target);
  }

  /** Sets the target expression and asynchronously kicks off a target kind update. */
  public void setTarget(@Nullable TargetExpression target) {
    targetPattern = target != null ? target.toString().trim() : null;
    updateTargetKindAsync(null);
  }

  private void updateHandler() {
    BlazeCommandRunConfigurationHandlerProvider handlerProvider =
        BlazeCommandRunConfigurationHandlerProvider.findHandlerProvider(targetKind);
    updateHandlerIfDifferentProvider(handlerProvider);
  }

  private void updateHandlerIfDifferentProvider(
      BlazeCommandRunConfigurationHandlerProvider newProvider) {
    if (handlerProvider == newProvider) {
      return;
    }
    try {
      handler.getState().writeExternal(blazeElementState);
    } catch (WriteExternalException e) {
      logger.error(e);
    }
    handlerProvider = newProvider;
    handler = newProvider.createHandler(this);
    try {
      handler.getState().readExternal(blazeElementState);
    } catch (InvalidDataException e) {
      logger.error(e);
    }
  }

  @Nullable
  private static TargetExpression parseTarget(@Nullable String targetPattern) {
    return targetPattern != null ? TargetExpression.fromStringSafe(targetPattern) : null;
  }

  /**
   * Returns the {@link Kind} of the single blaze target corresponding to the configuration's target
   * expression, if it's currently known. Returns null if the target expression points to multiple
   * blaze targets.
   */
  @Nullable
  public Kind getTargetKind() {
    return targetKind;
  }

  /**
   * Queries the kind of the current target pattern, possibly asynchronously.
   *
   * @param asyncCallback if the kind is updated asynchronously, this will be run after the kind is
   *     updated. If it's updated synchronously, this will not be run.
   */
  void updateTargetKindAsync(@Nullable Runnable asyncCallback) {
    TargetExpression expr = parseTarget(targetPattern);
    if (!(expr instanceof Label)) {
      updateTargetKind(null);
      return;
    }
    Label label = (Label) expr;
    ListenableFuture<TargetInfo> future = TargetFinder.findTargetInfoFuture(getProject(), label);
    if (future.isDone()) {
      updateTargetKind(FuturesUtil.getIgnoringErrors(future));
    } else {
      updateTargetKind(null);
      future.addListener(
          () -> {
            updateTargetKind(FuturesUtil.getIgnoringErrors(future));
            if (asyncCallback != null) {
              asyncCallback.run();
            }
          },
          MoreExecutors.directExecutor());
    }
  }

  private void updateTargetKind(@Nullable TargetInfo targetInfo) {
    targetKind = targetInfo != null ? targetInfo.getKind() : null;
    updateHandler();
  }

  /**
   * @return The {@link Kind} name, if the target is a known rule. Otherwise, "target pattern" if it
   *     is a general {@link TargetExpression}, "unknown rule" if it is a {@link Label} without a
   *     known rule, and "unknown target" if there is no target.
   */
  private String getTargetKindName() {
    Kind kind = targetKind;
    if (kind != null) {
      return kind.toString();
    }

    TargetExpression target = parseTarget(targetPattern);
    if (target instanceof Label) {
      return "unknown rule";
    } else if (target != null) {
      return "target pattern";
    } else {
      return "unknown target";
    }
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    // Our handler check is not valid when we don't have BlazeProjectData.
    if (BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData() == null) {
      throw new RuntimeConfigurationError(
          "Configuration cannot be run until project has been synced.");
    }
    if (Strings.isNullOrEmpty(targetPattern)) {
      throw new RuntimeConfigurationError(
          String.format(
              "You must specify a %s target expression.", Blaze.buildSystemName(getProject())));
    }
    if (!targetPattern.startsWith("//")) {
      throw new RuntimeConfigurationError(
          "You must specify the full target expression, starting with //");
    }
    String error = TargetExpression.validate(targetPattern);
    if (error != null) {
      throw new RuntimeConfigurationError(error);
    }
    for (BeforeRunTask task : RunManagerCompatUtils.getBeforeRunTasks(this)) {
      if (task.getProviderId().equals(BlazeBeforeRunTaskProvider.ID) && !task.isEnabled()) {
        throw new RuntimeConfigurationError(
            String.format(
                "Invalid run configuration: the %s before run task is missing. Please re-run sync "
                    + "to add it back",
                Blaze.buildSystemName(getProject())));
      }
    }
    handler.checkConfiguration();
  }

  private static Element getBlazeSettingsCopy(Element element) {
    Element blazeSettings = element.getChild(BLAZE_SETTINGS_TAG);
    if (blazeSettings != null) {
      return blazeSettings.clone();
    }
    // migrate an old-style run configuration
    blazeSettings = element.clone();
    blazeSettings.setName(BLAZE_SETTINGS_TAG);
    for (String common : COMMON_SETTINGS) {
      blazeSettings.removeChildren(common);
      blazeSettings.removeAttribute(common);
    }
    return blazeSettings;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    element = getBlazeSettingsCopy(element);

    String keepInSyncString = element.getAttributeValue(KEEP_IN_SYNC_TAG);
    keepInSync = keepInSyncString != null ? Boolean.parseBoolean(keepInSyncString) : null;

    // Target is persisted as a tag to permit multiple targets in the future.
    Element targetElement = element.getChild(TARGET_TAG);
    if (targetElement != null && !Strings.isNullOrEmpty(targetElement.getTextTrim())) {
      targetPattern = targetElement.getTextTrim();
      targetKind = Kind.fromString(targetElement.getAttributeValue(KIND_ATTR));
    }
    // Because BlazeProjectData is not available when configurations are loading,
    // we can't call setTarget and have it find the appropriate handler provider.
    // So instead, we use the stored provider ID.
    String providerId = element.getAttributeValue(HANDLER_ATTR);
    BlazeCommandRunConfigurationHandlerProvider handlerProvider =
        BlazeCommandRunConfigurationHandlerProvider.getHandlerProvider(providerId);
    if (handlerProvider != null) {
      updateHandlerIfDifferentProvider(handlerProvider);
    }

    blazeElementState = element;
    handler.getState().readExternal(blazeElementState);
  }

  @Override
  @SuppressWarnings("ThrowsUncheckedException")
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);

    blazeElementState.removeChildren(TARGET_TAG);
    if (targetPattern != null) {
      Element targetElement = new Element(TARGET_TAG);
      targetElement.setText(targetPattern);
      if (targetKind != null) {
        targetElement.setAttribute(KIND_ATTR, targetKind.toString());
      }
      blazeElementState.addContent(targetElement);
    }
    if (keepInSync != null) {
      blazeElementState.setAttribute(KEEP_IN_SYNC_TAG, Boolean.toString(keepInSync));
    } else {
      blazeElementState.removeAttribute(KEEP_IN_SYNC_TAG);
    }
    blazeElementState.setAttribute(HANDLER_ATTR, handlerProvider.getId());

    handler.getState().writeExternal(blazeElementState);
    // copy our internal state to the provided Element
    element.addContent(blazeElementState.clone());
  }

  @Override
  public BlazeCommandRunConfiguration clone() {
    final BlazeCommandRunConfiguration configuration = (BlazeCommandRunConfiguration) super.clone();
    configuration.blazeElementState = blazeElementState.clone();
    configuration.targetPattern = targetPattern;
    configuration.targetKind = targetKind;
    configuration.keepInSync = keepInSync;
    configuration.handlerProvider = handlerProvider;
    configuration.handler = handlerProvider.createHandler(this);
    try {
      configuration.handler.getState().readExternal(configuration.blazeElementState);
    } catch (InvalidDataException e) {
      logger.error(e);
    }

    return configuration;
  }

  @Override
  @Nullable
  public RunProfileState getState(Executor executor, ExecutionEnvironment environment)
      throws ExecutionException {
    BlazeCommandRunConfigurationRunner runner = handler.createRunner(executor, environment);
    if (runner != null) {
      environment.putCopyableUserData(BlazeCommandRunConfigurationRunner.RUNNER_KEY, runner);
      return runner.getRunProfileState(executor, environment);
    }
    return null;
  }

  @Override
  @Nullable
  public String suggestedName() {
    return handler.suggestedName(this);
  }

  @Override
  @Nullable
  public Icon getExecutorIcon(RunConfiguration configuration, Executor executor) {
    return handler.getExecutorIcon(configuration, executor);
  }

  @Override
  public SettingsEditor<? extends BlazeCommandRunConfiguration> getConfigurationEditor() {
    return new BlazeCommandRunConfigurationSettingsEditor(this);
  }

  @Override
  public Module[] getModules() {
    return new Module[0];
  }

  static class BlazeCommandRunConfigurationSettingsEditor
      extends SettingsEditor<BlazeCommandRunConfiguration> {

    private BlazeCommandRunConfigurationHandlerProvider handlerProvider;
    private BlazeCommandRunConfigurationHandler handler;
    private RunConfigurationStateEditor handlerStateEditor;
    private JComponent handlerStateComponent;
    private Element elementState;

    private final Box editorWithoutSyncCheckBox;
    private final Box editor;
    private final JBCheckBox keepInSyncCheckBox;
    private final JBLabel targetExpressionLabel;
    private final TextFieldWithAutoCompletion<String> targetField;

    BlazeCommandRunConfigurationSettingsEditor(BlazeCommandRunConfiguration config) {
      Project project = config.getProject();
      targetField =
          new TextFieldWithAutoCompletion<>(
              project, new TargetCompletionProvider(project), true, null);
      elementState = config.blazeElementState.clone();
      targetExpressionLabel = new JBLabel(UIUtil.ComponentStyle.LARGE);
      keepInSyncCheckBox = new JBCheckBox("Keep in sync with source XML");
      editorWithoutSyncCheckBox = UiUtil.createBox(targetExpressionLabel, targetField);
      editor = UiUtil.createBox(editorWithoutSyncCheckBox, keepInSyncCheckBox);
      updateEditor(config);
      updateHandlerEditor(config);
      keepInSyncCheckBox.addItemListener(e -> updateEnabledStatus());
    }

    private void updateEditor(BlazeCommandRunConfiguration config) {
      targetExpressionLabel.setText(
          String.format(
              "Target expression (%s handled by %s):",
              config.getTargetKindName(), config.handler.getHandlerName()));
      keepInSyncCheckBox.setVisible(config.keepInSync != null);
      if (config.keepInSync != null) {
        keepInSyncCheckBox.setSelected(config.keepInSync);
      }
      updateEnabledStatus();
    }

    private void updateEnabledStatus() {
      setEnabled(!keepInSyncCheckBox.isVisible() || !keepInSyncCheckBox.isSelected());
    }

    private void setEnabled(boolean enabled) {
      if (handlerStateEditor != null) {
        handlerStateEditor.setComponentEnabled(enabled);
      }
      targetField.setEnabled(enabled);
    }

    private void updateHandlerEditor(BlazeCommandRunConfiguration config) {
      handlerProvider = config.handlerProvider;
      handler = handlerProvider.createHandler(config);
      try {
        handler.getState().readExternal(config.blazeElementState);
      } catch (InvalidDataException e) {
        logger.error(e);
      }
      handlerStateEditor = handler.getState().getEditor(config.getProject());

      if (handlerStateComponent != null) {
        editorWithoutSyncCheckBox.remove(handlerStateComponent);
      }
      handlerStateComponent = handlerStateEditor.createComponent();
      editorWithoutSyncCheckBox.add(handlerStateComponent);
    }

    @Override
    protected JComponent createEditor() {
      return editor;
    }

    @Override
    protected void resetEditorFrom(BlazeCommandRunConfiguration config) {
      elementState = config.blazeElementState.clone();
      updateEditor(config);
      if (config.handlerProvider != handlerProvider) {
        updateHandlerEditor(config);
      }
      targetField.setText(config.targetPattern);
      handlerStateEditor.resetEditorFrom(config.handler.getState());
    }

    @Override
    protected void applyEditorTo(BlazeCommandRunConfiguration config) {
      handlerStateEditor.applyEditorTo(handler.getState());
      try {
        handler.getState().writeExternal(elementState);
      } catch (WriteExternalException e) {
        logger.error(e);
      }
      config.keepInSync = keepInSyncCheckBox.isVisible() ? keepInSyncCheckBox.isSelected() : null;

      // now set the config's state, based on the editor's (possibly out of date) handler
      config.updateHandlerIfDifferentProvider(handlerProvider);
      config.blazeElementState = elementState.clone();
      try {
        config.handler.getState().readExternal(config.blazeElementState);
      } catch (InvalidDataException e) {
        logger.error(e);
      }

      // finally, update the handler
      config.targetPattern = Strings.emptyToNull(targetField.getText().trim());
      config.updateTargetKindAsync(() -> UIUtil.invokeLaterIfNeeded(this::fireEditorStateChanged));
      updateEditor(config);
      if (config.handlerProvider != handlerProvider) {
        updateHandlerEditor(config);
        handlerStateEditor.resetEditorFrom(config.handler.getState());
      } else {
        handlerStateEditor.applyEditorTo(config.handler.getState());
      }
    }
  }

  private static class TargetCompletionProvider extends StringsCompletionProvider {
    TargetCompletionProvider(Project project) {
      super(getTargets(project), null);
    }

    private static Collection<String> getTargets(Project project) {
      BlazeProjectData projectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      BlazeImportSettings importSettings =
          BlazeImportSettingsManager.getInstance(project).getImportSettings();
      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      if (projectData == null || importSettings == null || projectViewSet == null) {
        return ImmutableList.of();
      }
      ImportRoots importRoots =
          ImportRoots.builder(
                  WorkspaceRoot.fromImportSettings(importSettings), importSettings.getBuildSystem())
              .add(projectViewSet)
              .build();
      return projectData
          .targetMap
          .targets()
          .stream()
          .filter(TargetIdeInfo::isPlainTarget)
          .filter(target -> importRoots.importAsSource(target.key.label))
          .map(target -> target.key.label.toString())
          .collect(toList());
    }
  }
}

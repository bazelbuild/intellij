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

import com.google.common.base.Strings;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.rulefinder.RuleFinder;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerIconProvider;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.UIUtil;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JComponent;
import org.jdom.Attribute;
import org.jdom.Element;

/** A run configuration which executes Blaze commands. */
public class BlazeCommandRunConfiguration extends LocatableConfigurationBase
    implements BlazeRunConfiguration, RunnerIconProvider {
  private static final Logger LOG = Logger.getInstance(BlazeCommandRunConfiguration.class);

  private static final String HANDLER_ATTR = "handler-id";
  private static final String TARGET_TAG = "blaze-target";
  private static final String KIND_ATTR = "kind";

  /** The last serialized state of the configuration. */
  private Element elementState = new Element("dummy");

  @Nullable private TargetExpression target;
  // Null if the target is null, not a Label, or not a known rule.
  @Nullable private Kind targetKind;
  private BlazeCommandRunConfigurationHandlerProvider handlerProvider;
  private BlazeCommandRunConfigurationHandler handler;

  public BlazeCommandRunConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(project, factory, name);
    // start with whatever fallback is present
    handlerProvider = BlazeCommandRunConfigurationHandlerProvider.findHandlerProvider(null);
    handler = handlerProvider.createHandler(this);
    try {
      handler.getState().readExternal(elementState);
    } catch (InvalidDataException e) {
      LOG.error(e);
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
  @Nullable
  public TargetExpression getTarget() {
    return target;
  }

  public void setTarget(@Nullable TargetExpression target) {
    this.target = target;
    targetKind = getKindForTarget();

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
      handler.getState().writeExternal(elementState);
    } catch (WriteExternalException e) {
      LOG.error(e);
    }
    handlerProvider = newProvider;
    handler = newProvider.createHandler(this);
    try {
      handler.getState().readExternal(elementState);
    } catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  /**
   * Returns the {@link Kind} of the single blaze target corresponding to the configuration's target
   * expression, if it can be determined. Returns null if the target expression points to multiple
   * blaze targets.
   */
  @Nullable
  public Kind getKindForTarget() {
    if (target instanceof Label) {
      RuleIdeInfo rule = RuleFinder.getInstance().ruleForTarget(getProject(), (Label) target);
      return rule != null ? rule.kind : null;
    }
    return null;
  }

  /**
   * @return The {@link Kind} name, if the target is a known rule. Otherwise, "target pattern" if it
   *     is a general {@link TargetExpression}, "unknown rule" if it is a {@link Label} without a
   *     known rule, and "unknown target" if there is no target.
   */
  public String getTargetKindName() {
    Kind kind = getKindForTarget();
    if (kind != null) {
      return kind.toString();
    } else if (target instanceof Label) {
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
    if (target == null) {
      throw new RuntimeConfigurationError(
          String.format(
              "You must specify a %s target expression.", Blaze.buildSystemName(getProject())));
    }
    if (!target.toString().startsWith("//")) {
      throw new RuntimeConfigurationError(
          "You must specify the full target expression, starting with //");
    }
    handler.checkConfiguration();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    element = element.clone();

    // Target is persisted as a tag to permit multiple targets in the future.
    Element targetElement = element.getChild(TARGET_TAG);
    if (targetElement != null && !Strings.isNullOrEmpty(targetElement.getTextTrim())) {
      target = TargetExpression.fromString(targetElement.getTextTrim());
      targetKind = Kind.fromString(targetElement.getAttributeValue(KIND_ATTR));
    } else {
      // Legacy: Added in 1.9 to support reading target as an attribute so
      // BlazeAndroid(Binary/Test)RunConfiguration elements can be read.
      // TODO remove in 2.1 once BlazeAndroidBinaryRunConfigurationType and
      // BlazeAndroidTestRunConfigurationType have been removed.
      String targetString = element.getAttributeValue(TARGET_TAG);
      target = targetString != null ? TargetExpression.fromString(targetString) : null;
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

    element.removeAttribute(KIND_ATTR);
    element.removeAttribute(HANDLER_ATTR);
    element.removeChildren(TARGET_TAG);
    // remove legacy attribute, if present
    element.removeAttribute(TARGET_TAG);

    this.elementState = element;
    handler.getState().readExternal(elementState);
  }

  @Override
  @SuppressWarnings("ThrowsUncheckedException")
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    if (target != null) {
      Element targetElement = new Element(TARGET_TAG);
      targetElement.setText(target.toString());
      if (targetKind != null) {
        targetElement.setAttribute(KIND_ATTR, targetKind.toString());
      }
      element.addContent(targetElement);
    }
    element.setAttribute(HANDLER_ATTR, handlerProvider.getId());
    handler.getState().writeExternal(elementState);

    // copy our internal state to the provided Element, skipping items already present
    Set<String> baseAttributes =
        element.getAttributes().stream().map(Attribute::getName).collect(Collectors.toSet());
    for (Attribute attribute : elementState.getAttributes()) {
      if (!baseAttributes.contains(attribute.getName())) {
        element.setAttribute(attribute.clone());
      }
    }
    Set<String> baseChildren =
        element.getChildren().stream().map(Element::getName).collect(Collectors.toSet());
    for (Element child : elementState.getChildren()) {
      if (!baseChildren.contains(child.getName())) {
        element.addContent(child.clone());
      }
    }
  }

  @Override
  public BlazeCommandRunConfiguration clone() {
    final BlazeCommandRunConfiguration configuration = (BlazeCommandRunConfiguration) super.clone();
    configuration.elementState = elementState.clone();
    configuration.target = target;
    configuration.targetKind = targetKind;
    configuration.handlerProvider = handlerProvider;
    configuration.handler = handlerProvider.createHandler(this);
    try {
      configuration.handler.getState().readExternal(configuration.elementState);
    } catch (InvalidDataException e) {
      LOG.error(e);
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

  static class BlazeCommandRunConfigurationSettingsEditor
      extends SettingsEditor<BlazeCommandRunConfiguration> {

    private BlazeCommandRunConfigurationHandlerProvider handlerProvider;
    private BlazeCommandRunConfigurationHandler handler;
    private RunConfigurationStateEditor handlerStateEditor;
    private JComponent handlerStateComponent;
    private Element elementState;

    private final Box editor;
    private final JBLabel targetExpressionLabel;
    private final JBTextField targetField = new JBTextField(1);

    BlazeCommandRunConfigurationSettingsEditor(BlazeCommandRunConfiguration config) {
      elementState = config.elementState.clone();
      targetExpressionLabel = new JBLabel(UIUtil.ComponentStyle.LARGE);
      editor = UiUtil.createBox(targetExpressionLabel, targetField);
      targetField.getEmptyText().setText("Full target expression starting with //");
      updateTargetExpressionLabel(config);
      updateHandlerEditor(config);
    }

    private void updateTargetExpressionLabel(BlazeCommandRunConfiguration config) {
      targetExpressionLabel.setText(
          String.format(
              "Target expression (%s handled by %s):",
              config.getTargetKindName(), config.handler.getHandlerName()));
    }

    private void updateHandlerEditor(BlazeCommandRunConfiguration config) {
      handlerProvider = config.handlerProvider;
      handler = handlerProvider.createHandler(config);
      try {
        handler.getState().readExternal(config.elementState);
      } catch (InvalidDataException e) {
        LOG.error(e);
      }
      handlerStateEditor = handler.getState().getEditor(config.getProject());

      if (handlerStateComponent != null) {
        editor.remove(handlerStateComponent);
      }
      handlerStateComponent = handlerStateEditor.createComponent();
      editor.add(handlerStateComponent);
    }

    @Override
    protected JComponent createEditor() {
      return editor;
    }

    @Override
    protected void resetEditorFrom(BlazeCommandRunConfiguration config) {
      elementState = config.elementState.clone();
      updateTargetExpressionLabel(config);
      if (config.handlerProvider != handlerProvider) {
        updateHandlerEditor(config);
      }
      targetField.setText(config.target == null ? null : config.target.toString());
      handlerStateEditor.resetEditorFrom(config.handler.getState());
    }

    @Override
    protected void applyEditorTo(BlazeCommandRunConfiguration config) {
      // update the editor's elementState
      handlerStateEditor.applyEditorTo(handler.getState());
      try {
        handler.getState().writeExternal(elementState);
      } catch (WriteExternalException e) {
        LOG.error(e);
      }

      // now set the config's state, based on the editor's (possibly out of date) handler
      config.updateHandlerIfDifferentProvider(handlerProvider);
      config.elementState = elementState.clone();
      try {
        config.handler.getState().readExternal(config.elementState);
      } catch (InvalidDataException e) {
        LOG.error(e);
      }

      // finally, update the handler
      String targetString = targetField.getText();
      config.setTarget(
          Strings.isNullOrEmpty(targetString) ? null : TargetExpression.fromString(targetString));
      updateTargetExpressionLabel(config);
      if (config.handlerProvider != handlerProvider) {
        updateHandlerEditor(config);
        handlerStateEditor.resetEditorFrom(config.handler.getState());
      } else {
        handlerStateEditor.applyEditorTo(config.handler.getState());
      }
    }
  }
}

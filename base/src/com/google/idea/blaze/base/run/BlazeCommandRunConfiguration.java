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
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerEditor;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider;
import com.google.idea.blaze.base.run.confighandler.BlazeUnknownRunConfigurationHandler;
import com.google.idea.blaze.base.run.rulefinder.RuleFinder;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
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
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JComponent;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/** A run configuration which executes Blaze commands. */
public class BlazeCommandRunConfiguration extends LocatableConfigurationBase
    implements BlazeRunConfiguration, RunnerIconProvider {
  private static final Logger LOG = Logger.getInstance(BlazeCommandRunConfiguration.class);

  private static final String HANDLER_ATTR = "handler-id";
  private static final String TARGET_TAG = "blaze-target";
  private static final String KIND_ATTR = "kind";

  // Null for configurations created since restart.
  @Nullable private Element externalElementBackup;
  // Null when there is no target.
  @Nullable private TargetExpression target;
  // Null if the target is null, not a Label, or not a known rule.
  @Nullable private Kind targetKind;
  private BlazeCommandRunConfigurationHandler handler;
  // Null if the handler is BlazeUnknownRunConfigurationHandler.
  @Nullable private BlazeCommandRunConfigurationHandlerProvider handlerProvider;

  public BlazeCommandRunConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(project, factory, name);
    handler = new BlazeUnknownRunConfigurationHandler(this);
  }

  /** @return The configuration's {@link BlazeCommandRunConfigurationHandler}. */
  @NotNull
  public BlazeCommandRunConfigurationHandler getHandler() {
    return handler;
  }

  /**
   * Gets the configuration's {@link BlazeCommandRunConfigurationHandler} if it is an instance of
   * the given class; otherwise returns null.
   */
  @Nullable
  public <T extends BlazeCommandRunConfigurationHandler> T getHandlerIfType(Class<T> type) {
    if (type.isInstance(handler)) {
      return type.cast(handler);
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
    RuleIdeInfo rule = getRuleForTarget();
    targetKind = rule != null ? rule.kind : null;

    BlazeCommandRunConfigurationHandlerProvider handlerProvider =
        BlazeCommandRunConfigurationHandlerProvider.findHandlerProvider(targetKind);
    setHandlerIfDifferentProvider(handlerProvider);
  }

  private void setHandlerIfDifferentProvider(
      BlazeCommandRunConfigurationHandlerProvider newProvider) {
    // Only change the handler if the provider has changed.
    if (handlerProvider != newProvider) {
      handlerProvider = newProvider;
      handler = newProvider.createHandler(this);
    }
  }

  /**
   * Returns the single blaze target corresponding to the configuration's target expression, if one
   * exists. Returns null if the target expression points to multiple blaze targets, or wasn't
   * included in the latest sync.
   */
  @Nullable
  public RuleIdeInfo getRuleForTarget() {
    if (target instanceof Label) {
      return RuleFinder.getInstance().ruleForTarget(getProject(), (Label) target);
    }
    return null;
  }

  /**
   * @return The {@link Kind} name, if the target is a known rule. Otherwise, "target pattern" if it
   *     is a general {@link TargetExpression}, "unknown rule" if it is a {@link Label} without a
   *     known rule, and "unknown target" if there is no target.
   */
  public String getTargetKindName() {
    RuleIdeInfo rule = getRuleForTarget();
    if (rule != null) {
      return rule.kind.toString();
    } else if (target instanceof Label) {
      return "unknown rule";
    } else if (target != null) {
      return "target pattern";
    } else {
      return "unknown target";
    }
  }

  // TODO This method can be private after BlazeCommandRunConfigurationUpdater is removed.
  void loadExternalElementBackup() {
    if (externalElementBackup != null) {
      try {
        handler.readExternal(externalElementBackup);
      } catch (InvalidDataException e) {
        // This is what IntelliJ does when getting this exception while loading a configuration.
        LOG.error(e);
      }
    }
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    // Our handler check and its quick fix are not valid when we don't have BlazeProjectData.
    if (BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData() == null) {
      throw new RuntimeConfigurationError(
          "Configuration cannot be used or modified while project is syncing.");
    }
    if (isConfigurationInvalidated()) {
      throw new RuntimeConfigurationError(
          "A property of the target unexpectedly changed. The configuration must be updated. "
              + "Some configuration settings may be lost.",
          () -> {
            BlazeCommandRunConfigurationHandler oldHandler = handler;
            setTarget(target);
            if (handler != oldHandler) {
              loadExternalElementBackup();
            }
          });
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

  private boolean isConfigurationInvalidated() {
    boolean configurationInvalidated = handler instanceof BlazeUnknownRunConfigurationHandler;
    if (!configurationInvalidated) {
      RuleIdeInfo rule = getRuleForTarget();
      Kind expectedKind = rule != null ? rule.kind : null;
      configurationInvalidated = targetKind != expectedKind;
    }
    if (!configurationInvalidated) {
      configurationInvalidated =
          handlerProvider
              != BlazeCommandRunConfigurationHandlerProvider.findHandlerProvider(targetKind);
    }
    return configurationInvalidated;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    externalElementBackup = element.clone();
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
      String targetString =
          element.getAttributeValue(
              TARGET_TAG); // The attribute ID happens to be identical to the tag ID.
      if (targetString != null) {
        target = TargetExpression.fromString(targetString);
        // Once the above is removed, 'target = null;' should be
        // the only thing in the outer else clause.
      } else {
        target = null;
      }
    }
    // Because BlazeProjectData is not available when configurations are loading,
    // we can't call setTarget and have it find the appropriate handler provider.
    // So instead, we use the stored provider ID.
    String providerId = element.getAttributeValue(HANDLER_ATTR);
    BlazeCommandRunConfigurationHandlerProvider handlerProvider =
        BlazeCommandRunConfigurationHandlerProvider.getHandlerProvider(providerId);
    if (handlerProvider != null) {
      setHandlerIfDifferentProvider(handlerProvider);
    }
    handler.readExternal(element);
  }

  @Override
  @SuppressWarnings("ThrowsUncheckedException")
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    // We can't write externalElementBackup contents; doing so would cause the configuration
    // xml to retain duplicate elements and grow across reopenings.
    // We also can't use the approach in BlazeUnknownRunConfigurationHandler;
    // this can revive intentionally deleted attributes/elements such as user flags.
    if (target != null) {
      // Target is persisted as a tag to permit multiple targets in the future.
      Element targetElement = new Element(TARGET_TAG);
      targetElement.setText(target.toString());
      if (targetKind != null) {
        targetElement.setAttribute(KIND_ATTR, targetKind.toString());
      }
      element.addContent(targetElement);
    }
    if (handlerProvider != null) {
      element.setAttribute(HANDLER_ATTR, handlerProvider.getId());
    }
    handler.writeExternal(element);
  }

  @Override
  public BlazeCommandRunConfiguration clone() {
    final BlazeCommandRunConfiguration configuration = (BlazeCommandRunConfiguration) super.clone();
    if (externalElementBackup != null) {
      configuration.externalElementBackup = externalElementBackup.clone();
    }
    configuration.target = target;
    configuration.targetKind = targetKind;
    configuration.handler = handler.cloneFor(configuration);
    configuration.handlerProvider = handlerProvider;
    return configuration;
  }

  @Override
  @Nullable
  public RunProfileState getState(
      @NotNull Executor executor, @NotNull ExecutionEnvironment environment)
      throws ExecutionException {
    return handler.getState(executor, environment);
  }

  @Override
  @Nullable
  public String suggestedName() {
    return handler.suggestedName();
  }

  @Override
  public boolean isGeneratedName() {
    return handler.isGeneratedName(super.isGeneratedName());
  }

  @Override
  @Nullable
  public Icon getExecutorIcon(@NotNull RunConfiguration configuration, @NotNull Executor executor) {
    return handler.getExecutorIcon(configuration, executor);
  }

  @Override
  @NotNull
  public SettingsEditor<? extends BlazeCommandRunConfiguration> getConfigurationEditor() {
    return new BlazeCommandRunConfigurationSettingsEditor(this);
  }

  static class BlazeCommandRunConfigurationSettingsEditor
      extends SettingsEditor<BlazeCommandRunConfiguration> {
    @Nullable private BlazeCommandRunConfigurationHandlerProvider handlerProvider;
    private BlazeCommandRunConfigurationHandlerEditor handlerEditor;
    @Nullable private JComponent handlerComponent;

    private final Box editor;
    private final JBLabel targetExpressionLabel;
    private final JBTextField targetField = new JBTextField(1);

    private boolean isEditable;

    public BlazeCommandRunConfigurationSettingsEditor(BlazeCommandRunConfiguration config) {
      targetExpressionLabel = new JBLabel(UIUtil.ComponentStyle.LARGE);
      editor = UiUtil.createBox(targetExpressionLabel, targetField);
      targetField.getEmptyText().setText("Full target expression starting with //");
      updateTargetExpressionLabel(config);
      updateHandlerEditor(config);
      setEditable(isConfigurationEditable(config));
    }

    private static boolean isConfigurationEditable(BlazeCommandRunConfiguration config) {
      RunConfiguration template =
          RunManager.getInstance(config.getProject())
              .getConfigurationTemplate(config.getFactory())
              .getConfiguration();
      if (config == template) {
        return true; // The default template is always editable.
      }
      return BlazeProjectDataManager.getInstance(config.getProject()).getBlazeProjectData() != null
          && !config.isConfigurationInvalidated();
    }

    private void setEditable(boolean editable) {
      isEditable = editable;
      targetField.setEnabled(isEditable);
      if (handlerComponent != null) {
        handlerComponent.setVisible(isEditable);
      }
    }

    private void updateTargetExpressionLabel(BlazeCommandRunConfiguration config) {
      targetExpressionLabel.setText(
          String.format(
              "Target expression (%s handled by %s):",
              config.getTargetKindName(), config.handler.getHandlerName()));
    }

    private void updateHandlerEditor(BlazeCommandRunConfiguration config) {
      handlerProvider = config.handlerProvider;
      handlerEditor = config.handler.getHandlerEditor();

      if (handlerComponent != null) {
        editor.remove(handlerComponent);
      }
      handlerComponent = handlerEditor.createEditor();
      if (handlerComponent != null) {
        editor.add(handlerComponent);
      }
    }

    @Override
    @NotNull
    protected JComponent createEditor() {
      return editor;
    }

    @Override
    protected void resetEditorFrom(BlazeCommandRunConfiguration config) {
      updateTargetExpressionLabel(config);
      if (config.handlerProvider != handlerProvider) {
        updateHandlerEditor(config);
      }
      setEditable(isConfigurationEditable(config));
      targetField.setText(config.target == null ? null : config.target.toString());
      handlerEditor.resetEditorFrom(config.handler);
    }

    @Override
    protected void applyEditorTo(BlazeCommandRunConfiguration config) {
      if (!isEditable) {
        return;
      }
      applyTarget(config);
      updateTargetExpressionLabel(config);
      if (config.handlerProvider != handlerProvider) {
        updateHandlerEditor(config);
        handlerEditor.resetEditorFrom(config.handler);
      } else {
        handlerEditor.applyEditorTo(config.handler);
      }
    }

    private void applyTarget(BlazeCommandRunConfiguration config) {
      String targetString = targetField.getText();
      BlazeCommandRunConfigurationHandler oldHandler = config.handler;
      config.setTarget(
          Strings.isNullOrEmpty(targetString) ? null : TargetExpression.fromString(targetString));
      if (config.handler != oldHandler) {
        config.loadExternalElementBackup();
      }
    }
  }
}

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
package com.google.idea.blaze.plugin.run;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.ideinfo.JavaRuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeRunConfiguration;
import com.google.idea.blaze.base.run.rulefinder.RuleFinder;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.ui.UiUtil;
import com.google.idea.blaze.plugin.IntellijPluginRule;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.util.PlatformUtils;
import com.intellij.util.execution.ParametersListUtil;
import org.jdom.Element;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;

/**
 * A run configuration that builds a plugin jar via blaze, copies it to the
 * SDK sandbox, then runs IJ with the plugin loaded.
 */
public class BlazeIntellijPluginConfiguration extends LocatableConfigurationBase implements BlazeRunConfiguration, ModuleRunConfiguration {

  private static final String TARGET_TAG = "blaze-target";
  private static final String USER_BLAZE_FLAG_TAG = "blaze-user-flag";
  private static final String USER_EXE_FLAG_TAG = "blaze-user-exe-flag";
  private static final String SDK_ATTR = "blaze-plugin-sdk";
  private static final String VM_PARAMS_ATTR = "blaze-vm-params";
  private static final String PROGRAM_PARAMS_ATTR = "blaze-program-params";

  private final String buildSystem;

  @Nullable private Label target;
  private ImmutableList<String> blazeFlags = ImmutableList.of();
  private ImmutableList<String> exeFlags = ImmutableList.of();
  @Nullable private Sdk pluginSdk;
  @Nullable private String vmParameters;
  @Nullable private String programParameters;

  public BlazeIntellijPluginConfiguration(
    Project project,
    ConfigurationFactory factory,
    String name,
    @Nullable RuleIdeInfo initialRule) {
    super(project, factory, name);
    this.buildSystem = Blaze.buildSystemName(project);
    if (initialRule != null) {
      target = initialRule.label;
    }
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (IdeaJdkHelper.isIdeaJdk(projectSdk)) {
      pluginSdk = projectSdk;
    }
  }

  @Override
  @Nullable
  public Label getTarget() {
    return target;
  }

  public void setTarget(Label target) {
    this.target = target;
  }

  private File findPluginJar() throws ExecutionException {
    RuleIdeInfo rule = RuleFinder.getInstance().ruleForTarget(getProject(), getTarget());
    if (rule == null) {
      throw new ExecutionException(buildSystem + " rule '" + getTarget() + "' not imported during sync");
    }
    JavaRuleIdeInfo javaRuleIdeInfo = rule.javaRuleIdeInfo;
    if (javaRuleIdeInfo == null) {
      throw new ExecutionException(buildSystem + " rule '" + getTarget() + "' is not a valid intellij_plugin rule");
    }
    Collection<LibraryArtifact> jars = javaRuleIdeInfo.jars;
    if (javaRuleIdeInfo.jars.size() > 1) {
      throw new ExecutionException("Invalid IntelliJ plugin rule: it has multiple output jars");
    }
    LibraryArtifact artifact = jars.isEmpty() ? null : jars.iterator().next();
    if (artifact == null || artifact.runtimeJar == null) {
      throw new ExecutionException("No output plugin jar found for '" + getTarget() + "'");
    }
    return artifact.runtimeJar.getFile();
  }

  /**
   * Plugin jar has been previously created via blaze build. This method:
   *  - copies jar to sandbox environment
   *  - cracks open jar and finds plugin.xml (with ID, etc., needed for JVM args)
   *  - sets up the SDK, etc. (use project SDK?)
   *  - sets up the JVM, and returns a JavaCommandLineState
   */
  @Nullable
  @Override
  public RunProfileState getState(Executor executor, ExecutionEnvironment env) throws ExecutionException {
    final Sdk ideaJdk = pluginSdk;
    if (!IdeaJdkHelper.isIdeaJdk(ideaJdk)) {
      throw new ExecutionException("Choose an IntelliJ Platform Plugin SDK");
    }
    String sandboxHome = IdeaJdkHelper.getSandboxHome(ideaJdk);
    if (sandboxHome == null){
      throw new ExecutionException("No sandbox specified for IntelliJ Platform Plugin SDK");
    }

    try {
      sandboxHome = new File(sandboxHome).getCanonicalPath();
    }
    catch (IOException e) {
      throw new ExecutionException("No sandbox specified for IntelliJ Platform Plugin SDK");
    }
    final String canonicalSandbox = sandboxHome;
    final File pluginJar = findPluginJar();
    if (!pluginJar.exists()) {
      throw new ExecutionException("No plugin jar found. Did the " + buildSystem + " build fail?");
    }
    final File pluginJarDestination = new File(canonicalSandbox, "plugins/" + pluginJar.getName());

    // copy license from running instance of idea
    IdeaJdkHelper.copyIDEALicense(sandboxHome);

    final JavaCommandLineState state = new JavaCommandLineState(env) {
      @Override
      protected JavaParameters createJavaParameters() throws ExecutionException {

        // copy plugin jar to sandbox
        IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.loadDescriptor(pluginJar, "plugin.xml");
        String buildNumber = IdeaJdkHelper.getBuildNumber(ideaJdk);
        if (PluginManagerCore.isIncompatible(pluginDescriptor, BuildNumber.fromString(buildNumber))) {
          throw new ExecutionException(
            String.format("Plugin SDK version '%s' is incompatible with this plugin (since: '%s', until: '%s')",
                          buildNumber, pluginDescriptor.getSinceBuild(), pluginDescriptor.getUntilBuild()));
        }

        try {
          pluginJarDestination.getParentFile().mkdirs();
          Files.copy(pluginJar.toPath(), pluginJarDestination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e) {
          throw new ExecutionException("Error copying plugin jar to sandbox", e);
        }

        final JavaParameters params = new JavaParameters();

        ParametersList vm = params.getVMParametersList();

        fillParameterList(vm, vmParameters);
        fillParameterList(params.getProgramParametersList(), programParameters);

        IntellijWithPluginClasspathHelper.addRequiredVmParams(params, ideaJdk);

        vm.defineProperty(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY, pluginDescriptor.getPluginId().toString());

        if (!vm.hasProperty(PlatformUtils.PLATFORM_PREFIX_KEY) && buildNumber != null) {
          String prefix = IdeaJdkHelper.getPlatformPrefix(buildNumber);
          if (prefix != null) {
            vm.defineProperty(PlatformUtils.PLATFORM_PREFIX_KEY, prefix);
          }
        }
        return params;
      }

      @Override
      protected OSProcessHandler startProcess() throws ExecutionException {
        final OSProcessHandler handler = super.startProcess();
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(ProcessEvent event) {
            pluginJarDestination.delete();
          }
        });
        return handler;
      }
    };
    return state;
  }

  private static void fillParameterList(ParametersList list, @Nullable String value) {
    if (value == null) return;

    for (String parameter : value.split(" ")) {
      if (parameter != null && parameter.length() > 0) {
        list.add(parameter);
      }
    }
  }

  @Override
  public Module[] getModules() {
    return Module.EMPTY_ARRAY;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    Label target = getTarget();
    if (target == null) {
      throw new RuntimeConfigurationError("Select a target to run");
    }
    RuleIdeInfo rule = RuleFinder.getInstance().ruleForTarget(getProject(), target);
    if (rule == null) {
      throw new RuntimeConfigurationError("The selected target does not exist.");
    }
    if (!IntellijPluginRule.isPluginRule(rule)) {
      throw new RuntimeConfigurationError(
        "The selected target is not an intellij_plugin");
    }
    if (!IdeaJdkHelper.isIdeaJdk(pluginSdk)) {
      throw new RuntimeConfigurationError("Select an IntelliJ Platform Plugin SDK");
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    // Target is persisted as a tag to permit multiple targets in the future.
    Element targetElement = element.getChild(TARGET_TAG);
    if (targetElement != null && !Strings.isNullOrEmpty(targetElement.getTextTrim())) {
      target = (Label) TargetExpression.fromString(targetElement.getTextTrim());
    }
    else {
      target = null;
    }
    blazeFlags = loadUserFlags(element, USER_BLAZE_FLAG_TAG);
    exeFlags = loadUserFlags(element, USER_EXE_FLAG_TAG);

    String sdkName = element.getAttributeValue(SDK_ATTR);
    if (!Strings.isNullOrEmpty(sdkName)) {
      pluginSdk = ProjectJdkTable.getInstance().findJdk(sdkName);
    }
    vmParameters = Strings.emptyToNull(element.getAttributeValue(VM_PARAMS_ATTR));
    programParameters = Strings.emptyToNull(element.getAttributeValue(PROGRAM_PARAMS_ATTR));
  }

  private static ImmutableList<String> loadUserFlags(Element root,String tag) {
    ImmutableList.Builder<String> flagsBuilder = ImmutableList.builder();
    for (Element e : root.getChildren(tag)) {
      String flag = e.getTextTrim();
      if (flag != null && !flag.isEmpty()) {
        flagsBuilder.add(flag);
      }
    }
    return flagsBuilder.build();
  }

  private static void saveUserFlags(Element root, List<String> flags, String tag) {
    for (String flag : flags) {
      Element child = new Element(tag);
      child.setText(flag);
      root.addContent(child);
    }
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
    saveUserFlags(element, blazeFlags, USER_BLAZE_FLAG_TAG);
    saveUserFlags(element, exeFlags, USER_EXE_FLAG_TAG);
    if (pluginSdk != null) {
      element.setAttribute(SDK_ATTR, pluginSdk.getName());
    }
    if (vmParameters != null) {
      element.setAttribute(VM_PARAMS_ATTR, vmParameters);
    }
    if (programParameters != null) {
      element.setAttribute(PROGRAM_PARAMS_ATTR, programParameters);
    }
  }

  @Override
  public BlazeIntellijPluginConfiguration clone() {
    final BlazeIntellijPluginConfiguration configuration = (BlazeIntellijPluginConfiguration) super.clone();
    configuration.target = target;
    configuration.blazeFlags = blazeFlags;
    configuration.exeFlags = exeFlags;
    configuration.pluginSdk = pluginSdk;
    configuration.vmParameters = vmParameters;
    configuration.programParameters = programParameters;
    return configuration;
  }

  protected BlazeCommand buildBlazeCommand(Project project, ProjectViewSet projectViewSet) {
    BlazeCommand.Builder command = BlazeCommand.builder(Blaze.getBuildSystem(getProject()), BlazeCommandName.BUILD)
      .addTargets(getTarget())
      .addBlazeFlags(BlazeFlags.buildFlags(project, projectViewSet))
      .addBlazeFlags(blazeFlags)
      .addExeFlags(exeFlags)
      ;
    return command.build();
  }

  @Override
  public BlazeIntellijPluginConfigurationSettingsEditor getConfigurationEditor() {
    List<RuleIdeInfo> javaRules = RuleFinder.getInstance().findRules(getProject(), IntellijPluginRule::isPluginRule);
    List<Label> javaLabels = Lists.newArrayList();
    for (RuleIdeInfo rule : javaRules) {
      javaLabels.add(rule.label);
    }
    return new BlazeIntellijPluginConfigurationSettingsEditor(buildSystem, javaLabels);
  }

  @Override
  @Nullable
  public String suggestedName() {
    Label target = getTarget();
    if (target == null) {
      return null;
    }
    return target.ruleName().toString();
  }

  @VisibleForTesting
  static class BlazeIntellijPluginConfigurationSettingsEditor extends SettingsEditor<BlazeIntellijPluginConfiguration> {
    private final String buildSystemName;
    private final ComboBox targetCombo;
    private final JTextArea blazeFlagsField = new JTextArea(5, 0);
    private final JTextArea exeFlagsField = new JTextArea(5, 0);
    private final JdkComboBox sdkCombo;
    private final LabeledComponent<RawCommandLineEditor> vmParameters = new LabeledComponent<>();
    private final LabeledComponent<RawCommandLineEditor> programParameters = new LabeledComponent<>();

    public BlazeIntellijPluginConfigurationSettingsEditor(String buildSystemName, List<Label> javaLabels) {
      this.buildSystemName = buildSystemName;
      targetCombo = new ComboBox(new DefaultComboBoxModel(Ordering.usingToString().sortedCopy(javaLabels).toArray()));
      targetCombo.setRenderer(new ListCellRendererWrapper<Label>() {
        @Override
        public void customize(JList list, @Nullable Label value, int index, boolean selected, boolean hasFocus) {
          setText(value == null ? null : value.toString());
        }
      });

      ProjectSdksModel sdksModel = new ProjectSdksModel();
      sdksModel.reset(null);
      sdkCombo = new JdkComboBox(sdksModel, IdeaJdkHelper::isIdeaJdkType);
    }

    @VisibleForTesting
    @Override
    public void resetEditorFrom(BlazeIntellijPluginConfiguration s) {
      targetCombo.setSelectedItem(s.getTarget());
      blazeFlagsField.setText(ParametersListUtil.join(s.blazeFlags));
      exeFlagsField.setText(ParametersListUtil.join(s.exeFlags));
      if (s.pluginSdk != null) {
        sdkCombo.setSelectedJdk(s.pluginSdk);
      } else {
        s.pluginSdk = sdkCombo.getSelectedJdk();
      }
      if (s.vmParameters != null) {
        vmParameters.getComponent().setText(s.vmParameters);
      }
      if (s.programParameters != null) {
        programParameters.getComponent().setText(s.programParameters);
      }
    }

    @VisibleForTesting
    @Override
    public void applyEditorTo(BlazeIntellijPluginConfiguration s) throws ConfigurationException {
      try {
        s.target = (Label)targetCombo.getSelectedItem();
      }
      catch (ClassCastException e) {
        throw new ConfigurationException("Invalid label specified.");
      }
      s.blazeFlags = ImmutableList.copyOf(ParametersListUtil.parse(Strings.nullToEmpty(blazeFlagsField.getText())));
      s.exeFlags = ImmutableList.copyOf(ParametersListUtil.parse(Strings.nullToEmpty(exeFlagsField.getText())));
      s.pluginSdk = sdkCombo.getSelectedJdk();
      s.vmParameters = vmParameters.getComponent().getText();
      s.programParameters = programParameters.getComponent().getText();
    }

    @Override
    protected JComponent createEditor() {
      vmParameters.setText("VM options:");
      vmParameters.setComponent(new RawCommandLineEditor());
      vmParameters.getComponent().setDialogCaption(vmParameters.getRawText());
      vmParameters.setLabelLocation(BorderLayout.WEST);

      programParameters.setText("Program arguments");
      programParameters.setComponent(new RawCommandLineEditor());
      programParameters.getComponent().setDialogCaption(programParameters.getRawText());
      programParameters.setLabelLocation(BorderLayout.WEST);

      return UiUtil.createBox(
        new JLabel("Target:"),
        targetCombo,
        new JLabel("Plugin SDK"),
        sdkCombo,
        vmParameters.getLabel(),
        vmParameters.getComponent(),
        programParameters.getLabel(),
        programParameters.getComponent(),
        new JLabel(buildSystemName + " flags:"),
        blazeFlagsField,
        new JLabel("Executable flags:"),
        exeFlagsField
      );
    }
  }
}


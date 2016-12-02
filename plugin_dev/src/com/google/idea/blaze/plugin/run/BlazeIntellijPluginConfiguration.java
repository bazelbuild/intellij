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
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.Dependency.DependencyType;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.BlazeRunConfiguration;
import com.google.idea.blaze.base.run.targetfinder.TargetFinder;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.ui.UiUtil;
import com.google.idea.blaze.plugin.IntellijPluginRule;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.ModuleRunConfiguration;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
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
import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextArea;
import org.jdom.Element;

/**
 * A run configuration that builds a plugin jar via blaze, copies it to the SDK sandbox, then runs
 * IJ with the plugin loaded.
 */
public class BlazeIntellijPluginConfiguration extends LocatableConfigurationBase
    implements BlazeRunConfiguration, ModuleRunConfiguration {

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
  @Nullable String vmParameters;
  @Nullable private String programParameters;

  public BlazeIntellijPluginConfiguration(
      Project project,
      ConfigurationFactory factory,
      String name,
      @Nullable TargetIdeInfo initialTarget) {
    super(project, factory, name);
    this.buildSystem = Blaze.buildSystemName(project);
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (IdeaJdkHelper.isIdeaJdk(projectSdk)) {
      pluginSdk = projectSdk;
    }
    if (initialTarget != null) {
      target = initialTarget.key.label;
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

  public void setPluginSdk(Sdk sdk) {
    if (IdeaJdkHelper.isIdeaJdk(sdk)) {
      pluginSdk = sdk;
    }
  }

  private ImmutableList<File> findPluginJars() throws ExecutionException {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    if (blazeProjectData == null) {
      throw new ExecutionException("Not synced yet, please sync project");
    }
    TargetIdeInfo target = TargetFinder.getInstance().targetForLabel(getProject(), getTarget());
    if (target == null) {
      throw new ExecutionException(
          buildSystem + " target '" + getTarget() + "' not imported during sync");
    }
    return IntellijPluginRule.isPluginBundle(target)
        ? findBundledJars(blazeProjectData.artifactLocationDecoder, target)
        : ImmutableList.of(findPluginJar(blazeProjectData.artifactLocationDecoder, target));
  }

  private ImmutableList<File> findBundledJars(
      ArtifactLocationDecoder artifactLocationDecoder, TargetIdeInfo target)
      throws ExecutionException {
    ImmutableList.Builder<File> jars = ImmutableList.builder();
    for (Dependency dep : target.dependencies) {
      if (dep.dependencyType == DependencyType.COMPILE_TIME && dep.targetKey.isPlainTarget()) {
        TargetIdeInfo depTarget =
            TargetFinder.getInstance().targetForLabel(getProject(), dep.targetKey.label);
        if (depTarget != null && IntellijPluginRule.isSinglePluginTarget(depTarget)) {
          jars.add(findPluginJar(artifactLocationDecoder, depTarget));
        }
      }
    }
    return jars.build();
  }

  private File findPluginJar(ArtifactLocationDecoder artifactLocationDecoder, TargetIdeInfo target)
      throws ExecutionException {
    JavaIdeInfo javaIdeInfo = target.javaIdeInfo;
    if (!IntellijPluginRule.isSinglePluginTarget(target) || javaIdeInfo == null) {
      throw new ExecutionException(
          buildSystem + " target '" + target + "' is not a valid intellij_plugin target");
    }
    Collection<LibraryArtifact> jars = javaIdeInfo.jars;
    if (javaIdeInfo.jars.size() > 1) {
      throw new ExecutionException("Invalid IntelliJ plugin target: it has multiple output jars");
    }
    LibraryArtifact artifact = jars.isEmpty() ? null : jars.iterator().next();
    if (artifact == null || artifact.classJar == null) {
      throw new ExecutionException("No output plugin jar found for '" + target + "'");
    }
    return artifactLocationDecoder.decode(artifact.classJar);
  }

  /**
   * Plugin jar has been previously created via blaze build. This method: - copies jar to sandbox
   * environment - cracks open jar and finds plugin.xml (with ID, etc., needed for JVM args) - sets
   * up the SDK, etc. (use project SDK?) - sets up the JVM, and returns a JavaCommandLineState
   */
  @Nullable
  @Override
  public RunProfileState getState(Executor executor, ExecutionEnvironment env)
      throws ExecutionException {
    final Sdk ideaJdk = pluginSdk;
    if (!IdeaJdkHelper.isIdeaJdk(ideaJdk)) {
      throw new ExecutionException("Choose an IntelliJ Platform Plugin SDK");
    }
    String sandboxHome = IdeaJdkHelper.getSandboxHome(ideaJdk);
    if (sandboxHome == null) {
      throw new ExecutionException("No sandbox specified for IntelliJ Platform Plugin SDK");
    }

    try {
      sandboxHome = new File(sandboxHome).getCanonicalPath();
    } catch (IOException e) {
      throw new ExecutionException("No sandbox specified for IntelliJ Platform Plugin SDK");
    }
    final String canonicalSandbox = sandboxHome;
    final ImmutableList<File> pluginJars = findPluginJars();
    for (File file : pluginJars) {
      if (!file.exists()) {
        throw new ExecutionException(
            String.format(
                "Plugin jar '%s' not found. Did the %s build fail?", file.getName(), buildSystem));
      }
    }
    // copy license from running instance of idea
    IdeaJdkHelper.copyIDEALicense(sandboxHome);

    final JavaCommandLineState state =
        new JavaCommandLineState(env) {
          @Override
          protected JavaParameters createJavaParameters() throws ExecutionException {
            String buildNumber = IdeaJdkHelper.getBuildNumber(ideaJdk);
            List<String> pluginIds = Lists.newArrayList();
            for (File jar : pluginJars) {
              pluginIds.add(copyPluginJarToSandbox(jar, buildNumber, canonicalSandbox));
            }

            final JavaParameters params = new JavaParameters();

            ParametersList vm = params.getVMParametersList();

            fillParameterList(vm, vmParameters);
            fillParameterList(params.getProgramParametersList(), programParameters);

            IntellijWithPluginClasspathHelper.addRequiredVmParams(params, ideaJdk);

            vm.defineProperty(
                JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY, Joiner.on(',').join(pluginIds));

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
            handler.addProcessListener(
                new ProcessAdapter() {
                  @Override
                  public void processTerminated(ProcessEvent event) {
                    for (File jar : pluginJars) {
                      pluginDestination(jar, canonicalSandbox).delete();
                    }
                  }
                });
            return handler;
          }
        };
    return state;
  }

  private static File pluginDestination(File jar, String sandboxPath) {
    return new File(sandboxPath, "plugins/" + jar.getName());
  }

  /** Copies the plugin jar to the sandbox, and returns the plugin ID. */
  private static String copyPluginJarToSandbox(File jar, String buildNumber, String sandboxPath)
      throws ExecutionException {
    IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.loadDescriptor(jar, "plugin.xml");
    if (PluginManagerCore.isIncompatible(pluginDescriptor, BuildNumber.fromString(buildNumber))) {
      throw new ExecutionException(
          String.format(
              "Plugin SDK version '%s' is incompatible with this plugin "
                  + "(since: '%s', until: '%s')",
              buildNumber, pluginDescriptor.getSinceBuild(), pluginDescriptor.getUntilBuild()));
    }
    File pluginJarDestination = pluginDestination(jar, sandboxPath);
    try {
      pluginJarDestination.getParentFile().mkdirs();
      Files.copy(jar.toPath(), pluginJarDestination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new ExecutionException("Error copying plugin jar to sandbox", e);
    }
    return pluginDescriptor.getPluginId().getIdString();
  }

  private static void fillParameterList(ParametersList list, @Nullable String value) {
    if (value == null) {
      return;
    }

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

    Label label = getTarget();
    if (label == null) {
      throw new RuntimeConfigurationError("Select a target to run");
    }
    TargetIdeInfo target = TargetFinder.getInstance().targetForLabel(getProject(), label);
    if (target == null) {
      throw new RuntimeConfigurationError("The selected target does not exist.");
    }
    if (!IntellijPluginRule.isPluginTarget(target)) {
      throw new RuntimeConfigurationError("The selected target is not an intellij_plugin");
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
    } else {
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
    final BlazeIntellijPluginConfiguration configuration =
        (BlazeIntellijPluginConfiguration) super.clone();
    configuration.target = target;
    configuration.blazeFlags = blazeFlags;
    configuration.exeFlags = exeFlags;
    configuration.pluginSdk = pluginSdk;
    configuration.vmParameters = vmParameters;
    configuration.programParameters = programParameters;
    return configuration;
  }

  protected BlazeCommand buildBlazeCommand(Project project, ProjectViewSet projectViewSet) {
    BlazeCommand.Builder command =
        BlazeCommand.builder(Blaze.getBuildSystem(getProject()), BlazeCommandName.BUILD)
            .addTargets(getTarget())
            .addBlazeFlags(BlazeFlags.buildFlags(project, projectViewSet))
            .addBlazeFlags(blazeFlags)
            .addExeFlags(exeFlags);
    return command.build();
  }

  @Override
  public BlazeIntellijPluginConfigurationSettingsEditor getConfigurationEditor() {
    List<TargetIdeInfo> javaTargets =
        TargetFinder.getInstance().findTargets(getProject(), IntellijPluginRule::isPluginTarget);
    List<Label> javaLabels = Lists.newArrayList();
    for (TargetIdeInfo target : javaTargets) {
      javaLabels.add(target.key.label);
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
    return new BlazeConfigurationNameBuilder()
        .setBuildSystemName(getProject())
        .setCommandName("build")
        .setTargetString(target)
        .build();
  }

  @VisibleForTesting
  static class BlazeIntellijPluginConfigurationSettingsEditor
      extends SettingsEditor<BlazeIntellijPluginConfiguration> {
    private final String buildSystemName;
    private final ComboBox targetCombo;
    private final JTextArea blazeFlagsField = new JTextArea(5, 0);
    private final JTextArea exeFlagsField = new JTextArea(5, 0);
    private final JdkComboBox sdkCombo;
    private final LabeledComponent<RawCommandLineEditor> vmParameters = new LabeledComponent<>();
    private final LabeledComponent<RawCommandLineEditor> programParameters =
        new LabeledComponent<>();

    public BlazeIntellijPluginConfigurationSettingsEditor(
        String buildSystemName, List<Label> javaLabels) {
      this.buildSystemName = buildSystemName;
      targetCombo =
          new ComboBox(
              new DefaultComboBoxModel(Ordering.usingToString().sortedCopy(javaLabels).toArray()));
      targetCombo.setRenderer(
          new ListCellRendererWrapper<Label>() {
            @Override
            public void customize(
                JList list, @Nullable Label value, int index, boolean selected, boolean hasFocus) {
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
        s.target = (Label) targetCombo.getSelectedItem();
      } catch (ClassCastException e) {
        throw new ConfigurationException("Invalid label specified.");
      }
      s.blazeFlags =
          ImmutableList.copyOf(
              ParametersListUtil.parse(Strings.nullToEmpty(blazeFlagsField.getText())));
      s.exeFlags =
          ImmutableList.copyOf(
              ParametersListUtil.parse(Strings.nullToEmpty(exeFlagsField.getText())));
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
          exeFlagsField);
    }
  }
}

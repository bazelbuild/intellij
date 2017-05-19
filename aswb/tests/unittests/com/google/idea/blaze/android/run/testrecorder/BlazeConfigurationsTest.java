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
package com.google.idea.blaze.android.run.testrecorder;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidJavaDebugger;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.android.tools.idea.run.editor.ShowChooserTargetProvider;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.gct.testrecorder.run.TestRecorderRunConfigurationProxy;
import com.google.gct.testrecorder.run.TestRecorderRunConfigurationProxyProvider;
import com.google.gct.testrecorder.ui.TestRecorderAction;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationHandler;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationHandlerProvider;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.bazel.WorkspaceRootProvider;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeBeforeRunTaskProvider;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider;
import com.google.idea.blaze.base.run.targetfinder.TargetFinder;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.ide.util.ProjectPropertiesComponentImpl;
import com.intellij.mock.MockModule;
import com.intellij.mock.MockProject;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link TestRecorderBlazeCommandRunConfiguration}. */
@RunWith(JUnit4.class)
public class BlazeConfigurationsTest extends BlazeTestCase {

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);

    mockBlazeImportSettings(projectServices);
    applicationServices.register(TargetFinder.class, new MockTargetFinder());

    ExtensionPoint<ConfigurationType> configurationTypeExtensionPoint =
        registerExtensionPoint(ConfigurationType.CONFIGURATION_TYPE_EP, ConfigurationType.class);
    configurationTypeExtensionPoint.registerExtension(new BlazeCommandRunConfigurationType());

    ExtensionPoint<BlazeCommandRunConfigurationHandlerProvider> handlerProviderExtensionPoint =
        registerExtensionPoint(
            BlazeCommandRunConfigurationHandlerProvider.EP_NAME,
            BlazeCommandRunConfigurationHandlerProvider.class);
    handlerProviderExtensionPoint.registerExtension(
        new MockBlazeAndroidBinaryRunConfigurationHandlerProvider());

    ExtensionPoint<BuildSystemProvider> buildSystemProviderExtensionPoint =
        registerExtensionPoint(BuildSystemProvider.EP_NAME, BuildSystemProvider.class);
    buildSystemProviderExtensionPoint.registerExtension(new MockBuildSystemProvider());

    ExtensionPoint<DeployTargetProvider> deployTargetProviderExtensionPoint =
        registerExtensionPoint(
            ExtensionPointName.create("com.android.run.deployTargetProvider"),
            DeployTargetProvider.class);
    deployTargetProviderExtensionPoint.registerExtension(new ShowChooserTargetProvider());

    ExtensionPoint<AndroidDebugger> androidDebuggerExtensionPoint =
        registerExtensionPoint(AndroidDebugger.EP_NAME, AndroidDebugger.class);
    androidDebuggerExtensionPoint.registerExtension(new AndroidJavaDebugger());

    ExtensionPoint<BeforeRunTaskProvider> beforeRunTaskProviderExtensionPoint =
        registerExtensionPoint(
            ExtensionPointName.create("com.intellij.stepsBeforeRunProvider"),
            BeforeRunTaskProvider.class);
    ((ExtensionsAreaImpl) Extensions.getArea(project))
        .registerExtensionPoint((ExtensionPointImpl) beforeRunTaskProviderExtensionPoint);
    beforeRunTaskProviderExtensionPoint.registerExtension(new BlazeBeforeRunTaskProvider());

    ExtensionPoint<TestRecorderRunConfigurationProxyProvider>
        testRecorderRunConfigurationProxyProviderExtensionPoint =
            registerExtensionPoint(
                ExtensionPointName.create(
                    "com.google.gct.testrecorder.run.testRecorderRunConfigurationProxyProvider"),
                TestRecorderRunConfigurationProxyProvider.class);
    testRecorderRunConfigurationProxyProviderExtensionPoint.registerExtension(
        new TestRecorderBlazeCommandRunConfigurationProxyProvider());

    ((MockProject) project)
        .addComponent(
            RunManager.class, new RunManagerImpl(project, new ProjectPropertiesComponentImpl()));
  }

  @Test
  public void testSuitableRunConfigurations() {
    addConfigurations();

    List<RunConfiguration> allConfigurations =
        RunManagerEx.getInstanceEx(project).getAllConfigurationsList();
    assertThat(allConfigurations.size()).isEqualTo(2);

    List<RunConfiguration> suitableConfigurations =
        TestRecorderAction.getSuitableRunConfigurations(project);
    assertThat(suitableConfigurations.size()).isEqualTo(1);
    assertThat(suitableConfigurations.get(0).getName()).isEqualTo("AndroidBinaryConfiguration");
  }

  @Test
  public void testLaunchActivityClass() {
    BlazeCommandRunConfiguration blazeConfiguration =
        BlazeCommandRunConfigurationType.getInstance()
            .getFactory()
            .createTemplateConfiguration(project);
    blazeConfiguration.setTarget(Label.create("//label:android_binary_rule"));
    BlazeAndroidBinaryRunConfigurationState configurationState =
        ((BlazeAndroidBinaryRunConfigurationHandler) blazeConfiguration.getHandler()).getState();
    configurationState.setMode(BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY);
    configurationState.setActivityClass("MyAppMainActivity");

    TestRecorderRunConfigurationProxy proxy =
        TestRecorderRunConfigurationProxy.getInstance(blazeConfiguration);
    assertThat(proxy).isNotNull();
    assertThat(proxy.getLaunchActivityClass()).isEqualTo("MyAppMainActivity");
  }

  private void mockBlazeImportSettings(Container projectServices) {
    BlazeImportSettingsManager importSettingsManager = new BlazeImportSettingsManager();
    importSettingsManager.setImportSettings(
        new BlazeImportSettings("", "", "", "", Blaze.BuildSystem.Blaze));
    projectServices.register(BlazeImportSettingsManager.class, importSettingsManager);
  }

  private void addConfigurations() {
    RunManagerImpl runManager = (RunManagerImpl) RunManagerEx.getInstanceEx(project);
    BlazeCommandRunConfigurationType.BlazeCommandRunConfigurationFactory configurationFactory =
        BlazeCommandRunConfigurationType.getInstance().getFactory();

    BlazeCommandRunConfiguration blazeAndroidBinaryConfiguration =
        configurationFactory.createTemplateConfiguration(project);
    blazeAndroidBinaryConfiguration.setName("AndroidBinaryConfiguration");
    blazeAndroidBinaryConfiguration.setTarget(Label.create("//label:android_binary_rule"));

    BlazeCommandRunConfiguration blazeAndroidTestConfiguration =
        configurationFactory.createTemplateConfiguration(project);
    blazeAndroidTestConfiguration.setName("AndroidTestConfiguration");
    blazeAndroidTestConfiguration.setTarget(Label.create("//label:android_test_rule"));

    runManager.addConfiguration(
        runManager.createConfiguration(blazeAndroidBinaryConfiguration, configurationFactory),
        true);
    runManager.addConfiguration(
        runManager.createConfiguration(blazeAndroidTestConfiguration, configurationFactory), true);
  }

  private static class MockTargetFinder extends TargetFinder {
    @Override
    public List<TargetIdeInfo> findTargets(Project project, Predicate<TargetIdeInfo> predicate) {
      return null;
    }

    @Override
    public TargetIdeInfo targetForLabel(Project project, final Label label) {
      TargetIdeInfo.Builder builder = TargetIdeInfo.builder().setLabel(label);
      if (label.equals(Label.create("//label:android_binary_rule"))) {
        builder.setKind(Kind.ANDROID_BINARY);
      } else if (label.equals(Label.create("//label:android_test_rule"))) {
        builder.setKind(Kind.ANDROID_TEST);
      }
      return builder.build();
    }
  }

  private class MockBlazeAndroidBinaryRunConfigurationHandlerProvider
      extends BlazeAndroidBinaryRunConfigurationHandlerProvider {
    @Override
    public boolean canHandleKind(Kind kind) {
      return true;
    }

    @Override
    public BlazeCommandRunConfigurationHandler createHandler(BlazeCommandRunConfiguration config) {
      return new MockBlazeAndroidBinaryRunConfigurationHandler(config);
    }
  }

  private class MockBlazeAndroidBinaryRunConfigurationHandler
      extends BlazeAndroidBinaryRunConfigurationHandler {
    private final MockModule mockModule;

    MockBlazeAndroidBinaryRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
      super(configuration);
      mockModule = new MockModule(project, () -> {});
    }

    @Nullable
    @Override
    public Module getModule() {
      Label label = getLabel();
      if (label != null && label.equals(Label.create("//label:android_binary_rule"))) {
        return mockModule;
      }

      return null;
    }
  }

  private static class MockBuildSystemProvider implements BuildSystemProvider {
    @Override
    public Blaze.BuildSystem buildSystem() {
      return Blaze.BuildSystem.Blaze;
    }

    @Override
    public String getBinaryPath() {
      return "/usr/bin/blaze";
    }

    @Override
    public WorkspaceRootProvider getWorkspaceRootProvider() {
      return null;
    }

    @Override
    public ImmutableList<String> buildArtifactDirectories(WorkspaceRoot root) {
      return null;
    }

    @Nullable
    @Override
    public String getRuleDocumentationUrl(RuleDefinition rule) {
      return null;
    }

    @Nullable
    @Override
    public String getProjectViewDocumentationUrl() {
      return null;
    }

    @Override
    public boolean isBuildFile(String fileName) {
      return false;
    }

    @Nullable
    @Override
    public File findBuildFileInDirectory(File directory) {
      return null;
    }

    @Override
    public ImmutableList<FileNameMatcher> buildLanguageFileTypeMatchers() {
      return ImmutableList.of();
    }

    @Override
    public void populateBlazeVersionData(
        BuildSystem buildSystem,
        WorkspaceRoot workspaceRoot,
        BlazeInfo blazeInfo,
        BlazeVersionData.Builder builder) {}
  }
}

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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BuildFlagsProvider;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationHandlerProvider;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.targetfinder.TargetFinder;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.java.fastbuild.FastBuildInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildService;
import com.google.idea.blaze.java.sync.source.JavaLikeLanguage;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeJavaRunProfileState}. */
@RunWith(JUnit4.class)
public class BlazeJavaRunProfileStateTest extends BlazeTestCase {

  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("", "", "", "", BuildSystem.Blaze);

  private BlazeCommandRunConfiguration configuration;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    projectServices.register(BlazeImportSettingsManager.class, new BlazeImportSettingsManager());
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    ExperimentService experimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experimentService);
    applicationServices.register(BlazeUserSettings.class, new BlazeUserSettings());
    projectServices.register(FastBuildService.class, new DisabledFastBuildService());
    projectServices.register(ProjectViewManager.class, new MockProjectViewManager());

    ExtensionPointImpl<TargetFinder> targetFinderEp =
        registerExtensionPoint(TargetFinder.EP_NAME, TargetFinder.class);
    targetFinderEp.registerExtension(new MockTargetFinder());

    ExtensionPointImpl<JavaLikeLanguage> javaLikeEp =
        registerExtensionPoint(JavaLikeLanguage.EP_NAME, JavaLikeLanguage.class);
    javaLikeEp.registerExtension(new JavaLikeLanguage.Java());

    registerExtensionPoint(BuildFlagsProvider.EP_NAME, BuildFlagsProvider.class);
    ExtensionPointImpl<BlazeCommandRunConfigurationHandlerProvider> handlerProviderEp =
        registerExtensionPoint(
            BlazeCommandRunConfigurationHandlerProvider.EP_NAME,
            BlazeCommandRunConfigurationHandlerProvider.class);
    handlerProviderEp.registerExtension(new BlazeJavaRunConfigurationHandlerProvider());
    handlerProviderEp.registerExtension(new BlazeCommandGenericRunConfigurationHandlerProvider());
    ExtensionPointImpl<BuildSystemProvider> buildSystemProviderExtensionPoint =
        registerExtensionPoint(BuildSystemProvider.EP_NAME, BuildSystemProvider.class);
    BuildSystemProvider buildSystemProvider = mock(BuildSystemProvider.class);
    when(buildSystemProvider.getBinaryPath()).thenReturn("/usr/bin/blaze");
    buildSystemProviderExtensionPoint.registerExtension(buildSystemProvider);

    configuration =
        new BlazeCommandRunConfigurationType().getFactory().createTemplateConfiguration(project);
  }

  @Test
  public void flagsShouldBeAppendedIfPresent() {
    configuration.setTargetInfo(
        TargetInfo.builder(Label.create("//label:rule"), "java_test").build());
    BlazeCommandRunConfigurationCommonState handlerState =
        (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
    handlerState.getCommandState().setCommand(BlazeCommandName.fromString("command"));
    handlerState.getBlazeFlagsState().setRawFlags(ImmutableList.of("--flag1", "--flag2"));
    assertThat(
            BlazeJavaRunProfileState.getBlazeCommandBuilder(
                    project, configuration, ImmutableList.of(), ExecutorType.RUN)
                .build()
                .toList())
        .isEqualTo(
            ImmutableList.of(
                "/usr/bin/blaze",
                "command",
                BlazeFlags.getToolTagFlag(),
                "--flag1",
                "--flag2",
                "--",
                "//label:rule"));
  }

  @Test
  public void debugFlagShouldBeIncludedForJavaTest() {
    configuration.setTargetInfo(
        TargetInfo.builder(Label.create("//label:rule"), "java_test").build());
    BlazeCommandRunConfigurationCommonState handlerState =
        (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
    handlerState.getCommandState().setCommand(BlazeCommandName.fromString("command"));
    assertThat(
            BlazeJavaRunProfileState.getBlazeCommandBuilder(
                    project, configuration, ImmutableList.of(), ExecutorType.DEBUG)
                .build()
                .toList())
        .isEqualTo(
            ImmutableList.of(
                "/usr/bin/blaze",
                "command",
                BlazeFlags.getToolTagFlag(),
                "--java_debug",
                "--test_arg=--wrapper_script_flag=--debug=5005",
                "--",
                "//label:rule"));
  }

  @Test
  public void debugFlagShouldBeIncludedForJavaBinary() {
    configuration.setTargetInfo(
        TargetInfo.builder(Label.create("//label:java_binary_rule"), "java_binary").build());
    BlazeCommandRunConfigurationCommonState handlerState =
        (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
    handlerState.getCommandState().setCommand(BlazeCommandName.fromString("command"));
    assertThat(
            BlazeJavaRunProfileState.getBlazeCommandBuilder(
                    project, configuration, ImmutableList.of(), ExecutorType.DEBUG)
                .build()
                .toList())
        .isEqualTo(
            ImmutableList.of(
                "/usr/bin/blaze",
                "command",
                BlazeFlags.getToolTagFlag(),
                "--",
                "//label:java_binary_rule",
                "--wrapper_script_flag=--debug=5005"));
  }

  private static class MockTargetFinder implements TargetFinder {
    @Override
    public Future<TargetInfo> findTarget(Project project, Label label) {
      TargetIdeInfo.Builder builder = TargetIdeInfo.builder().setLabel(label);
      if (label.targetName().toString().equals("java_binary_rule")) {
        builder.setKind(Kind.JAVA_BINARY);
      } else {
        builder.setKind(Kind.JAVA_TEST);
      }
      return Futures.immediateFuture(builder.build().toTargetInfo());
    }
  }

  private static class MockProjectViewManager extends ProjectViewManager {
    @Override
    public ProjectViewSet getProjectViewSet() {
      return ProjectViewSet.builder().build();
    }

    @Nullable
    @Override
    public ProjectViewSet reloadProjectView(BlazeContext context) {
      return ProjectViewSet.builder().build();
    }

    @Nullable
    @Override
    public ProjectViewSet reloadProjectView(
        BlazeContext context, WorkspacePathResolver workspacePathResolver) {
      return ProjectViewSet.builder().build();
    }
  }

  private static class DisabledFastBuildService implements FastBuildService {

    @Override
    public boolean supportsFastBuilds(BuildSystem buildSystem, Kind kind) {
      return false;
    }

    @Override
    public Future<FastBuildInfo> createBuild(
        Label label, String blazeBinaryPath, List<String> blazeFlags) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void resetBuild(Label label) {}
  }
}

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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BuildFlagsProvider;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.DistributedExecutorSupport;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationHandlerProvider;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.targetfinder.TargetFinder;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeJavaRunProfileState}. */
@RunWith(JUnit4.class)
public class BlazeJavaRunProfileStateTest extends BlazeTestCase {

  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("", "", "", "", "", BuildSystem.Blaze);

  private BlazeCommandRunConfiguration configuration;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    ExperimentService experimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experimentService);
    applicationServices.register(TargetFinder.class, new MockTargetFinder());
    applicationServices.register(BlazeUserSettings.class, new BlazeUserSettings());
    registerExtensionPoint(BuildFlagsProvider.EP_NAME, BuildFlagsProvider.class);
    registerExtensionPoint(DistributedExecutorSupport.EP_NAME, DistributedExecutorSupport.class);
    ExtensionPointImpl<BlazeCommandRunConfigurationHandlerProvider> handlerProviderEp =
        registerExtensionPoint(
            BlazeCommandRunConfigurationHandlerProvider.EP_NAME,
            BlazeCommandRunConfigurationHandlerProvider.class);
    handlerProviderEp.registerExtension(new BlazeCommandGenericRunConfigurationHandlerProvider());

    configuration =
        new BlazeCommandRunConfigurationType().getFactory().createTemplateConfiguration(project);
  }

  @Test
  public void flagsShouldBeAppendedIfPresent() {
    configuration.setTarget(new Label("//label:rule"));
    BlazeCommandRunConfigurationCommonState handlerState =
        (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
    handlerState.setCommand(BlazeCommandName.fromString("command"));
    handlerState.setBlazeFlags(ImmutableList.of("--flag1", "--flag2"));
    assertThat(
            BlazeJavaRunProfileState.getBlazeCommand(
                    project,
                    configuration,
                    ProjectViewSet.builder().build(),
                    ImmutableList.of(),
                    false /* debug */)
                .toList())
        .isEqualTo(
            ImmutableList.of(
                "/usr/bin/blaze",
                "command",
                BlazeFlags.getToolTagFlag(),
                "--flag1",
                "--flag2",
                "--test_output=streamed",
                "--",
                "//label:rule"));
  }

  @Test
  public void debugFlagShouldBeIncludedForJavaTest() {
    configuration.setTarget(new Label("//label:rule"));
    BlazeCommandRunConfigurationCommonState handlerState =
        (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
    handlerState.setCommand(BlazeCommandName.fromString("command"));
    assertThat(
            BlazeJavaRunProfileState.getBlazeCommand(
                    project,
                    configuration,
                    ProjectViewSet.builder().build(),
                    ImmutableList.of(),
                    true /* debug */)
                .toList())
        .isEqualTo(
            ImmutableList.of(
                "/usr/bin/blaze",
                "command",
                BlazeFlags.getToolTagFlag(),
                "--java_debug",
                "--",
                "//label:rule"));
  }

  @Test
  public void debugFlagShouldBeIncludedForJavaBinary() {
    configuration.setTarget(new Label("//label:java_binary_rule"));
    BlazeCommandRunConfigurationCommonState handlerState =
        (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
    handlerState.setCommand(BlazeCommandName.fromString("command"));
    assertThat(
            BlazeJavaRunProfileState.getBlazeCommand(
                    project,
                    configuration,
                    ProjectViewSet.builder().build(),
                    ImmutableList.of(),
                    true /* debug */)
                .toList())
        .isEqualTo(
            ImmutableList.of(
                "/usr/bin/blaze",
                "command",
                BlazeFlags.getToolTagFlag(),
                "--",
                "//label:java_binary_rule",
                "--wrapper_script_flag=--debug"));
  }

  private static class MockTargetFinder extends TargetFinder {
    @Override
    public List<TargetIdeInfo> findTargets(Project project, Predicate<TargetIdeInfo> predicate) {
      return null;
    }

    @Override
    public TargetIdeInfo targetForLabel(Project project, final Label label) {
      TargetIdeInfo.Builder builder = TargetIdeInfo.builder().setLabel(label);
      if (label.targetName().toString().equals("java_binary_rule")) {
        builder.setKind(Kind.JAVA_BINARY);
      } else {
        builder.setKind(Kind.JAVA_TEST);
      }
      return builder.build();
    }
  }
}

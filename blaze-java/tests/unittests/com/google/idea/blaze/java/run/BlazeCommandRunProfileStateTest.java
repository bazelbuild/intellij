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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BuildFlagsProvider;
import com.google.idea.blaze.base.experiments.ExperimentService;
import com.google.idea.blaze.base.experiments.MockExperimentService;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.rulefinder.RuleFinder;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link BlazeCommandRunProfileState}.
 */
@RunWith(JUnit4.class)
public class BlazeCommandRunProfileStateTest extends BlazeTestCase {

  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS = new BlazeImportSettings("", "", "", "", "", BuildSystem.Blaze);

  private BlazeCommandRunConfiguration configuration;

  @Override
  protected void initTest(@NotNull Container applicationServices, @NotNull Container projectServices) {
    projectServices.register(BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    configuration = new BlazeCommandRunConfigurationType().getFactory().createTemplateConfiguration(project);

    ExperimentService experimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experimentService);
    applicationServices.register(RuleFinder.class, new MockRuleFinder());
    applicationServices.register(BlazeUserSettings.class, new BlazeUserSettings());
    registerExtensionPoint(BuildFlagsProvider.EP_NAME, BuildFlagsProvider.class);
  }

  @Test
  public void flagsShouldBeAppendedIfPresent() {
    configuration.setTarget(new Label("//label:rule"));
    configuration.setCommand(BlazeCommandName.fromString("command"));
    configuration.setBlazeFlags(ImmutableList.of("--flag1", "--flag2"));
    assertThat(
      BlazeCommandRunProfileState.getBlazeCommand(project, configuration, ProjectViewSet.builder().build(), false /* debug */).toList())
      .isEqualTo(ImmutableList.of(
        "/usr/bin/blaze",
        "command",
        BlazeFlags.getToolTagFlag(),
        "--flag1",
        "--flag2",
        "--",
        "//label:rule"
      ));
  }

  @Test
  public void debugFlagShouldBeIncludedForJavaTest() {
    configuration.setTarget(new Label("//label:rule"));
    configuration.setCommand(BlazeCommandName.fromString("command"));
    assertThat(
      BlazeCommandRunProfileState.getBlazeCommand(project, configuration, ProjectViewSet.builder().build(), true /* debug */).toList())
      .isEqualTo(ImmutableList.of(
        "/usr/bin/blaze",
        "command",
        BlazeFlags.getToolTagFlag(),
        "--java_debug",
        "--",
        "//label:rule"
      ));
  }

  @Test
  public void debugFlagShouldBeIncludedForJavaBinary() {
    configuration.setTarget(new Label("//label:java_binary_rule"));
    configuration.setCommand(BlazeCommandName.fromString("command"));
    assertThat(
      BlazeCommandRunProfileState.getBlazeCommand(project, configuration, ProjectViewSet.builder().build(), true /* debug */).toList())
      .isEqualTo(ImmutableList.of(
        "/usr/bin/blaze",
        "command",
        BlazeFlags.getToolTagFlag(),
        "--",
        "//label:java_binary_rule",
        "--debug"
      ));
  }

  private static class MockRuleFinder extends RuleFinder {
    @Override
    public List<RuleIdeInfo> findRules(Project project, Predicate<RuleIdeInfo> predicate) {
      return null;
    }

    @Override
    public RuleIdeInfo ruleForTarget(Project project, final Label target) {
      RuleIdeInfo.Builder builder = RuleIdeInfo.builder().setLabel(target);
      if (target.ruleName().toString().equals("java_binary_rule")) {
        builder.setKind(Kind.JAVA_BINARY);
      } else {
        builder.setKind(Kind.JAVA_TEST);
      }
      return builder.build();
    }
  }
}

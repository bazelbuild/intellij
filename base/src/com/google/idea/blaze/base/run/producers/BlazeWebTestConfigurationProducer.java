/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.targetmaps.ReverseDependencyMap;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.ListSelectionModel;

/**
 * Common run configuration producer for web_test wrapped language-specific tests.
 *
 * <p>Will create the test configuration using the regular per-language producer, then swap out the
 * test target and kind with the web test that wraps the underlying test.
 *
 * <p>Pops up a dialog to choose a specific browser/platform if multiple are declared.
 */
public abstract class BlazeWebTestConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {
  private final ImmutableList<
          Class<? extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration>>>
      producers;

  protected BlazeWebTestConfigurationProducer(
      ImmutableList<Class<? extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration>>>
          producers) {
    super(BlazeCommandRunConfigurationType.getInstance());
    this.producers = producers;
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    if (producers.stream()
        .map(EP_NAME::findExtension)
        .noneMatch(
            producer -> producer.doSetupConfigFromContext(configuration, context, sourceElement))) {
      return false;
    }

    TargetExpression targetExpression = configuration.getTarget();
    if (!(targetExpression instanceof Label)) {
      return false;
    }
    Label label = (Label) targetExpression;

    Project project = context.getProject();
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return false;
    }
    TargetMap targetMap = projectData.getTargetMap();

    // Wrong kind to prevent the language-specific debug runner from interfering.
    // The target will be updated to match the kind at the end.
    configuration.setTargetInfo(
        TargetInfo.builder(label, GenericBlazeRules.RuleTypes.WEB_TEST.toString()).build());
    return ReverseDependencyMap.get(project).get(TargetKey.forPlainTarget(label)).stream()
        .map(targetMap::get)
        .filter(Objects::nonNull)
        .map(TargetIdeInfo::getKind)
        .anyMatch(kind -> kind == GenericBlazeRules.RuleTypes.WEB_TEST.getKind());
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    return producers.stream()
            .map(EP_NAME::findExtension)
            .anyMatch(producer -> producer.doIsConfigFromContext(configuration, context))
        && configuration.getTargetKind() == GenericBlazeRules.RuleTypes.WEB_TEST.getKind();
  }

  @Override
  public void onFirstRun(
      ConfigurationFromContext configurationFromContext,
      ConfigurationContext context,
      Runnable startRunnable) {
    if (!(configurationFromContext.getConfiguration() instanceof BlazeCommandRunConfiguration)) {
      return;
    }
    BlazeCommandRunConfiguration configuration =
        (BlazeCommandRunConfiguration) configurationFromContext.getConfiguration();
    Label wrappedTest = (Label) configuration.getTarget();
    if (wrappedTest == null) {
      return;
    }
    List<Label> webTestWrappers = getWebTestWrappers(context.getProject(), wrappedTest);
    if (webTestWrappers.isEmpty()) {
      return;
    }
    if (webTestWrappers.size() == 1) {
      updateConfigurationAndRun(configuration, wrappedTest, webTestWrappers.get(0), startRunnable);
      return;
    }
    chooseWebTest(configuration, context, startRunnable, wrappedTest, webTestWrappers);
  }

  private static List<Label> getWebTestWrappers(Project project, Label wrappedTest) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return ImmutableList.of();
    }
    TargetMap targetMap = projectData.getTargetMap();
    return ReverseDependencyMap.get(project).get(TargetKey.forPlainTarget(wrappedTest)).stream()
        .map(targetMap::get)
        .filter(Objects::nonNull)
        .filter(t -> t.getKind() == GenericBlazeRules.RuleTypes.WEB_TEST.getKind())
        .map(TargetIdeInfo::getKey)
        .map(TargetKey::getLabel)
        .sorted()
        .collect(Collectors.toList());
  }

  private static void chooseWebTest(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Runnable startRunnable,
      Label wrappedTest,
      List<Label> webTestWrappers) {
    JBList<Label> list = new JBList<>(webTestWrappers);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JBPopupFactory.getInstance()
        .createListPopupBuilder(list)
        .setTitle("Choose web test to run")
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setCancelOnWindowDeactivation(false)
        .setItemChoosenCallback(
            () ->
                updateConfigurationAndRun(
                    configuration, wrappedTest, list.getSelectedValue(), startRunnable))
        .createPopup()
        .showInBestPositionFor(context.getDataContext());
  }

  private static void updateConfigurationAndRun(
      BlazeCommandRunConfiguration configuration,
      Label wrappedTest,
      @Nullable Label wrapperTest,
      Runnable startRunnable) {
    if (wrapperTest == null) {
      return;
    }
    configuration.setTarget(wrapperTest);
    updateConfigurationName(configuration, wrappedTest, wrapperTest);
    startRunnable.run();
  }

  private static void updateConfigurationName(
      BlazeCommandRunConfiguration configuration, Label wrappedTest, Label wrapperTest) {
    String wrappedTestSuffix = "_wrapped_test";
    String wrappedName = wrappedTest.targetName().toString();
    if (!wrappedName.endsWith(wrappedTestSuffix)) {
      return;
    }
    String baseName = wrappedName.substring(0, wrappedName.lastIndexOf(wrappedTestSuffix)) + '_';
    String wrapperName = wrapperTest.targetName().toString();
    if (!wrapperName.startsWith(baseName)) {
      return;
    }
    String platform = wrapperName.substring(baseName.length());
    configuration.setName(configuration.getName() + " on " + platform);
  }

  @Override
  public boolean shouldReplace(ConfigurationFromContext self, ConfigurationFromContext other) {
    return producers.stream().anyMatch(other::isProducedBy);
  }
}

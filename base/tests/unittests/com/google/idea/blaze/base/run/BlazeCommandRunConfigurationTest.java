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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider;
import com.google.idea.blaze.base.run.targetfinder.TargetFinder;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeCommandRunConfiguration}. */
@RunWith(JUnit4.class)
public class BlazeCommandRunConfigurationTest extends BlazeTestCase {
  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("", "", "", "", "", Blaze.BuildSystem.Blaze);

  private final BlazeCommandRunConfigurationType type = new BlazeCommandRunConfigurationType();
  private BlazeCommandRunConfiguration configuration;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);

    applicationServices.register(UISettings.class, new UISettings());
    projectServices.register(BlazeImportSettingsManager.class, new BlazeImportSettingsManager());
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(TargetFinder.class, new MockTargetFinder());
    ExtensionPointImpl<BlazeCommandRunConfigurationHandlerProvider> handlerProviderEp =
        registerExtensionPoint(
            BlazeCommandRunConfigurationHandlerProvider.EP_NAME,
            BlazeCommandRunConfigurationHandlerProvider.class);
    handlerProviderEp.registerExtension(new MockBlazeCommandRunConfigurationHandlerProvider());

    this.configuration = this.type.getFactory().createTemplateConfiguration(project);
  }

  @Test
  public void readAndWriteShouldMatch() throws Exception {
    Label label = new Label("//package:rule");
    configuration.setTarget(label);

    Element element = new Element("test");
    configuration.writeExternal(element);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(project);
    readConfiguration.readExternal(element);

    assertThat(readConfiguration.getTarget()).isEqualTo(label);
  }

  @Test
  public void readAndWriteShouldHandleNulls() throws Exception {
    Element element = new Element("test");
    configuration.writeExternal(element);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(project);
    readConfiguration.readExternal(element);

    assertThat(readConfiguration.getTarget()).isEqualTo(configuration.getTarget());
  }

  private static class MockTargetFinder extends TargetFinder {
    @Override
    public List<TargetIdeInfo> findTargets(Project project, Predicate<TargetIdeInfo> predicate) {
      return ImmutableList.of();
    }
  }
}

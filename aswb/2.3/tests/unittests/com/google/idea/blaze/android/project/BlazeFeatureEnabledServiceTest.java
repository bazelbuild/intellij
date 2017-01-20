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
package com.google.idea.blaze.android.project;

import static com.google.common.truth.Truth.THROW_ASSERTION_ERROR;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.android.tools.idea.project.FeatureEnableService;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.settings.BlazeAndroidUserSettings;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManagerLegacy;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.common.experiments.ExperimentService;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link BlazeFeatureEnableService}. */
@RunWith(JUnit4.class)
public class BlazeFeatureEnabledServiceTest extends BlazeTestCase {
  private MockExperimentService experimentService;
  private BlazeAndroidUserSettings userSettings;
  private MockBlazeProjectDataManager projectDataManager;
  private FeatureEnableService featureEnableService;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    experimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experimentService);

    userSettings = new BlazeAndroidUserSettings();
    applicationServices.register(BlazeAndroidUserSettings.class, userSettings);

    projectDataManager = new MockBlazeProjectDataManager();
    projectServices.register(BlazeProjectDataManager.class, projectDataManager);

    BlazeImportSettingsManager importSettingsManager = new BlazeImportSettingsManager(project);
    importSettingsManager.setImportSettings(
        new BlazeImportSettings(null, null, null, null, null, BuildSystem.Blaze));
    projectServices.register(BlazeImportSettingsManager.class, importSettingsManager);
    projectServices.register(
        BlazeImportSettingsManagerLegacy.class, new BlazeImportSettingsManagerLegacy(project));

    ExtensionPoint<FeatureEnableService> extensionPoint =
        registerExtensionPoint(
            ExtensionPointName.create("com.android.project.featureEnableService"),
            FeatureEnableService.class);
    extensionPoint.registerExtension(new BlazeFeatureEnableService());

    featureEnableService = FeatureEnableService.getInstance(project);
  }

  @Test
  public void testIsBlazeFeatureEnableService() {
    assertThat(featureEnableService).isInstanceOf(BlazeFeatureEnableService.class);
  }

  @Test
  public void testIsNotBlazeFeatureEnableService() {
    BlazeImportSettingsManager.getInstance(project).loadState(null);
    assertThat(FeatureEnableService.getInstance(project))
        .isNotInstanceOf(BlazeFeatureEnableService.class);
  }

  @Test
  public void testGetLayoutEditorEnabled() {
    for (boolean experimentEnabled : ImmutableList.of(true, false)) {
      for (boolean settingEnabled : ImmutableList.of(true, false)) {
        for (boolean projectDataReady : ImmutableList.of(true, false)) {
          userSettings.setUseLayoutEditor(settingEnabled);
          experimentService.setEnableLayoutEditor(experimentEnabled);
          projectDataManager.setBlazeProjectData(
              projectDataReady ? mock(BlazeProjectData.class) : null);
          assertThat(featureEnableService.isLayoutEditorEnabled(project))
              .isEqualTo(settingEnabled && experimentEnabled && projectDataReady);
        }
      }
    }
  }

  private static class MockBlazeProjectDataManager implements BlazeProjectDataManager {
    private BlazeProjectData blazeProjectData;

    public void setBlazeProjectData(BlazeProjectData blazeProjectData) {
      this.blazeProjectData = blazeProjectData;
    }

    @Nullable
    @Override
    public BlazeProjectData getBlazeProjectData() {
      return blazeProjectData;
    }
  }

  private static class MockExperimentService implements ExperimentService {
    private boolean enableLayoutEditor;

    public void setEnableLayoutEditor(boolean enableLayoutEditor) {
      this.enableLayoutEditor = enableLayoutEditor;
    }

    @Override
    public boolean getExperiment(String key, boolean defaultValue) {
      assertThat(key).isEqualTo("enable.layout.editor");
      return enableLayoutEditor;
    }

    @Nullable
    @Override
    public String getExperimentString(String key, @Nullable String defaultValue) {
      THROW_ASSERTION_ERROR.fail("Should not be called.");
      return null;
    }

    @Override
    public int getExperimentInt(String key, int defaultValue) {
      THROW_ASSERTION_ERROR.fail("Should not be called.");
      return 0;
    }

    @Override
    public void startExperimentScope() {}

    @Override
    public void endExperimentScope() {}
  }
}

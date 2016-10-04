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
package com.google.idea.blaze.android.run.binary;

import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.UnknownConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import icons.AndroidIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/**
 * A type for Android application run configurations adapted specifically to run android_binary
 * targets.
 *
 * @deprecated See {@link com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType}. Retained
 *     in 1.9 for legacy purposes, to allow existing BlazeAndroidBinaryRunConfigurations to be
 *     updated to BlazeCommandRunConfigurations. Intended to be removed in 2.1.
 */
// Hack: extend UnknownConfigurationType to completely hide it in the Run/Debug Configurations UI.
@Deprecated
public class BlazeAndroidBinaryRunConfigurationType extends UnknownConfigurationType {
  private final BlazeAndroidBinaryRunConfigurationFactory factory =
      new BlazeAndroidBinaryRunConfigurationFactory(this);

  static class BlazeAndroidBinaryRunConfigurationFactory extends ConfigurationFactory {

    protected BlazeAndroidBinaryRunConfigurationFactory(@NotNull ConfigurationType type) {
      super(type);
    }

    @Override
    public String getName() {
      // Used to look up this ConfigurationFactory.
      // Preserve value so legacy configurations can be loaded.
      return Blaze.defaultBuildSystemName() + " Android Binary";
    }

    @Override
    @NotNull
    public BlazeCommandRunConfiguration createTemplateConfiguration(@NotNull Project project) {
      // Create a BlazeCommandRunConfiguration instead, to update legacy configurations.
      return BlazeCommandRunConfigurationType.getInstance()
          .getFactory()
          .createTemplateConfiguration(project);
    }

    @Override
    public boolean canConfigurationBeSingleton() {
      return false;
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      return false;
    }

    @Override
    public void configureBeforeRunTaskDefaults(
        Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
      // Removed BlazeAndroidBeforeRunTaskProvider; this method won't be called anymore anyhow.
    }

    @Override
    public boolean isConfigurationSingletonByDefault() {
      return false;
    }
  }

  @NotNull
  public static BlazeAndroidBinaryRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(
        BlazeAndroidBinaryRunConfigurationType.class);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return "Legacy " + Blaze.defaultBuildSystemName() + " Android Binary";
  }

  @Override
  public String getConfigurationTypeDescription() {
    return "Launch/debug configuration for android_binary rules. "
        + "Use Blaze Command instead; this legacy configuration type is being removed.";
  }

  @Override
  public Icon getIcon() {
    return AndroidIcons.Android;
  }

  @Override
  @NotNull
  public String getId() {
    // Used to look up this ConfigurationType.
    // Preserve value so legacy configurations can be loaded.
    return "BlazeAndroidBinaryRunConfigurationType";
  }

  @Override
  public BlazeAndroidBinaryRunConfigurationFactory[] getConfigurationFactories() {
    return new BlazeAndroidBinaryRunConfigurationFactory[] {factory};
  }
}

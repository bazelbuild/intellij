/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.cppimpl.debug;

import com.android.tools.idea.run.ValidationError;
import com.android.tools.ndk.run.attach.AndroidNativeAttachConfiguration;
import com.android.tools.ndk.run.editor.NativeAndroidDebugger;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import icons.BlazeIcons;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension of {@link NativeAndroidDebugger} with the following key differences compared to {@link
 * NativeAndroidDebugger}.
 *
 * <ul>
 *   <li>Overrides {@link #supportsProject} so native debugger is only enabled for native support is
 *       enabled.
 * </ul>
 */
public class BlazeNativeAndroidDebuggerBase extends NativeAndroidDebugger {
  /**
   * This ID needs to be lexicographically larger than "Java" so it come after the "Java" debugger
   * when sorted lexicographically in the "Attach Debugger to Android Process" dialog. See {@link
   * org.jetbrains.android.actions.AndroidProcessChooserDialog#populateDebuggerTypeCombo}.
   */
  public static final String ID = "Native" + Blaze.defaultBuildSystemName();

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getDisplayName() {
    return "Native Only";
  }

  @Override
  public boolean supportsProject(Project project) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return blazeProjectData != null
        && blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.C);
  }

  @Override
  @Nullable
  public Module findModuleForProcess(Project project, String packageName) {
    // Blaze plugin uses the workspace module for all run/debug related tasks.
    return ModuleManager.getInstance(project)
        .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
  }

  @Override
  protected void handleModuleNotFound(String packageName) {
    // Can only happen if the workspace module is missing.  The workspace module should always
    // be present as long as there's been a successful sync.
    ApplicationManager.getApplication()
        .invokeLater(
            () ->
                Messages.showErrorDialog(
                    "Module information missing.\nPlease try again after a successful sync.",
                    "Module Information Missing"));
  }

  /**
   * Extension of {@link AndroidNativeAttachConfiguration} with the following specializations:
   *
   * <ul>
   *   <li>Performs NO-OP during run configuration validation. The inherited {@link #validate}
   *       method contains validations specific to gradle based projects, such as special checks for
   *       library projects and outdated manifest checks that don't work for blaze projects. See
   *       implementation of {@link com.android.tools.idea.run.AndroidRunConfigurationBase#validate}
   *       for more details.
   *   <li>Returns empty list for before run tasks. The inherited {@link #getBeforeRunTasks()}
   *       returns gradle specific before-run tasks that aren't applicable to blaze projects.
   * </ul>
   *
   * Currently {@link #validate} doesn't perform any validations, but as common errors and use-cases
   * are surfaced we should add them to the method.
   */
  protected static class BlazeAndroidNativeAttachConfiguration
      extends AndroidNativeAttachConfiguration {
    BlazeAndroidNativeAttachConfiguration(Project project, ConfigurationFactory factory) {
      super(project, factory);
    }

    // Only required for #api203
    @Nullable
    public String getPackageNameOverride() {
      // "package name override" is not required in blaze projects.
      return null;
    }

    /** Don't validate anything. */
    @Override
    public List<ValidationError> validate(@Nullable Executor executor) {
      return Collections.emptyList();
    }

    /** Overridden to stop providing Grade tasks from superclasses */
    @Override
    public List<BeforeRunTask<?>> getBeforeRunTasks() {
      return Collections.emptyList();
    }
  }

  /** Configuration type for {@link BlazeAndroidNativeAttachConfiguration}. */
  protected static class BlazeAndroidNativeAttachConfigurationType implements ConfigurationType {
    @NotNull
    @Override
    public String getDisplayName() {
      return "Android Native Attach";
    }

    @Nls
    @Override
    public String getConfigurationTypeDescription() {
      return "Android Native Attach Configuration";
    }

    @Override
    public Icon getIcon() {
      return BlazeIcons.Logo;
    }

    @NotNull
    @Override
    public String getId() {
      return "BlazeAndroidNativeAttachConfigurationType";
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
      return new ConfigurationFactory[] {new Factory()};
    }

    /** Makes new {@link BlazeAndroidNativeAttachConfiguration}. */
    static class Factory extends ConfigurationFactory {
      protected Factory() {
        super(new BlazeAndroidNativeAttachConfigurationType());
      }

      @Override
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new BlazeAndroidNativeAttachConfiguration(project, this);
      }
    }
  }
}

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
package com.google.idea.blaze.android.cppimpl.debug;

import com.android.ddmlib.Client;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.ndk.run.attach.AndroidNativeAttachConfiguration;
import com.android.tools.ndk.run.editor.NativeAndroidDebugger;
import com.android.tools.ndk.run.editor.NativeAndroidDebuggerState;
import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.android.projectsystem.BlazeModuleSystem;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationDebuggerManager;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import icons.BlazeIcons;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension of {@link NativeAndroidDebugger} with the following key differences compared to {@link
 * NativeAndroidDebugger}.
 *
 * <ul>
 *   <li>Sets blaze working directory for source file resolution. See {@link
 *       #createRunnerAndConfigurationSettings}.
 *   <li>Creates a run-config setting using {@link BlazeAndroidNativeAttachConfiguration} instead of
 *       {@link AndroidNativeAttachConfiguration} to override counterproductive validations.
 *   <li>Use {@link BlazeModuleSystem#getPackageName()} during compatible module search. See {@link
 *       #findModuleForProcess}.
 * </ul>
 *
 * <p>See {@link BlazeAndroidRunConfigurationDebuggerManager#getAndroidDebuggerState}.
 */
public class BlazeNativeAndroidDebugger extends NativeAndroidDebugger {
  public static final String ID = Blaze.defaultBuildSystemName() + "Native";

  @VisibleForTesting
  @Override
  @Nullable
  public Module findModuleForProcess(Project project, String packageName) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (AndroidFacet.getInstance(module) == null) {
        continue; // A module must have an attached AndroidFacet to have a package name.
      }

      BlazeModuleSystem moduleSystem = BlazeModuleSystem.getInstance(module);
      String modulePackageName = ReadAction.compute(() -> moduleSystem.getPackageName());
      if (modulePackageName != null && modulePackageName.equals(packageName)) {
        return module;
      }
    }
    return null;
  }

  @Override
  protected void handleModuleNotFound(String packageName) {
    String noMatchingModuleMsg =
        String.format(
            "No android target with application ID (or package name) '%s' found in the current"
                + " project.\n"
                + "Please ensure a matching target is included in project view (Blaze > Project"
                + " > Open Project View File).",
            packageName);
    ApplicationManager.getApplication()
        .invokeLater(
            () ->
                Messages.showErrorDialog(noMatchingModuleMsg, "No Target Matching Debug Process"));
  }

  @NotNull
  @Override
  protected RunnerAndConfigurationSettings createRunnerAndConfigurationSettings(
      @NotNull Project project,
      @NotNull Module module,
      @NotNull Client client,
      @Nullable AndroidDebuggerState inputState) {
    String runConfigurationName =
        String.format(
            "%s %s Debugger (%d)",
            Blaze.getBuildSystem(project).getName(),
            getDisplayName(),
            client.getClientData().getPid());
    RunnerAndConfigurationSettings runSettings =
        RunManager.getInstance(project)
            .createConfiguration(
                runConfigurationName, new BlazeAndroidNativeAttachConfigurationType.Factory());
    BlazeAndroidNativeAttachConfiguration configuration =
        (BlazeAndroidNativeAttachConfiguration) runSettings.getConfiguration();
    configuration.setClient(client);
    configuration.getAndroidDebuggerContext().setDebuggerType(getId());
    configuration.getConfigurationModule().setModule(module);
    configuration.setConsoleProvider(getConsoleProvider());

    // TODO(b/145707569): Copy debugger settings from inputState to state. See
    // NativeAndroidDebugger.
    AndroidDebuggerState state =
        configuration.getAndroidDebuggerContext().getAndroidDebuggerState();
    if (state instanceof NativeAndroidDebuggerState) {
      NativeAndroidDebuggerState nativeState = (NativeAndroidDebuggerState) state;
      nativeState.setWorkingDir(WorkspaceRoot.fromProject(project).directory().getPath());
    }
    return runSettings;
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
  private static class BlazeAndroidNativeAttachConfiguration
      extends AndroidNativeAttachConfiguration {
    BlazeAndroidNativeAttachConfiguration(Project project, ConfigurationFactory factory) {
      super(project, factory);
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
  private static class BlazeAndroidNativeAttachConfigurationType implements ConfigurationType {
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

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Native Only";
  }

  @Override
  public boolean supportsProject(@NotNull Project project) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return blazeProjectData != null
        && blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.C);
  }
}

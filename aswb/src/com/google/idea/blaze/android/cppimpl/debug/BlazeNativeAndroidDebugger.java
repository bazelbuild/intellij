/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.cppimpl.debug;

import com.android.ddmlib.Client;
import com.android.tools.idea.run.ApkProviderUtil;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.ndk.run.attach.AndroidNativeAttachConfiguration;
import com.android.tools.ndk.run.editor.NativeAndroidDebugger;
import com.android.tools.ndk.run.editor.NativeAndroidDebuggerState;
import com.google.idea.blaze.android.run.attach.BlazeAndroidNativeAttachConfigurationFactory;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * {@link NativeAndroidDebugger} with slight modifications to work for attaching from a blaze
 * project. Code duplication is required to fix the issue in the current release without upstream
 * changes.
 *
 * <p>TODO: refactor upstream to remove code duplication.
 */
public class BlazeNativeAndroidDebugger extends NativeAndroidDebugger {
  @Override
  public void attachToClient(Project project, Client client) {
    final String clientDescr = client.getClientData().getClientDescription();
    Module module = null;
    final List<AndroidFacet> facets =
        ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    for (AndroidFacet facet : facets) {
      try {
        final String packageName = ApkProviderUtil.computePackageName(facet);
        if (clientDescr.startsWith(packageName)) {
          module = facet.getModule();
          break;
        }
      } catch (ApkProvisionException ignored) {
        // ignored
      }
    }
    if (module == null) {
      throw new RuntimeException("Cannot find module by package name");
    }

    if (hasExistingSession(project, client)) {
      return;
    }

    // Detach any existing JDWP debug session - reusing an existing session is troublesome
    // because we need to setup a custom XDebugProcess.
    DebuggerSession debuggerSession = findJdwpDebuggerSession(project, getClientDebugPort(client));
    if (debuggerSession != null) {
      debuggerSession.getProcess().stop(false);
    }

    // Create run configuration
    // TODO: Important modification here. Make sure to keep this in refactor.
    // We need a custom BlazeAndroidNativeAttachConfiguration to skip a bunch of launch checks so
    // validate passes.
    ConfigurationFactory factory = BlazeAndroidNativeAttachConfigurationFactory.getInstance();
    String runConfigurationName =
        String.format("Android Native Debugger (%d)", client.getClientData().getPid());
    RunnerAndConfigurationSettings runSettings =
        RunManager.getInstance(project).createRunConfiguration(runConfigurationName, factory);

    AndroidNativeAttachConfiguration configuration =
        (AndroidNativeAttachConfiguration) runSettings.getConfiguration();
    configuration.setClient(client);
    configuration.getAndroidDebuggerContext().setDebuggerType(getId());
    configuration.getConfigurationModule().setModule(module);

    // TODO: Important modification here. Make sure to keep this in refactor.
    // We need to set the correct working dir to find sources while debugging.
    // See BlazeAndroidRunConfigurationDebuggerManager#getAndroidDebuggerState
    AndroidDebuggerState state =
        configuration.getAndroidDebuggerContext().getAndroidDebuggerState();
    if (state instanceof NativeAndroidDebuggerState) {
      NativeAndroidDebuggerState nativeState = (NativeAndroidDebuggerState) state;
      nativeState.setWorkingDir(WorkspaceRoot.fromProject(project).directory().getPath());
    }

    ProgramRunnerUtil.executeConfiguration(
        project, runSettings, DefaultDebugExecutor.getDebugExecutorInstance());
  }
}

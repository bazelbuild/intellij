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
package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.run.DeviceCount;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.run.DeployTargetProviderCompat;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;

/** Manages deploy target state for run configurations. */
public abstract class BlazeAndroidRunConfigurationDeployTargetManagerBase
    implements JDOMExternalizable {
  private final boolean isAndroidTest;
  private final List<DeployTargetProvider> deployTargetProviders;
  private final Map<String, DeployTargetState> deployTargetStates;

  public BlazeAndroidRunConfigurationDeployTargetManagerBase(boolean isAndroidTest) {
    this.isAndroidTest = isAndroidTest;
    this.deployTargetProviders = DeployTargetProvider.getProviders();

    ImmutableMap.Builder<String, DeployTargetState> builder = ImmutableMap.builder();
    for (DeployTargetProvider provider : deployTargetProviders) {
      builder.put(provider.getId(), provider.createState());
    }
    this.deployTargetStates = builder.build();
  }

  public List<ValidationError> validate(AndroidFacet facet) {
    return getCurrentDeployTargetState().validate(facet);
  }

  @Nullable
  DeployTarget getDeployTarget(
      Executor executor, ExecutionEnvironment env, AndroidFacet facet, int runConfigId)
      throws ExecutionException {
    DeployTargetProvider currentTargetProvider = getCurrentDeployTargetProvider();

    DeployTarget deployTarget;
    if (currentTargetProvider.requiresRuntimePrompt()) {
      deployTarget =
          currentTargetProvider.showPrompt(
              executor,
              env,
              facet,
              getDeviceCount(),
              isAndroidTest,
              deployTargetStates,
              runConfigId,
              (device) -> LaunchCompatibility.YES);
      if (deployTarget == null) {
        return null;
      }
    } else {
      deployTarget =
          DeployTargetProviderCompat.getDeployTarget(currentTargetProvider, env.getProject());
    }

    return deployTarget;
  }

  DeployTargetState getCurrentDeployTargetState() {
    DeployTargetProvider currentTarget = getCurrentDeployTargetProvider();
    return deployTargetStates.get(currentTarget.getId());
  }

  // #api3.5
  protected abstract DeployTargetProvider getCurrentDeployTargetProvider();

  @Nullable
  protected DeployTargetProvider getDeployTargetProvider(String id) {
    for (DeployTargetProvider target : deployTargetProviders) {
      if (target.getId().equals(id)) {
        return target;
      }
    }
    return null;
  }

  DeviceCount getDeviceCount() {
    return DeviceCount.SINGLE;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    for (DeployTargetState state : deployTargetStates.values()) {
      DefaultJDOMExternalizer.readExternal(state, element);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    for (DeployTargetState state : deployTargetStates.values()) {
      DefaultJDOMExternalizer.writeExternal(state, element);
    }
  }
}

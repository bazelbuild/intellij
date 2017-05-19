/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.python.run.producers;

import com.google.common.collect.ImmutableCollection;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyFile;
import java.io.File;
import javax.annotation.Nullable;

/** Producer for run configurations related to py_binary main classes in Blaze. */
public class BlazePyBinaryConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public BlazePyBinaryConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {

    Location<?> location = context.getLocation();
    if (location == null) {
      return false;
    }
    PsiElement element = location.getPsiElement();
    PsiFile file = element.getContainingFile();
    if (!(file instanceof PyFile)) {
      return false;
    }
    Label binaryTarget = getTargetLabel(file);
    if (binaryTarget == null) {
      return false;
    }
    configuration.setTarget(binaryTarget);
    sourceElement.set(file);

    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    handlerState.getCommandState().setCommand(BlazeCommandName.RUN);
    configuration.setGeneratedName();
    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {

    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null
        || !BlazeCommandName.RUN.equals(handlerState.getCommandState().getCommand())) {
      return false;
    }

    Location<?> location = context.getLocation();
    if (location == null) {
      return false;
    }
    PsiElement element = location.getPsiElement();
    PsiFile file = element.getContainingFile();
    if (!(file instanceof PyFile)) {
      return false;
    }
    Label binaryTarget = getTargetLabel(file);
    if (binaryTarget == null) {
      return false;
    }
    return binaryTarget.equals(configuration.getTarget());
  }

  @Nullable
  private static Label getTargetLabel(PsiFile psiFile) {
    VirtualFile vf = psiFile.getVirtualFile();
    if (vf == null) {
      return null;
    }
    File file = new File(vf.getPath());
    ImmutableCollection<TargetKey> targetKeys =
        SourceToTargetMap.getInstance(psiFile.getProject()).getRulesForSourceFile(file);
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(psiFile.getProject()).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }

    String fileName = FileUtil.getNameWithoutExtension(file);
    for (TargetKey key : targetKeys) {
      TargetIdeInfo target = blazeProjectData.targetMap.get(key);
      if (target == null) {
        continue;
      }
      if (target.kind == Kind.PY_BINARY || target.kind == Kind.PY_APPENGINE_BINARY) {
        // The 'main' attribute isn't exposed, so only suggest a binary if the name matches
        if (key.label.targetName().toString().equals(fileName)) {
          return key.label;
        }
      }
    }
    return null;
  }
}

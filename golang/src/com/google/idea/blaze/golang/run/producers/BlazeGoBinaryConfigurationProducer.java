/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.golang.run.producers;

import com.goide.execution.GoRunUtil;
import com.goide.psi.GoFile;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.util.Objects;
import javax.annotation.Nullable;

/** Go-specific handler for {@link BlazeCommandRunConfiguration}s. */
public class BlazeGoBinaryConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public BlazeGoBinaryConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    PsiFile file = getMainFile(context);
    if (file == null) {
      return false;
    }
    TargetInfo binaryTarget = getTargetLabel(file);
    if (binaryTarget == null) {
      return false;
    }
    configuration.setTargetInfo(binaryTarget);
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
    PsiFile file = getMainFile(context);
    if (file == null) {
      return false;
    }
    TargetInfo binaryTarget = getTargetLabel(file);
    return binaryTarget != null && binaryTarget.label.equals(configuration.getTarget());
  }

  @Nullable
  private static PsiFile getMainFile(ConfigurationContext context) {
    PsiElement element = context.getPsiLocation();
    if (element == null) {
      return null;
    }
    PsiFile file = element.getContainingFile();
    if (file instanceof GoFile && GoRunUtil.isMainGoFile(file)) {
      return file;
    }
    return null;
  }

  @Nullable
  private static TargetInfo getTargetLabel(PsiFile psiFile) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(psiFile.getProject()).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    VirtualFile vf = psiFile.getVirtualFile();
    if (vf == null) {
      return null;
    }
    File file = new File(vf.getPath());
    return SourceToTargetMap.getInstance(psiFile.getProject()).getRulesForSourceFile(file).stream()
        .map(projectData.getTargetMap()::get)
        .filter(Objects::nonNull)
        .filter(t -> t.getKind().languageClass.equals(LanguageClass.GO))
        .filter(t -> t.getKind().ruleType.equals(RuleType.BINARY))
        .map(TargetIdeInfo::toTargetInfo)
        .findFirst()
        .orElse(null);
  }
}

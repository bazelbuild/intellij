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
package com.google.idea.blaze.java.run.producers;

import com.google.common.collect.ImmutableCollection;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.RuleMap;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.rulemaps.SourceToRuleMap;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationHandler;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.run.RunUtil;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiMethodUtil;
import java.io.File;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/** Creates run configurations for Java main classes sourced by java_binary targets. */
public class BlazeJavaMainClassRunConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public BlazeJavaMainClassRunConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    PsiClass mainClass = getMainClass(context);
    if (mainClass == null) {
      return false;
    }
    // Try setting source element to a main method so ApplicationConfigurationProducer
    // can't override our configuration by producing a more specific one.
    PsiMethod mainMethod = PsiMethodUtil.findMainMethod(mainClass);
    if (mainMethod == null) {
      sourceElement.set(mainClass);
    } else {
      sourceElement.set(mainMethod);
    }

    Label label = getRuleLabel(context.getProject(), mainClass);
    if (label == null) {
      return false;
    }
    configuration.setTarget(label);
    BlazeCommandGenericRunConfigurationHandler handler =
        configuration.getHandlerIfType(BlazeCommandGenericRunConfigurationHandler.class);
    if (handler == null) {
      return false;
    }
    handler.setCommand(BlazeCommandName.RUN);
    configuration.setGeneratedName();
    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    BlazeCommandGenericRunConfigurationHandler handler =
        configuration.getHandlerIfType(BlazeCommandGenericRunConfigurationHandler.class);
    if (handler == null) {
      return false;
    }
    if (!Objects.equals(handler.getCommand(), BlazeCommandName.RUN)) {
      return false;
    }
    PsiClass mainClass = getMainClass(context);
    if (mainClass == null) {
      return false;
    }
    Label label = getRuleLabel(context.getProject(), mainClass);
    if (label == null) {
      return false;
    }
    return Objects.equals(configuration.getTarget(), label);
  }

  @Nullable
  private static PsiClass getMainClass(ConfigurationContext context) {
    Location location = context.getLocation();
    if (location == null) {
      return null;
    }
    location = JavaExecutionUtil.stepIntoSingleClass(location);
    if (location == null) {
      return null;
    }
    PsiElement element = location.getPsiElement();
    if (!element.isPhysical()) {
      return null;
    }
    return ApplicationConfigurationType.getMainClass(element);
  }

  @Nullable
  private static Label getRuleLabel(Project project, PsiClass mainClass) {
    File mainClassFile = RunUtil.getFileForClass(mainClass);
    ImmutableCollection<Label> labels =
        SourceToRuleMap.getInstance(project).getTargetsForSourceFile(mainClassFile);
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    RuleMap ruleMap = blazeProjectData.ruleMap;
    for (Label label : labels) {
      RuleIdeInfo rule = ruleMap.get(label);
      if (rule.kind == Kind.JAVA_BINARY) {
        // Best-effort guess: the main_class attribute isn't exposed, but assume
        // mainClass is the main_class because it is sourced by the java_binary.
        return label;
      }
    }
    return null;
  }
}

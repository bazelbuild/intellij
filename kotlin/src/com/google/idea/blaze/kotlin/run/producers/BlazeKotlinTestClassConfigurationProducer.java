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
package com.google.idea.blaze.kotlin.run.producers;

import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.SourceToTargetFinder;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;

/** Creates run configurations for Kotlin tests. */
public class BlazeKotlinTestClassConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {
  public BlazeKotlinTestClassConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    TestLocation testLocation = TestLocation.from(context);
    if (testLocation == null) {
      return false;
    }
    sourceElement.set(testLocation.getSourceElement());
    TargetInfo targetInfo = testLocation.getTargetInfo();
    if (targetInfo == null) {
      return false;
    }
    configuration.setTargetInfo(targetInfo);
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    handlerState.getCommandState().setCommand(BlazeCommandName.TEST);
    String testFilter = testLocation.getTestFilterFlag();
    if (testFilter != null) {
      List<String> flags = new ArrayList<>(handlerState.getBlazeFlagsState().getRawFlags());
      flags.removeIf((flag) -> flag.startsWith(BlazeFlags.TEST_FILTER));
      flags.add(testFilter);
      handlerState.getBlazeFlagsState().setRawFlags(flags);
    }
    BlazeConfigurationNameBuilder nameBuilder = new BlazeConfigurationNameBuilder(configuration);
    nameBuilder.setTargetString(testLocation.getDisplayName());
    configuration.setName(nameBuilder.build());
    configuration.setNameChangedByUser(true);
    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    TestLocation testLocation = TestLocation.from(context);
    if (testLocation == null) {
      return false;
    }
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    return handlerState != null
        && Objects.equals(handlerState.getCommandState().getCommand(), BlazeCommandName.TEST)
        && Objects.equals(handlerState.getTestFilterFlag(), testLocation.getTestFilterFlag())
        && Objects.equals(configuration.getTarget(), testLocation.getTargetInfo().label);
  }

  private static class TestLocation {
    final KtClass testClass;
    @Nullable final KtNamedFunction testMethod;

    @Nullable
    static TestLocation from(ConfigurationContext context) {
      Location<?> location = context.getLocation();
      if (location == null) {
        return null;
      }
      PsiElement element = location.getPsiElement();
      KtNamedFunction testMethod = PsiUtils.getParentOfType(element, KtNamedFunction.class, false);
      KtClass testClass = PsiUtils.getParentOfType(element, KtClass.class, false);
      if (testClass == null) {
        return null;
      }
      return new TestLocation(testMethod, testClass);
    }

    private TestLocation(@Nullable KtNamedFunction testMethod, KtClass testClass) {
      this.testMethod = testMethod;
      this.testClass = testClass;
    }

    PsiElement getSourceElement() {
      return testMethod != null ? testMethod : testClass;
    }

    TargetInfo getTargetInfo() {
      File file = VfsUtil.virtualToIoFile(testClass.getContainingFile().getVirtualFile());
      return Iterables.getFirst(
          SourceToTargetFinder.findTargetsForSourceFile(
              testClass.getProject(), file, Optional.of(RuleType.TEST)),
          null);
    }

    @Nullable
    String getTestFilterFlag() {
      FqName fqName = testMethod != null ? testMethod.getFqName() : testClass.getFqName();
      if (fqName == null) {
        return null;
      }
      return BlazeFlags.TEST_FILTER + "=" + fqName;
    }

    String getDisplayName() {
      String displayName = testClass.getName();
      if (testMethod != null) {
        displayName += "." + testMethod.getName();
      }
      return displayName;
    }
  }
}

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
package com.google.idea.blaze.scala.run.producers;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.java.run.RunUtil;
import com.google.idea.blaze.java.run.producers.BlazeJavaTestClassConfigurationProducer;
import com.google.idea.blaze.java.run.producers.TestSizeAnnotationMap;
import com.google.idea.blaze.scala.run.Specs2Utils;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil;

/**
 * Producer for run configurations related to Scala specs2 test expressions in Blaze. Test class is
 * handled by {@link BlazeJavaTestClassConfigurationProducer}.
 */
public class BlazeScalaSpecs2TestExprConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public BlazeScalaSpecs2TestExprConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    PsiElement element = context.getPsiLocation();
    if (!(element instanceof ScInfixExpr)) {
      return false;
    }
    ScInfixExpr testCase = (ScInfixExpr) element;
    ScTypeDefinition testClass = getTestClass(context);
    if (testClass == null) {
      return false;
    }

    Label target = getTestTarget(testClass);
    if (target == null) {
      return false;
    }
    configuration.setTarget(target);

    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }

    sourceElement.set(element);

    handlerState.getCommandState().setCommand(BlazeCommandName.TEST);

    // remove old test filter flag if present
    List<String> flags = new ArrayList<>(handlerState.getBlazeFlagsState().getRawFlags());
    flags.removeIf((flag) -> flag.startsWith(BlazeFlags.TEST_FILTER));
    flags.add(BlazeFlags.TEST_FILTER + "=" + Specs2Utils.getTestFilter(testClass, testCase));
    handlerState.getBlazeFlagsState().setRawFlags(flags);

    String name =
        new BlazeConfigurationNameBuilder(configuration)
            .setTargetString(Specs2Utils.getSpecs2TestDisplayName(testClass, element))
            .build();
    configuration.setName(name);
    configuration.setNameChangedByUser(true); // don't revert to generated name

    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    PsiElement element = context.getPsiLocation();
    if (!(element instanceof ScInfixExpr)) {
      return false;
    }
    ScInfixExpr testCase = (ScInfixExpr) element;
    ScTypeDefinition testClass = getTestClass(context);
    if (testClass == null) {
      return false;
    }
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    if (!Objects.equals(handlerState.getCommandState().getCommand(), BlazeCommandName.TEST)) {
      return false;
    }
    String testFilter = Specs2Utils.getTestFilter(testClass, testCase);
    return Objects.equals(getTestTarget(testClass), configuration.getTarget())
        && Objects.equals(
            BlazeFlags.TEST_FILTER + "=" + testFilter, handlerState.getTestFilterFlag());
  }

  @Nullable
  @SuppressWarnings("unchecked")
  private static ScTypeDefinition getTestClass(ConfigurationContext context) {
    if (!SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isEmpty()) {
      // handled by a different producer
      return null;
    }
    Location<PsiElement> location = context.getLocation();
    if (location == null) {
      return null;
    }
    return TestConfigurationUtil.specs2ConfigurationProducer()
        .getLocationClassAndTest(location)
        ._1();
  }

  @Nullable
  private static Label getTestTarget(ScTypeDefinition testClass) {
    TestSize testSize = TestSizeAnnotationMap.getTestSize(testClass);
    TargetInfo target = RunUtil.targetForTestClass(testClass, testSize);
    return target != null ? target.label : null;
  }
}

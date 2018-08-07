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
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.java.run.RunUtil;
import com.google.idea.blaze.java.run.producers.BlazeJavaTestClassConfigurationProducer;
import com.google.idea.blaze.java.run.producers.TestSizeFinder;
import com.google.idea.blaze.scala.run.Specs2Utils;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;

/**
 * Producer for run configurations related to Scala specs2 test expressions in Blaze. Test class is
 * handled by {@link BlazeJavaTestClassConfigurationProducer}.
 */
public class BlazeScalaSpecs2TestExprConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public BlazeScalaSpecs2TestExprConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  private static class TestLocation {
    private final TargetInfo target;
    private final ScTypeDefinition testClass;
    private final ScInfixExpr testCase;

    private TestLocation(TargetInfo target, ScTypeDefinition testClass, ScInfixExpr testCase) {
      this.target = target;
      this.testClass = testClass;
      this.testCase = testCase;
    }

    @Nullable
    private String testFilter() {
      String filter = Specs2Utils.getTestFilter(testClass, testCase);
      return filter != null ? BlazeFlags.TEST_FILTER + "=" + filter : null;
    }

    private String targetString() {
      return Specs2Utils.getSpecs2TestDisplayName(testClass, testCase);
    }
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    TestLocation testLocation = testLocation(context);
    if (testLocation == null) {
      return false;
    }
    configuration.setTargetInfo(testLocation.target);
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }

    sourceElement.set(testLocation.testCase);

    handlerState.getCommandState().setCommand(BlazeCommandName.TEST);

    // remove old test filter flag if present
    List<String> flags = new ArrayList<>(handlerState.getBlazeFlagsState().getRawFlags());
    flags.removeIf((flag) -> flag.startsWith(BlazeFlags.TEST_FILTER));
    String filter = testLocation.testFilter();
    if (filter != null) {
      flags.add(filter);
    }
    handlerState.getBlazeFlagsState().setRawFlags(flags);

    String name =
        new BlazeConfigurationNameBuilder(configuration)
            .setTargetString(testLocation.targetString())
            .build();
    configuration.setName(name);
    configuration.setNameChangedByUser(true); // don't revert to generated name

    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null
        || !Objects.equals(handlerState.getCommandState().getCommand(), BlazeCommandName.TEST)) {
      return false;
    }
    TestLocation testLocation = testLocation(context);
    return testLocation != null
        && Objects.equals(testLocation.target.label, configuration.getTarget())
        && Objects.equals(testLocation.testFilter(), handlerState.getTestFilterFlag());
  }

  @Nullable
  private static TestLocation testLocation(ConfigurationContext context) {
    // Handled by SM runner.
    if (!SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isEmpty()) {
      return null;
    }
    ScInfixExpr testCase = Specs2Utils.getContainingTestExprOrScope(context.getPsiLocation());
    if (testCase == null) {
      return null;
    }
    ScTypeDefinition testClass = PsiTreeUtil.getParentOfType(testCase, ScTypeDefinition.class);
    if (testClass == null) {
      return null;
    }
    TestSize testSize = TestSizeFinder.getTestSize(testClass);
    TargetInfo target = RunUtil.targetForTestClass(testClass, testSize);
    if (target == null) {
      return null;
    }
    return new TestLocation(target, testClass, testCase);
  }

  /** Let {@link BlazeJavaTestClassConfigurationProducer} know about specs2 test expressions. */
  public static class Identifier
      implements BlazeJavaTestClassConfigurationProducer.JavaTestCaseIdentifier {
    @Override
    public boolean isTestCase(ConfigurationContext context) {
      return Specs2Utils.getContainingTestExprOrScope(context.getPsiLocation()) != null;
    }
  }
}

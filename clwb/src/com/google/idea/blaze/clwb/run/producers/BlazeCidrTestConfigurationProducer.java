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
package com.google.idea.blaze.clwb.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.TestRuleFinder;
import com.google.idea.blaze.base.run.TestRuleHeuristic;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.cidr.execution.testing.CidrTestUtil;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.psi.OCFunctionDefinition;
import com.jetbrains.cidr.lang.psi.OCMacroCall;
import com.jetbrains.cidr.lang.psi.OCMacroCallArgument;
import com.jetbrains.cidr.lang.psi.OCStruct;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCFunctionSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCStructSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCSymbolWithQualifiedName;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** Producer for run configurations related to C/C++ test classes in Blaze. */
public class BlazeCidrTestConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  private static class TestTarget {
    @Nullable
    private static TestTarget createFromFile(@Nullable PsiElement element) {
      return createFromClassAndMethod(element, null, null);
    }

    @Nullable
    private static TestTarget createFromClass(@Nullable PsiElement element, String className) {
      return createFromClassAndMethod(element, className, null);
    }

    @Nullable
    private static TestTarget createFromClassAndMethod(
        @Nullable PsiElement element, String classOrSuiteName, @Nullable String testName) {
      Label label = getCcTestTarget(element);
      if (label == null) {
        return null;
      }
      String filter = null;
      if (classOrSuiteName != null) {
        filter = classOrSuiteName;
        if (testName != null) {
          filter += "." + testName;
        }
      }
      return new TestTarget(element, label, filter);
    }

    private final PsiElement element;
    private final Label label;
    @Nullable private final String testFilterArg;
    private final String name;

    private TestTarget(PsiElement element, Label label, @Nullable String testFilter) {
      this.element = element;
      this.label = label;
      if (testFilter != null) {
        testFilterArg = BlazeFlags.TEST_FILTER + "=" + testFilter;
        name = String.format("%s (%s)", testFilter, label.toString());
      } else {
        testFilterArg = null;
        name = label.toString();
      }
    }
  }

  public BlazeCidrTestConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {

    TestTarget testObject = findTestObject(context.getLocation());
    if (testObject == null) {
      return false;
    }
    sourceElement.set(testObject.element);
    configuration.setTarget(testObject.label);
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    handlerState.setCommand(BlazeCommandName.TEST);

    ImmutableList.Builder<String> flags = ImmutableList.builder();
    if (testObject.testFilterArg != null) {
      flags.add(testObject.testFilterArg);
    }
    flags.add(BlazeFlags.TEST_OUTPUT_STREAMED);
    flags.addAll(handlerState.getBlazeFlags());

    handlerState.setBlazeFlags(flags.build());
    configuration.setName(
        String.format(
            "%s test: %s", Blaze.buildSystemName(configuration.getProject()), testObject.name));
    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    if (!Objects.equals(handlerState.getCommand(), BlazeCommandName.TEST)) {
      return false;
    }
    TestTarget testObject = findTestObject(context.getLocation());
    if (testObject == null) {
      return false;
    }
    List<String> flags = handlerState.getBlazeFlags();
    return testObject.label.equals(configuration.getTarget())
        && (testObject.testFilterArg == null || flags.contains(testObject.testFilterArg));
  }

  @Nullable
  private static TestTarget findTestObject(Location<?> location) {
    // Copied from on CidrGoogleTestRunConfigurationProducer::findTestObject.

    // Precedence order (decreasing): class/function, macro, file
    PsiElement element = location.getPsiElement();
    PsiElement parent =
        PsiTreeUtil.getNonStrictParentOfType(element, OCFunctionDefinition.class, OCStruct.class);

    OCStructSymbol parentSymbol;
    if (parent instanceof OCStruct
        && ((parentSymbol = ((OCStruct) parent).getSymbol()) != null)
        && CidrTestUtil.isGoogleTestClass(parentSymbol)) {
      Couple<String> name = CidrTestUtil.extractGoogleTestName(parentSymbol);
      if (name != null) {
        return TestTarget.createFromClassAndMethod(parent, name.first, name.second);
      }
      String className = parentSymbol.getQualifiedName().getName();
      return TestTarget.createFromClass(parent, className);
    } else if (parent instanceof OCFunctionDefinition) {
      OCFunctionSymbol symbol = ((OCFunctionDefinition) parent).getSymbol();
      if (symbol != null) {
        OCSymbolWithQualifiedName<?> resolvedOwner = symbol.getResolvedOwner();
        if (resolvedOwner != null) {
          OCSymbol<?> owner = resolvedOwner.getDefinitionSymbol();
          if (owner instanceof OCStructSymbol
              && CidrTestUtil.isGoogleTestClass((OCStructSymbol) owner)) {
            OCStruct struct = (OCStruct) owner.locateDefinition();
            Couple<String> name = CidrTestUtil.extractGoogleTestName((OCStructSymbol) owner);
            if (name != null) {
              return TestTarget.createFromClassAndMethod(struct, name.first, name.second);
            }
            return TestTarget.createFromClass(
                struct, ((OCStructSymbol) owner).getQualifiedName().getName());
          }
        }
      }
    }

    // if we're still here, let's test for a macro and, as a last resort, a file.
    parent = PsiTreeUtil.getNonStrictParentOfType(element, OCMacroCall.class, OCFile.class);
    if (parent instanceof OCMacroCall) {
      OCMacroCall gtestMacro = CidrTestUtil.findGoogleTestMacros(parent);
      if (gtestMacro != null) {
        List<OCMacroCallArgument> arguments = gtestMacro.getArguments();
        if (arguments.size() >= 2) {
          OCMacroCallArgument suiteArg = arguments.get(0);
          OCMacroCallArgument testArg = arguments.get(1);

          // if the element is the first argument of macro call,
          // then running entire suite, otherwise only a current test
          boolean isSuite =
              isFirstArgument(PsiTreeUtil.getParentOfType(element, OCMacroCallArgument.class))
                  || isFirstArgument(element.getPrevSibling());
          String suiteName = CidrTestUtil.extractArgumentValue(suiteArg);
          String testName = CidrTestUtil.extractArgumentValue(testArg);
          OCStructSymbol symbol =
              CidrTestUtil.findGoogleTestSymbol(element.getProject(), suiteName, testName);
          if (symbol != null) {
            OCStruct targetElement = (OCStruct) symbol.locateDefinition();
            return TestTarget.createFromClassAndMethod(
                targetElement, suiteName, isSuite ? null : testName);
          }
        }
      }
      Couple<String> suite = CidrTestUtil.extractFullSuiteNameFromMacro(parent);
      if (suite != null) {
        Collection<OCStructSymbol> res =
            CidrTestUtil.findGoogleTestSymbolsForSuiteRandomly(
                element.getProject(), suite.first, true);
        if (res.size() != 0) {
          OCStruct struct = (OCStruct) res.iterator().next().locateDefinition();
          return TestTarget.createFromClassAndMethod(struct, suite.first, null);
        }
      }
    } else if (parent instanceof OCFile) {
      return TestTarget.createFromFile(parent);
    }
    return null;
  }

  private static boolean isFirstArgument(@Nullable PsiElement element) {
    OCMacroCall macroCall = PsiTreeUtil.getParentOfType(element, OCMacroCall.class);
    if (macroCall != null) {
      List<OCMacroCallArgument> arguments = macroCall.getArguments();
      return arguments.size() > 0 && arguments.get(0).equals(element);
    }
    return false;
  }

  @Nullable
  private static Label getCcTestTarget(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }
    File file = getContainingFile(element);
    if (file == null) {
      return null;
    }
    Collection<RuleIdeInfo> rules =
        TestRuleFinder.getInstance(element.getProject()).testTargetsForSourceFile(file);
    return TestRuleHeuristic.chooseTestTargetForSourceFile(file, rules, null);
  }

  private static File getContainingFile(PsiElement element) {
    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    VirtualFile vf = psiFile.getVirtualFile();
    return vf != null ? new File(vf.getPath()) : null;
  }
}

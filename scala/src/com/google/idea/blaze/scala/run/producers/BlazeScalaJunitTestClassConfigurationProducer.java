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

import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.java.run.producers.BlazeJavaTestClassConfigurationProducer;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass;

/**
 * Hack to get {@link BlazeJavaTestClassConfigurationProducer} to stop hijacking configurations from
 * specs2 expressions with existing class configurations.
 *
 * <p>context.findExisting will take priority over context.getConfiguration, even if
 * context.getConfiguration returns the preferred, but non-existing, configuration. {@link
 * com.intellij.execution.actions.BaseRunConfigurationAction#update}
 */
public class BlazeScalaJunitTestClassConfigurationProducer
    extends BlazeJavaTestClassConfigurationProducer {
  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    return !(context.getPsiLocation() instanceof ScInfixExpr)
        && super.doSetupConfigFromContext(configuration, context, sourceElement);
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    return !(context.getPsiLocation() instanceof ScInfixExpr)
        && super.doIsConfigFromContext(configuration, context);
  }

  @Override
  protected boolean supportsClass(PsiClass psiClass) {
    return psiClass instanceof ScClass;
  }
}

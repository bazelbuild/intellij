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
package com.google.idea.blaze.scala.run.smrunner;

import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.execution.BlazeParametersListUtil;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.scala.run.Specs2Utils;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;

/** Provides scala-specific methods needed by the SM-runner test UI. */
public class BlazeScalaTestEventsHandler implements BlazeTestEventsHandler {
  @Override
  public boolean handlesKind(@Nullable Kind kind) {
    return kind != null
        && kind.getLanguageClass().equals(LanguageClass.KOTLIN)
        && kind.getRuleType().equals(RuleType.TEST);
  }

  @Override
  public SMTestLocator getTestLocator() {
    return BlazeScalaTestLocator.INSTANCE;
  }

  @Nullable
  @Override
  public String getTestFilter(Project project, List<Location<?>> testLocations) {
    String filter =
        testLocations.stream()
            .map(Location::getPsiElement)
            .map(BlazeScalaTestEventsHandler::getTestFilter)
            .filter(Objects::nonNull)
            .reduce((a, b) -> a + "|" + b)
            .orElse(null);
    return filter != null
        ? String.format(
            "%s=%s", BlazeFlags.TEST_FILTER, BlazeParametersListUtil.encodeParam(filter))
        : null;
  }

  @Nullable
  private static String getTestFilter(PsiElement element) {
    ScTypeDefinition testClass = PsiTreeUtil.getParentOfType(element, ScTypeDefinition.class);
    return testClass != null ? Specs2Utils.getTestFilter(testClass, element) : null;
  }

  @Override
  public String testDisplayName(Label label, @Nullable Kind kind, String rawName) {
    return rawName.replace(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER, " ");
  }
}

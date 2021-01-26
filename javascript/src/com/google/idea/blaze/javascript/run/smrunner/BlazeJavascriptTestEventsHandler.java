/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.javascript.run.smrunner;

import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.execution.BlazeParametersListUtil;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.io.URLUtil;
import java.util.List;
import javax.annotation.Nullable;

/** Provides javascript-specific methods needed by the SM-runner test UI. */
public class BlazeJavascriptTestEventsHandler implements BlazeTestEventsHandler {

  @Override
  public boolean handlesKind(@Nullable Kind kind) {
    return kind != null
        && kind.hasLanguage(LanguageClass.JAVASCRIPT)
        && kind.getRuleType().equals(RuleType.TEST);
  }

  @Override
  public SMTestLocator getTestLocator() {
    return BlazeJavascriptTestLocator.INSTANCE;
  }

  @Nullable
  @Override
  public String getTestFilter(Project project, List<Location<?>> testLocations) {
    WorkspaceRoot root = WorkspaceRoot.fromProject(project);
    String rootName = root.directory().getName();
    String filter =
        testLocations.stream()
            .map(Location::getPsiElement)
            .map(PsiElement::getContainingFile)
            .filter(JSFile.class::isInstance)
            .map(PsiFile::getVirtualFile)
            .map(root::workspacePathFor)
            .map(WorkspacePath::relativePath)
            .map(FileUtil::getNameWithoutExtension)
            .distinct()
            .map(name -> '^' + rootName + '/' + name + '$')
            .reduce((a, b) -> a + "|" + b)
            .orElse(null);
    return filter != null
        ? String.format(
            "%s=%s", BlazeFlags.TEST_FILTER, BlazeParametersListUtil.encodeParam(filter))
        : null;
  }

  @Override
  public String suiteLocationUrl(Label label, @Nullable Kind kind, String name) {
    return SmRunnerUtils.GENERIC_SUITE_PROTOCOL
        + URLUtil.SCHEME_SEPARATOR
        + label
        + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
        + name;
  }

  @Override
  public String testLocationUrl(
      Label label,
      @Nullable Kind kind,
      String parentSuite,
      String name,
      @Nullable String className) {
    return SmRunnerUtils.GENERIC_TEST_PROTOCOL
        + URLUtil.SCHEME_SEPARATOR
        + label
        + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
        + parentSuite
        + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
        + name;
  }
}

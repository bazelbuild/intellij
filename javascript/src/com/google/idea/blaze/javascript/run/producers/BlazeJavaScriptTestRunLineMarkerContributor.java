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
package com.google.idea.blaze.javascript.run.producers;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.SourceToTargetFinder;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.sync.BlazeSyncModificationTracker;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.targetmaps.ReverseDependencyMap;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.icons.AllIcons.RunConfigurations.TestState;
import com.intellij.javascript.testFramework.JsTestElementPath;
import com.intellij.javascript.testFramework.jasmine.JasmineFileStructureBuilder;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.io.URLUtil;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.swing.Icon;

/**
 * Generates run/debug gutter icons for jsunit_test.
 *
 * <p>{@link JSTestRunLineMarkersProvider} does not handle goog.testing.testSuite tests.
 *
 * <p>{@link JSFile#isTestFile()} will return false for these files.
 */
public class BlazeJavaScriptTestRunLineMarkerContributor extends RunLineMarkerContributor {
  private static BoolExperiment enableJavascriptRunLineMarkers =
      new BoolExperiment("enable.javascript.runline.markers", true);

  @Nullable
  @Override
  public Info getInfo(PsiElement element) {
    if (!enableJavascriptRunLineMarkers.getValue()) {
      return null;
    }
    JSFile file =
        Optional.of(element)
            .filter(PsiElement::isValid)
            .filter(LeafPsiElement.class::isInstance)
            .map(LeafPsiElement.class::cast)
            .filter(e -> e.getElementType().equals(JSTokenTypes.IDENTIFIER))
            .map(PsiElement::getContainingFile)
            .filter(JSFile.class::isInstance)
            .map(JSFile.class::cast)
            .orElse(null);
    if (file == null) {
      return null;
    }
    Collection<Label> labels = getWrapperTests(file);
    if (labels.isEmpty()) {
      return null;
    }
    JsTestElementPath jasmineTestElement =
        CachedValuesManager.getCachedValue(
                file,
                () ->
                    Result.create(
                        JasmineFileStructureBuilder.getInstance().buildTestFileStructure(file),
                        file))
            .findTestElementPath(element);
    if (jasmineTestElement != null) {
      return new Info(
          getJasmineTestIcon(file.getProject(), labels, jasmineTestElement),
          null,
          ExecutorAction.getActions());
    }
    PsiElement parent = element.getParent();
    if (parent instanceof JSFunction && element.getText().startsWith("test")) {
      return new Info(
          getClosureTestIcon(file.getProject(), labels, file, (JSFunction) parent),
          null,
          ExecutorAction.getActions());
    }
    return null;
  }

  private static ImmutableList<Label> getWrapperTests(JSFile file) {
    return CachedValuesManager.getCachedValue(
        file,
        () -> {
          Project project = file.getProject();
          BlazeProjectData projectData =
              BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
          if (projectData == null) {
            return Result.create(
                ImmutableList.of(), BlazeSyncModificationTracker.getInstance(project));
          }
          ImmutableMultimap<TargetKey, TargetKey> rdeps = ReverseDependencyMap.get(project);
          TargetMap targetMap = projectData.getTargetMap();
          return Result.create(
              SourceToTargetFinder.findTargetsForSourceFile(
                      project,
                      VfsUtil.virtualToIoFile(file.getVirtualFile()),
                      Optional.of(RuleType.TEST))
                  .stream()
                  .filter(t -> t.getKind() != null)
                  .filter(t -> t.getKind().hasLanguage(LanguageClass.JAVASCRIPT))
                  .map(t -> t.label)
                  .map(TargetKey::forPlainTarget)
                  .map(rdeps::get)
                  .filter(Objects::nonNull)
                  .flatMap(Collection::stream)
                  .filter(
                      key -> {
                        TargetIdeInfo target = targetMap.get(key);
                        return target != null && target.getKind().isWebTest();
                      })
                  .map(TargetKey::getLabel)
                  .collect(ImmutableList.toImmutableList()),
              BlazeSyncModificationTracker.getInstance(project));
        });
  }

  private static final Map<Icon, Integer> iconPriorities =
      ImmutableMap.of(
          AllIcons.RunConfigurations.TestState.Red2, 3,
          AllIcons.RunConfigurations.TestState.Green2, 2,
          TestState.Run_run, 1,
          TestState.Run, 0);

  private static Icon getJasmineTestIcon(
      Project project, Collection<Label> labels, JsTestElementPath jasmineTestElement) {
    String testName = jasmineTestElement.getTestName();
    Icon defaultRunIcon = testName == null ? TestState.Run_run : TestState.Run;
    if (jasmineTestElement.getSuiteNames().isEmpty()) {
      return defaultRunIcon;
    }
    StringBuilder urlSuffixBuilder =
        new StringBuilder(Iterables.getLast(jasmineTestElement.getSuiteNames()));
    if (testName != null) {
      urlSuffixBuilder
          .append(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER)
          .append(Joiner.on(' ').join(jasmineTestElement.getAllNames()))
          .append(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER); // empty class name
    }
    String urlSuffix = urlSuffixBuilder.toString();
    return labels.stream()
        .map(
            label ->
                SmRunnerUtils.GENERIC_TEST_PROTOCOL
                    + URLUtil.SCHEME_SEPARATOR
                    + label
                    + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
                    + urlSuffix)
        .map(url -> getTestStateIcon(url, project, /* isClass = */ testName == null))
        .max(Comparator.comparingInt(iconPriorities::get))
        .orElse(defaultRunIcon);
  }

  private static Icon getClosureTestIcon(
      Project project, Collection<Label> labels, JSFile file, JSFunction function) {
    WorkspaceRoot root = WorkspaceRoot.fromProject(project);
    WorkspacePath path = root.workspacePathFor(file.getVirtualFile());
    String relativePath = root.directory().getName() + '/' + path.relativePath();
    if (relativePath.endsWith(".js")) {
      relativePath = relativePath.substring(0, relativePath.lastIndexOf(".js"));
    }
    String urlSuffix =
        relativePath
            + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
            + function.getName()
            + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
            + relativePath; // redundant class name
    return labels.stream()
        .map(
            label ->
                SmRunnerUtils.GENERIC_TEST_PROTOCOL
                    + URLUtil.SCHEME_SEPARATOR
                    + label
                    + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
                    + urlSuffix)
        .map(url -> getTestStateIcon(url, project, /* isClass = */ false))
        .max(Comparator.comparingInt(iconPriorities::get))
        .orElse(TestState.Run);
  }
}

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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.javascript.testFramework.jasmine.AbstractJasmineElement;
import com.intellij.javascript.testFramework.jasmine.JasmineFileStructure;
import com.intellij.javascript.testFramework.jasmine.JasmineFileStructureBuilder;
import com.intellij.javascript.testFramework.jasmine.JasmineSpecStructure;
import com.intellij.javascript.testFramework.jasmine.JasmineSuiteStructure;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import javax.annotation.Nullable;

/** Locate javascript test packages / functions for test UI navigation. */
public final class BlazeJavascriptTestLocator implements SMTestLocator {
  private static final BoolExperiment searchForJasmineSources =
      new BoolExperiment("search.for.jasmine.sources", true);
  public static final BlazeJavascriptTestLocator INSTANCE = new BlazeJavascriptTestLocator();

  private BlazeJavascriptTestLocator() {}

  @Override
  @SuppressWarnings("rawtypes")
  public List<Location> getLocation(
      String protocol, String path, Project project, GlobalSearchScope scope) {
    List<String> components =
        Splitter.on(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER).limit(3).splitToList(path);
    if (components.size() < 2) {
      return ImmutableList.of();
    }
    String suiteName = components.get(1);
    String testName =
        protocol.equals(SmRunnerUtils.GENERIC_TEST_PROTOCOL) && components.size() == 3
            ? components.get(2)
            : null;
    File file = findFile(project, suiteName);
    if (file != null) {
      return findClosureTestCase(project, file, testName);
    } else {
      Label label = Label.createIfValid(components.get(0));
      if (label == null) {
        return ImmutableList.of();
      }
      return findJasmineTestCase(project, label, suiteName, testName);
    }
  }

  @Nullable
  private static File findFile(Project project, String path) {
    WorkspaceRoot root = WorkspaceRoot.fromProject(project);
    if (!path.startsWith(root.directory().getName() + '/')) {
      return null;
    }
    path = path.substring(path.indexOf('/') + 1);
    return root.fileForPath(new WorkspacePath(path + ".js"));
  }

  @SuppressWarnings("rawtypes")
  private static List<Location> findClosureTestCase(
      Project project, File file, @Nullable String testName) {
    VirtualFile virtualFile = VfsUtils.resolveVirtualFile(file);
    if (virtualFile == null) {
      return ImmutableList.of();
    }
    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    if (!(psiFile instanceof JSFile)) {
      return ImmutableList.of();
    }
    if (testName != null) {
      for (JSFunction function : PsiTreeUtil.findChildrenOfType(psiFile, JSFunction.class)) {
        if (Objects.equals(function.getName(), testName)) {
          return ImmutableList.of(new PsiLocation<>(function));
        }
      }
    }
    return ImmutableList.of(new PsiLocation<>(psiFile));
  }

  @SuppressWarnings("rawtypes")
  private static List<Location> findJasmineTestCase(
      Project project, Label label, String suiteName, @Nullable String testName) {
    if (!searchForJasmineSources.getValue()) {
      return ImmutableList.of();
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return ImmutableList.of();
    }
    PsiManager psiManager = PsiManager.getInstance(project);
    return OutputArtifactResolver.resolveAll(
            project,
            projectData.getArtifactLocationDecoder(),
            getJasmineSources(projectData, label))
        .stream()
        .map(VfsUtils::resolveVirtualFile)
        .filter(Objects::nonNull)
        .map(psiManager::findFile)
        .filter(JSFile.class::isInstance)
        .map(JSFile.class::cast)
        .map(JasmineFileStructureBuilder.getInstance()::buildTestFileStructure)
        .map(file -> findJasmineSuiteByName(file, suiteName))
        .filter(Objects::nonNull)
        .map(testSuite -> maybeFindJasmineTestCase(testSuite, testName))
        .map(AbstractJasmineElement::getEnclosingPsiElement)
        .map(PsiLocation::new)
        .collect(ImmutableList.toImmutableList());
  }

  @Nullable
  private static JasmineSuiteStructure findJasmineSuiteByName(
      JasmineFileStructure file, String suiteName) {
    Queue<JasmineSuiteStructure> queue = new ArrayDeque<>(file.getSuites());
    while (!queue.isEmpty()) {
      JasmineSuiteStructure suite = queue.remove();
      if (Objects.equals(suite.getName(), suiteName)) {
        return suite;
      }
      queue.addAll(suite.getSuites());
    }
    return null;
  }

  /** Each jasmine test case name is prefixed by all of the enclosing test suite names. */
  private static AbstractJasmineElement maybeFindJasmineTestCase(
      JasmineSuiteStructure testSuite, @Nullable String testName) {
    if (testName == null) {
      return testSuite;
    }
    StringBuilder prefixBuilder = new StringBuilder();
    JasmineSuiteStructure containingSuite = testSuite;
    while (containingSuite != null) {
      prefixBuilder.insert(0, containingSuite.getName() + " ");
      containingSuite = containingSuite.getParent();
    }
    String prefix = prefixBuilder.toString();
    if (testName.startsWith(prefix)) {
      testName = testName.substring(prefix.length());
    }
    JasmineSpecStructure testCase = testSuite.getInnerSpecByName(testName);
    return testCase != null ? testCase : testSuite;
  }

  private static ImmutableSet<ArtifactLocation> getJasmineSources(
      BlazeProjectData projectData, Label label) {
    TargetMap targetMap = projectData.getTargetMap();
    TargetIdeInfo target = targetMap.get(TargetKey.forPlainTarget(label));
    if (target == null || target.getKind().getLanguageClass() != LanguageClass.JAVASCRIPT) {
      return ImmutableSet.of();
    }
    if (!target.getSources().isEmpty()) {
      return target.getSources();
    }
    // the *_wrapped_test_jasmine_node_module dependency contains the sources files as srcs,
    // but also contains jasmine_runner.js and jasmine_once.js
    //
    // *_wrapped_test_srcs contains only the desired source files, but unfortunately is only
    // reachable through the data attribute on *_wrapped_test_jasmine_node_module, which we do not
    // want to follow.
    return target.getDependencies().stream()
        .map(Dependency::getTargetKey)
        .map(projectData.getTargetMap()::get)
        .filter(Objects::nonNull)
        .filter(t -> t.getKind().getLanguageClass() == LanguageClass.JAVASCRIPT)
        .map(TargetIdeInfo::getSources)
        .flatMap(Collection::stream)
        .collect(ImmutableSet.toImmutableSet());
  }
}

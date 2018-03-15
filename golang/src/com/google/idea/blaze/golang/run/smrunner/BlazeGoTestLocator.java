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
package com.google.idea.blaze.golang.run.smrunner;

import static com.google.idea.blaze.base.run.smrunner.SmRunnerUtils.GENERIC_SUITE_PROTOCOL;
import static com.google.idea.blaze.base.run.smrunner.SmRunnerUtils.GENERIC_TEST_PROTOCOL;

import com.goide.psi.GoFunctionDeclaration;
import com.goide.stubs.index.GoFunctionIndex;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope.FilesScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.PathUtil;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Locate go test packages / functions for test UI navigation. */
public final class BlazeGoTestLocator implements SMTestLocator {
  public static final BlazeGoTestLocator INSTANCE = new BlazeGoTestLocator();

  private BlazeGoTestLocator() {}

  @Override
  @SuppressWarnings("rawtypes")
  public List<Location> getLocation(
      String protocol, String path, Project project, GlobalSearchScope scope) {
    switch (protocol) {
      case GENERIC_SUITE_PROTOCOL:
        return findTestPackage(project, path);
      case GENERIC_TEST_PROTOCOL:
        return findTestFunction(project, path);
      default:
        return ImmutableList.of();
    }
  }

  /** @param path for "//foo/bar:baz" would be "foo/bar/baz". */
  @SuppressWarnings("rawtypes")
  private static List<Location> findTestPackage(Project project, String path) {
    TargetIdeInfo target = getGoTestTarget(project, path);
    if (target == null) {
      return ImmutableList.of();
    }
    // Exactly one source file, we'll go to the file.
    if (target.sources.size() == 1) {
      List<VirtualFile> goFiles = getGoFiles(project, target);
      if (!goFiles.isEmpty()) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(goFiles.get(0));
        if (psiFile != null) {
          return ImmutableList.of(new PsiLocation<>(psiFile));
        }
      }
    }
    // More than one source file or we failed to get one source file, we'll point to the rule.
    PsiElement rule = BuildReferenceManager.getInstance(project).resolveLabel(target.key.label);
    if (!(rule instanceof FuncallExpression)) {
      return ImmutableList.of();
    }
    Kind kind = ((FuncallExpression) rule).getRuleKind();
    if (kind != null
        && kind.languageClass.equals(LanguageClass.GO)
        && kind.ruleType.equals(RuleType.TEST)) {
      return ImmutableList.of(new PsiLocation<>(rule));
    }
    return ImmutableList.of();
  }

  /**
   * @param path for function "TestFoo" in target "//foo/bar:baz" would be "foo/bar/baz::TestFoo".
   *     See {@link BlazeGoTestEventsHandler#testLocationUrl}.
   */
  @SuppressWarnings("rawtypes")
  private static List<Location> findTestFunction(Project project, String path) {
    String[] parts = path.split(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER);
    if (parts.length != 2) {
      return ImmutableList.of();
    }
    String functionName = parts[1];
    TargetIdeInfo target = getGoTestTarget(project, parts[0]);
    List<VirtualFile> goFiles = getGoFiles(project, target);
    if (goFiles.isEmpty()) {
      return ImmutableList.of();
    }
    GlobalSearchScope scope = FilesScope.filesScope(project, goFiles);
    Collection<GoFunctionDeclaration> functions =
        StubIndex.getElements(
            GoFunctionIndex.KEY, functionName, project, scope, GoFunctionDeclaration.class);
    return functions.stream().map(PsiLocation::new).collect(Collectors.toList());
  }

  @Nullable
  private static TargetIdeInfo getGoTestTarget(Project project, String path) {
    WorkspacePath targetPackage = WorkspacePath.createIfValid(PathUtil.getParentPath(path));
    if (targetPackage == null) {
      return null;
    }
    TargetName targetName = TargetName.createIfValid(PathUtil.getFileName(path));
    if (targetName == null) {
      return null;
    }
    Label label = Label.create(targetPackage, targetName);
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    TargetIdeInfo target = projectData.targetMap.get(TargetKey.forPlainTarget(label));
    if (target != null
        && target.kind.languageClass.equals(LanguageClass.GO)
        && target.kind.ruleType.equals(RuleType.TEST)) {
      return target;
    }
    return null;
  }

  private static List<VirtualFile> getGoFiles(Project project, @Nullable TargetIdeInfo target) {
    if (target == null || target.goIdeInfo == null) {
      return ImmutableList.of();
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    LocalFileSystem lfs = VirtualFileSystemProvider.getInstance().getSystem();
    if (projectData == null) {
      return ImmutableList.of();
    }
    return target
        .goIdeInfo
        .sources
        .stream()
        .map(projectData.artifactLocationDecoder::decode)
        .map(lfs::findFileByIoFile)
        .collect(Collectors.toList());
  }
}

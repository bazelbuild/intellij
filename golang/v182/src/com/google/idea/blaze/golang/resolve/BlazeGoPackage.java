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
package com.google.idea.blaze.golang.resolve;

import com.goide.psi.GoFile;
import com.goide.psi.impl.GoPackage;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.google.idea.blaze.golang.sync.BlazeGoLibrary;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * {@link GoPackage} specialized for blaze, with a couple of differences:
 *
 * <ol>
 *   <li>Same directory may appear in may different {@link BlazeGoPackage}s.
 *   <li>A single {@link BlazeGoPackage} may contain may different directories.
 *   <li>{@link #files} will not necessarily return all files under {@link
 *       GoPackage#getDirectories()}.
 *   <li>{@link #navigate}s to the corresponding go rule instead of a directory.
 * </ol>
 *
 * Exactly one {@link BlazeGoPackage} per go rule.
 */
class BlazeGoPackage extends GoPackage {
  private final String importPath;
  private final Label label;
  private final Collection<File> files;
  @Nullable private transient Collection<GoFile> cachedGoFiles;
  @Nullable private transient PsiElement cachedNavigable;
  @Nullable private transient PsiElement[] cachedImportReferences;

  public static BlazeGoPackage create(Project project, String importPath, TargetIdeInfo target) {
    // Symlinks already created by BlazeGoLibrary during sync.
    // Grab the symlinkMap so we know which source files were replaced by symlinks.
    Collection<File> files = BlazeGoLibrary.getSymlinkMap(project, target).values();
    return new BlazeGoPackage(
        project, importPath, target.kind.ruleType == RuleType.TEST, target.key.label, files);
  }

  BlazeGoPackage(
      Project project, String importPath, boolean isTest, Label label, Collection<File> files) {
    super(project, getPackageName(importPath), isTest, getDirectories(files));
    this.importPath = importPath;
    this.label = label;
    this.files = files;
  }

  private static String getPackageName(String importPath) {
    int slash = importPath.lastIndexOf('/');
    return importPath.substring(slash + 1);
  }

  /**
   * {@link BlazeGoPackage}s with newly created/symlinked {@link #files} might not have them
   * resolved in time for {@link #getDirectories(Collection)}. This will attempt to recreate the
   * {@link BlazeGoPackage} after all source files have been refreshed, and update the entry in
   * {@link BlazeGoImportResolver#getGoPackageMap(Project)}.
   */
  void refreshFiles() {
    VirtualFileSystemProvider.getInstance()
        .getSystem()
        .refreshIoFiles(
            files,
            true, // async
            false, // recursive
            () -> {
              ConcurrentMap<String, Optional<BlazeGoPackage>> goPackageMap =
                  BlazeGoImportResolver.getGoPackageMap(getProject());
              if (goPackageMap == null) {
                return;
              }
              BlazeGoPackage goPackage =
                  new BlazeGoPackage(getProject(), importPath, isTestPackage(), label, files);
              goPackageMap.put(importPath, Optional.of(goPackage));
            });
  }

  private static VirtualFile[] getDirectories(Collection<File> files) {
    return files
        .stream()
        .map(File::getParentFile)
        .filter(Objects::nonNull)
        .distinct()
        .map(VfsUtils::resolveVirtualFile)
        .filter(Objects::nonNull)
        .toArray(VirtualFile[]::new);
  }

  @Override
  public Collection<GoFile> files() {
    if (cachedGoFiles == null) {
      PsiManager psiManager = PsiManager.getInstance(getProject());
      cachedGoFiles =
          files
              .stream()
              .map(VfsUtils::resolveVirtualFile)
              .filter(Objects::nonNull)
              .map(psiManager::findFile)
              .filter(GoFile.class::isInstance)
              .map(GoFile.class::cast)
              .collect(Collectors.toList());
    }
    return cachedGoFiles;
  }

  @Override
  public void navigate(boolean requestFocus) {
    PsiElement navigable = getNavigableElement();
    if (navigable instanceof Navigatable && ((Navigatable) navigable).canNavigate()) {
      ((Navigatable) navigable).navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    return getNavigableElement() instanceof Navigatable;
  }

  @Nullable
  @Override
  public PsiElement getNavigableElement() {
    if (cachedNavigable == null) {
      Project project = getProject();
      BuildReferenceManager buildReferenceManager = BuildReferenceManager.getInstance(project);
      cachedNavigable = buildReferenceManager.resolveLabel(label);
      if (cachedNavigable == null) {
        cachedNavigable =
            buildReferenceManager.findBuildFile(
                WorkspaceHelper.resolveBlazePackage(project, label));
      }
    }
    return cachedNavigable;
  }

  @Override
  public String getCanonicalImport(@Nullable Module contextModule) {
    return importPath;
  }

  @Override
  public String getImportPath(boolean withVendoring) {
    return importPath;
  }

  @Override
  public Collection<String> getAllImportPaths(boolean withVendoring) {
    return ImmutableList.of(importPath);
  }

  /**
   * Returns a list of resolve targets, one for each import path component.
   *
   * <p>The final path component may either be the target name, or the containing directory name (in
   * which case the target name is usually go_default_library).
   *
   * <p>E.g., for target //foo:bar with import path "github.com/user/foo/bar"
   *
   * <pre>
   *   import "github.com/user/foo/bar"
   *           (1)        (2)  (3) (4)
   * </pre>
   *
   * <ol>
   *   <li>github.com resolves to nothing
   *   <li>user resolves to nothing
   *   <li>foo resolves to directory foo/
   *   <li>bar resolves to target //foo:bar
   * </ol>
   *
   * for target //one/two:go_default_library with import path "github.com/user/one/two"
   *
   * <pre>
   *    *   import "github.com/user/one/two"
   *    *           (1)        (2)  (3) (4)
   *    * </pre>
   *
   * <ol>
   *   <li>github.com resolves to nothing
   *   <li>user resolves to nothing
   *   <li>one resolves to directory one/
   *   <li>two resolves to directory one/two/
   * </ol>
   */
  PsiElement[] getImportReferences() {
    if (cachedImportReferences == null) {
      PsiElement navigable = getNavigableElement();
      if (navigable != null) {
        cachedImportReferences = getImportReferences(label, navigable, importPath);
      }
    }
    return cachedImportReferences;
  }

  @VisibleForTesting
  static PsiElement[] getImportReferences(Label label, PsiElement buildElement, String importPath) {
    List<String> pathComponents = Splitter.on('/').splitToList(importPath);
    PsiElement[] importReferences = new PsiElement[pathComponents.size()];
    if (pathComponents.isEmpty()) {
      return importReferences;
    }
    PsiElement lastElement = getLastElement(Iterables.getLast(pathComponents), label, buildElement);
    if (lastElement == null) {
      return importReferences;
    }
    importReferences[importReferences.length - 1] = lastElement;
    PsiDirectory currentElement =
        lastElement instanceof PsiDirectory
            ? (PsiDirectory) lastElement.getParent()
            : Optional.of(lastElement)
                .map(PsiElement::getContainingFile)
                .map(PsiFile::getParent)
                .orElse(null);
    for (int i = pathComponents.size() - 2; i >= 0 && currentElement != null; --i) {
      String name = currentElement.getName();
      String pathComponent = pathComponents.get(i);
      if (Objects.equals(name, pathComponent)) {
        importReferences[i] = currentElement;
        currentElement = currentElement.getParent();
      } else {
        break;
      }
    }
    return importReferences;
  }

  @Nullable
  private static PsiElement getLastElement(String name, Label label, PsiElement buildElement) {
    if (buildElement instanceof FuncallExpression) {
      if (Objects.equals(name, label.targetName().toString())) {
        return buildElement;
      } else {
        return Optional.of(buildElement)
            .map(PsiElement::getContainingFile)
            .map(PsiFile::getParent)
            .filter(d -> Objects.equals(d.getName(), name))
            .orElse(null);
      }
    } else if (buildElement instanceof BuildFile) {
      PsiDirectory match = ((BuildFile) buildElement).getParent();
      if (match != null && Objects.equals(match.getName(), name)) {
        return match;
      }
      return buildElement;
    }
    return null;
  }
}

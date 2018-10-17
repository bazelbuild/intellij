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

import com.goide.psi.impl.GoPackage;
import com.goide.psi.impl.imports.GoImportReference;
import com.goide.psi.impl.imports.GoImportResolver;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.codeInsight.navigation.CtrlMouseHandler;
import com.intellij.lang.documentation.DocumentationProviderEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.SyntheticFileSystemItem;
import com.intellij.psi.search.PsiElementProcessor;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Converts each go target in the {@link TargetMap} into a corresponding {@link BlazeGoPackage}. */
class BlazeGoImportResolver implements GoImportResolver {
  private static final String GO_PACKAGE_MAP_KEY = "BlazeGoPackageMap";
  private static final String GO_TARGET_MAP_KEY = "BlazeGoTargetMap";

  @Nullable
  @Override
  public Collection<GoPackage> resolve(
      String importPath, Project project, @Nullable Module module, @Nullable PsiElement context) {
    GoPackage goPackage = doResolve(importPath, project);
    return goPackage != null ? ImmutableList.of(goPackage) : null;
  }

  @Nullable
  static BlazeGoPackage doResolve(String importPath, Project project) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    ConcurrentMap<String, Optional<BlazeGoPackage>> goPackageMap =
        Preconditions.checkNotNull(getGoPackageMap(project));
    Multimap<String, TargetKey> goTargetMap = Preconditions.checkNotNull(getGoTargetMap(project));
    Collection<TargetKey> targetKeys = goTargetMap.get(importPath);
    if (!goPackageMap.containsKey(importPath) && targetKeys.isEmpty()) {
      return null;
    }
    return goPackageMap
        .computeIfAbsent(
            importPath,
            (path) -> {
              TargetMap targetMap = projectData.getTargetMap();
              Collection<TargetIdeInfo> targets =
                  targetKeys.stream()
                      .map(targetMap::get)
                      .filter(Objects::nonNull)
                      .collect(Collectors.toList());
              BlazeGoPackage goPackage = BlazeGoPackage.create(project, projectData, path, targets);
              return Optional.of(goPackage);
            })
        .orElse(null);
  }

  @Nullable
  static ConcurrentMap<String, Optional<BlazeGoPackage>> getGoPackageMap(Project project) {
    return SyncCache.getInstance(project)
        .get(GO_PACKAGE_MAP_KEY, (p, pd) -> new ConcurrentHashMap<>());
  }

  @Nullable
  private static Multimap<String, TargetKey> getGoTargetMap(Project project) {
    return SyncCache.getInstance(project)
        .get(
            GO_TARGET_MAP_KEY,
            (p, projectData) -> {
              Multimap<String, TargetKey> map = HashMultimap.create();
              projectData.getTargetMap().targets().stream()
                  .filter(t -> t.getGoIdeInfo() != null)
                  .filter(t -> t.getGoIdeInfo().getImportPath() != null)
                  .forEach(t -> map.put(t.getGoIdeInfo().getImportPath(), t.getKey()));
              return map;
            });
  }

  @Nullable
  @Override
  public ResolveResult[] resolve(GoImportReference reference) {
    String importPath = reference.getFileReferenceSet().getPathString();
    Project project = reference.getElement().getProject();
    BlazeGoPackage goPackage = doResolve(importPath, project);
    if (goPackage == null) {
      return null;
    }
    return doResolve(goPackage, reference.getIndex());
  }

  @Nullable
  static ResolveResult[] doResolve(BlazeGoPackage goPackage, int index) {
    return Stream.of(goPackage)
        .map(BlazeGoPackage::getImportReferences)
        .filter(list -> index < list.length)
        .map(list -> list[index])
        .filter(Objects::nonNull)
        .map(GoPackageFileSystemItem::getInstance)
        .filter(Objects::nonNull)
        .map(PsiElementResolveResult::new)
        .toArray(ResolveResult[]::new);
  }

  /**
   * {@link GoImportReference} must resolve to a {@link PsiFileSystemItem}, but we might want it to
   * resolve to a build rule in a {@link BuildFile}. We'll just return the {@link BuildFile} with a
   * navigation redirect.
   */
  private static class GoPackageFileSystemItem extends SyntheticFileSystemItem {
    private final String name;
    private final FuncallExpression rule;

    @Nullable
    static PsiFileSystemItem getInstance(PsiElement element) {
      if (element instanceof PsiFileSystemItem) {
        return (PsiFileSystemItem) element;
      } else if (element instanceof FuncallExpression) {
        String name = ((FuncallExpression) element).getName();
        if (name != null) {
          return new GoPackageFileSystemItem(name, (FuncallExpression) element);
        }
      }
      return null;
    }

    private GoPackageFileSystemItem(String name, FuncallExpression rule) {
      super(rule.getProject());
      this.name = name;
      this.rule = rule;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public PsiElement getNavigationElement() {
      return rule;
    }

    @Nullable
    @Override
    public PsiFileSystemItem getParent() {
      return Optional.of(rule)
          .map(FuncallExpression::getContainingFile)
          .map(BuildFile::getParent)
          .orElse(null);
    }

    @Override
    public VirtualFile getVirtualFile() {
      return null;
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public boolean isPhysical() {
      return false;
    }

    @Override
    public boolean isDirectory() {
      return false;
    }

    @Override
    public boolean processChildren(PsiElementProcessor<PsiFileSystemItem> psiElementProcessor) {
      return false;
    }
  }

  /** Redirects quick navigation text on the fake file system item back to the build rule. */
  private static class GoPackageDocumentationProvider extends DocumentationProviderEx {
    @Nullable
    @Override
    public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
      if (element instanceof GoPackageFileSystemItem) {
        return CtrlMouseHandler.getInfo(element.getNavigationElement(), originalElement);
      }
      return null;
    }
  }
}

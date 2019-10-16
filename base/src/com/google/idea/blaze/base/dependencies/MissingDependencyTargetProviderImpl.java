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
package com.google.idea.blaze.base.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Provides the missing Blaze targets that can be added as dependencies to the target rules building
 * the given source file.
 */
public class MissingDependencyTargetProviderImpl implements MissingDependencyTargetProvider {

  @Override
  public ImmutableList<MissingDependencyData> getMissingDependencyTargets(
      PsiFile sourceFile, ImmutableSet<PsiReference> references) {
    Project project = sourceFile.getProject();
    ImmutableSetMultimap<Label, Label> dependencies =
        getDependenciesForSourceFile(project, sourceFile);
    if (dependencies.isEmpty()) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<MissingDependencyData> result = ImmutableList.builder();
    references.forEach(
        reference -> {
          ImmutableListMultimap<Label, Label> missingDependencyTargets =
              getMissingDependencyTargets(project, dependencies, reference);
          if (!missingDependencyTargets.isEmpty()) {
            result.add(
                MissingDependencyData.builder()
                    .setReference(reference)
                    .setDependencyTargets(missingDependencyTargets)
                    .build());
          }
        });
    return result.build();
  }

  private static ImmutableListMultimap<Label, Label> getMissingDependencyTargets(
      Project project, ImmutableSetMultimap<Label, Label> dependencies, PsiReference reference) {
    ImmutableListMultimap.Builder<Label, Label> result = ImmutableListMultimap.builder();
    // TODO(b/138926172): Support libraries in project.
    File referencedSourceFile = getReferencedSourceFile(reference);
    if (referencedSourceFile == null) {
      return ImmutableListMultimap.of();
    }
    ImmutableSet<Label> referencedSourceFileTargets =
        ImmutableSet.copyOf(getTargetsBuildingSourceFile(project, referencedSourceFile));
    if (referencedSourceFileTargets.isEmpty()) {
      return ImmutableListMultimap.of();
    }
    for (Label sourceTarget : dependencies.keySet()) {
      // TODO(b/138926172): Check whether the dependency set for the source rule also contains
      //  targets exporting any of the targets in the "referencedSourceFileTargets" set.
      Set<Label> existingDependencies =
          Sets.intersection(referencedSourceFileTargets, dependencies.get(sourceTarget));
      if (existingDependencies.isEmpty()) {
        result.putAll(sourceTarget, referencedSourceFileTargets);
      }
    }
    return result.build();
  }

  @Nullable
  private static File getReferencedSourceFile(PsiReference reference) {
    PsiElement resolvedElement =
        ApplicationManager.getApplication()
            .runReadAction((Computable<PsiElement>) () -> reference.resolve());
    if (resolvedElement == null) {
      return null;
    }
    PsiFile containingFile = resolvedElement.getContainingFile();
    if (containingFile == null) {
      return null;
    }
    return new File(containingFile.getVirtualFile().getPath());
  }

  private static ImmutableList<Label> getTargetsBuildingSourceFile(Project project, File file) {
    // TODO(b/138926172): Support Herb/Blaze queries for finding all the targets building a source
    //  file to take into account the user's local changes.
    return SourceToTargetMap.getInstance(project).getTargetsToBuildForSourceFile(file);
  }

  @Nullable
  private static ImmutableList<Label> getDependencies(Project project, Label target) {
    List<TargetInfo> dependencies =
        DependencyFinder.getCompileTimeDependencyTargets(project, target);
    if (dependencies == null) {
      return null;
    }
    return dependencies.stream()
        .map(targetInfo -> targetInfo.label)
        .collect(ImmutableList.toImmutableList());
  }

  private static ImmutableSetMultimap<Label, Label> getDependenciesForSourceFile(
      Project project, PsiFile file) {
    ImmutableList<Label> targets =
        getTargetsBuildingSourceFile(
            project, new File(file.getViewProvider().getVirtualFile().getPath()));
    Map<Label, ImmutableList<Label>> map =
        targets.stream()
            .collect(
                Collectors.toMap(Function.identity(), target -> getDependencies(project, target)));
    return map.entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .collect(
            ImmutableSetMultimap.flatteningToImmutableSetMultimap(
                Map.Entry::getKey, entry -> entry.getValue().stream()));
  }
}

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
package com.google.idea.blaze.golang.run.producers;

import com.goide.execution.GoRunUtil;
import com.goide.psi.GoFile;
import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producers.BinaryContextProvider;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.targetmaps.ReverseDependencyMap;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** Go-specific handler for {@link BlazeCommandRunConfiguration}s. */
class GoBinaryContextProvider implements BinaryContextProvider {

  @Nullable
  @Override
  public BinaryRunContext getRunContext(ConfigurationContext context) {
    PsiFile file = getMainFile(context);
    if (file == null) {
      return null;
    }
    TargetInfo binaryTarget = getTargetLabel(file);
    if (binaryTarget == null) {
      return null;
    }
    return BinaryRunContext.create(file, binaryTarget);
  }

  @Nullable
  private static PsiFile getMainFile(ConfigurationContext context) {
    PsiElement element = context.getPsiLocation();
    if (element == null) {
      return null;
    }
    PsiFile file = element.getContainingFile();
    if (file instanceof GoFile && GoRunUtil.isMainGoFile(file)) {
      return file;
    }
    return null;
  }

  @Nullable
  private static TargetInfo getTargetLabel(PsiFile psiFile) {
    Project project = psiFile.getProject();
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    File file = VfsUtil.virtualToIoFile(virtualFile);
    Collection<TargetKey> rulesForFile =
        SourceToTargetMap.getInstance(project).getRulesForSourceFile(file);

    TargetMap targetMap = projectData.getTargetMap();
    List<TargetKey> libraryKeys = new ArrayList<>();
    for (TargetKey key : rulesForFile) {
      TargetIdeInfo target = targetMap.get(key);
      if (target == null || !target.getKind().hasLanguage(LanguageClass.GO)) {
        continue;
      }
      switch (target.getKind().getRuleType()) {
        case BINARY:
          return target.toTargetInfo();
        case LIBRARY:
          libraryKeys.add(target.getKey());
          break;
        case TEST:
        case UNKNOWN:
          // ignore these
      }
    }
    ImmutableMultimap<TargetKey, TargetKey> rdeps = ReverseDependencyMap.get(project);
    return libraryKeys.stream()
        .map(rdeps::get)
        .flatMap(Collection::stream)
        .map(targetMap::get)
        .filter(Objects::nonNull)
        .filter(t -> t.getKind().hasLanguage(LanguageClass.GO))
        .filter(t -> t.getKind().getRuleType() == RuleType.BINARY)
        .map(TargetIdeInfo::toTargetInfo)
        .findFirst()
        .orElse(null);
  }
}

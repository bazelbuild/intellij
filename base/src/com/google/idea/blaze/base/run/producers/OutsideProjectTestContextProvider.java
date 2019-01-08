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
package com.google.idea.blaze.base.run.producers;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverProvider;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.FakePsiElement;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * For situations where psi elements can't be efficiently resolved. Uses rough heuristics to
 * recognize test contexts, then does everything else asynchronously.
 */
class OutsideProjectTestContextProvider implements TestContextProvider {

  private static final ListeningExecutorService EXECUTOR =
      MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    if (context.getModule() != null) {
      return null;
    }
    PsiElement psi = context.getPsiLocation();
    if (!(psi instanceof PsiFileSystemItem)) {
      return null;
    }
    VirtualFile vf = ((PsiFileSystemItem) psi).getVirtualFile();
    if (vf == null) {
      return null;
    }
    WorkspacePath path = getWorkspacePath(context.getProject(), vf);
    if (path == null) {
      return null;
    }
    boolean isTestContext =
        Arrays.stream(HeuristicTestIdentifier.EP_NAME.getExtensions())
            .anyMatch(h -> h.isTestContext(path));
    if (!isTestContext) {
      return null;
    }
    ListenableFuture<RunConfigurationContext> future =
        EXECUTOR.submit(() -> findContextAsync(resolveContext(context, vf)));
    return TestContext.builder()
        .setContextFuture(future)
        .setSourceElement(psi)
        .setDescription(vf.getNameWithoutExtension())
        .build();
  }

  @Nullable
  private static RunConfigurationContext findContextAsync(ConfigurationContext context) {
    return Arrays.stream(TestContextProvider.EP_NAME.getExtensions())
        .filter(p -> !(p instanceof OutsideProjectTestContextProvider))
        .map(p -> ReadAction.compute(() -> p.getTestContext(context)))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static ConfigurationContext resolveContext(ConfigurationContext context, VirtualFile vf) {
    if (!(context.getPsiLocation() instanceof FakePsiElement)) {
      return context;
    }
    PsiFile psi =
        ReadAction.compute(() -> PsiManager.getInstance(context.getProject()).findFile(vf));
    return psi == null ? context : createContextForNewPsi(context, psi);
  }

  private static ConfigurationContext createContextForNewPsi(
      ConfigurationContext context, PsiElement psi) {
    // #api182 use ConfigurationContext#createEmptyContextForLocation
    Map<String, Object> map = new HashMap<>();
    map.put(CommonDataKeys.PROJECT.getName(), context.getProject());
    map.put(LangDataKeys.MODULE.getName(), context.getModule());
    map.put(Location.DATA_KEY.getName(), PsiLocation.fromPsiElement(psi));
    DataContext dataContext = SimpleDataContext.getSimpleContext(map, null);
    return ConfigurationContext.getFromContext(dataContext);
  }

  @Nullable
  private static WorkspacePath getWorkspacePath(Project project, VirtualFile vf) {
    WorkspacePathResolver resolver =
        WorkspacePathResolverProvider.getInstance(project).getPathResolver();
    if (resolver == null) {
      return null;
    }
    return resolver.getWorkspacePath(new File(vf.getPath()));
  }
}

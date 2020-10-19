/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.projectsystem;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.idea.blaze.android.ResourceRepositoryManagerCompat;
import com.google.idea.blaze.android.targetmap.ResourcePackageToTargetMap;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * Registers R packages corresponding to a fully qualified class name. For a fqcn "com.foo.bar.Baz",
 * this class registers the R class "com.foo.bar.R"
 *
 * <p>This hack is required because as4.1 has a bug that accidentally clears ResourceRepository when
 * it sees an R class for the first time. This makes a best effort to re-register the R packages
 * that might be accessed by Layout Editor.
 *
 * <p>TODO(b/170994302): #as41 Remove this hack once as4.1 is paved
 */
public class ResourceRepositoryUpdater {

  private static final BoolExperiment attachClassResourcePackage =
      new BoolExperiment("aswb.psiclsfinder.attach.cls.r.package", true);

  private ResourceRepositoryUpdater() {}

  static void registerResourcePackageForClass(
      Module module, @Nullable VirtualFile classFile, String fqcn) {
    if (classFile == null || !attachClassResourcePackage.getValue()) {
      return;
    }

    // For a fqcn "com.foo.Bar$Baz", we want to register the R class "com.foo.R". We check if the
    // project has any target that exports the package "com.foo" in the build graph, and register
    // "com.foo" with ResourceRegistry.
    // If the class is already present in ResourceRegistry, this step is a no-op as ResourceRegistry
    // already de-duplicates classes.
    Project project = module.getProject();
    int lastDotIdx = fqcn.lastIndexOf('.');
    String rPackageRoot = fqcn.substring(0, lastDotIdx);
    if (ResourcePackageToTargetMap.get(project).containsKey(rPackageRoot)) {
      registerResourcePackage(module, rPackageRoot);
    }
  }

  private static void registerResourcePackage(Module module, String resourcePackage) {
    if (StringUtil.isEmpty(resourcePackage)) {
      return;
    }

    ResourceRepositoryManager repositoryManager =
        ResourceRepositoryManagerCompat.getResourceRepositoryManager(module);

    ResourceIdManager idManager = ResourceIdManager.get(module);
    if (repositoryManager == null) {
      return;
    }

    ResourceNamespace namespace = ReadAction.compute(repositoryManager::getNamespace);
    ResourceClassRegistry.get(module.getProject())
        .addLibrary(
            ResourceRepositoryManagerCompat.getAppResources(repositoryManager),
            idManager,
            resourcePackage,
            namespace);
  }
}

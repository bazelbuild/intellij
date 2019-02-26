/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.resources;

import com.android.tools.idea.projectsystem.LightResourceClassService;
import com.android.tools.idea.res.AndroidLightPackage;
import com.android.tools.idea.res.ResourceRepositoryRClass;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Implementation of {@link LightResourceClassService} set up at Blaze sync time. */
public class BlazeLightResourceClassService implements LightResourceClassService {

  @NotNull private Map<String, BlazeRClass> rClasses = Maps.newHashMap();
  @NotNull private Map<String, PsiPackage> rClassPackages = Maps.newHashMap();

  // It should be harmless to create stub resource PsiPackages which shadow any "real" PsiPackages.
  // Based on the ordering of PsiElementFinder it would prefer the real package
  // (PsiElementFinderImpl has 'order="first"').
  // Put under experiment just in case we find a problem w/ other element finders.
  private static final BoolExperiment CREATE_STUB_RESOURCE_PACKAGES =
      new BoolExperiment("create.stub.resource.packages", true);

  public static BlazeLightResourceClassService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, BlazeLightResourceClassService.class);
  }

  /** Builds light R classes */
  public static class Builder {
    Map<String, BlazeRClass> rClassMap = Maps.newHashMap();
    Map<String, PsiPackage> rClassPackages = Maps.newHashMap();

    private final PsiManager psiManager;

    public Builder(Project project) {
      this.psiManager = PsiManager.getInstance(project);
    }

    public void addRClass(String resourceJavaPackage, Module module) {
      BlazeRClass rClass = new BlazeRClass(psiManager, module, resourceJavaPackage);
      rClassMap.put(getQualifiedRClassName(resourceJavaPackage), rClass);
      if (CREATE_STUB_RESOURCE_PACKAGES.getValue()) {
        addStubPackages(resourceJavaPackage);
      }
    }

    @NotNull
    private static String getQualifiedRClassName(@NotNull String packageName) {
      return packageName + ".R";
    }

    private void addStubPackages(String resourceJavaPackage) {
      while (!resourceJavaPackage.isEmpty()) {
        if (rClassPackages.containsKey(resourceJavaPackage)) {
          return;
        }
        rClassPackages.put(
            resourceJavaPackage,
            AndroidLightPackage.withName(resourceJavaPackage, psiManager.getProject()));
        int nextIndex = resourceJavaPackage.lastIndexOf('.');
        if (nextIndex < 0) {
          return;
        }
        resourceJavaPackage = resourceJavaPackage.substring(0, nextIndex);
      }
    }
  }

  public void installRClasses(Builder builder) {
    this.rClasses = builder.rClassMap;
    this.rClassPackages = builder.rClassPackages;
  }

  @Override
  @NotNull
  public List<PsiClass> getLightRClasses(
      @NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    ResourceRepositoryRClass rClass = this.rClasses.get(qualifiedName);
    if (rClass != null) {
      if (scope.isSearchInModuleContent(rClass.getModule())) {
        return ImmutableList.of(rClass);
      }
    }
    return ImmutableList.of();
  }

  @NotNull
  @Override
  public Collection<? extends PsiClass> getLightRClassesAccessibleFromModule(
      @NotNull Module module, boolean includeTest) {
    return rClasses.values();
  }

  @NotNull
  @Override
  public Collection<? extends PsiClass> getLightRClassesContainingModuleResources(
      @NotNull Module module) {
    return rClasses.values();
  }

  @Override
  @Nullable
  public PsiPackage findRClassPackage(@NotNull String qualifiedName) {
    return rClassPackages.get(qualifiedName);
  }

  @Override
  @NotNull
  public Collection<? extends PsiClass> getAllLightRClasses() {
    return rClasses.values();
  }
}

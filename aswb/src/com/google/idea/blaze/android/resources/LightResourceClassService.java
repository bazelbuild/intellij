/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.experiments.BoolExperiment;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A service for storing and finding light R classes.
 */
public class LightResourceClassService {

  @NotNull
  private Map<String, AndroidPackageRClass> rClasses = Maps.newHashMap();
  @NotNull
  private Map<String, PsiPackage> rClassPackages = Maps.newHashMap();

  // It should be harmless to create stub resource PsiPackages which shadow any "real" PsiPackages. Based on the ordering
  // of PsiElementFinder it would prefer the real package (PsiElementFinderImpl has 'order="first"').
  // Put under experiment just in case we find a problem w/ other element finders.
  private static final BoolExperiment CREATE_STUB_RESOURCE_PACKAGES = new BoolExperiment("create.stub.resource.packages", true);

  public LightResourceClassService() {
  }

  public static LightResourceClassService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, LightResourceClassService.class);
  }

  public static class Builder {
    Map<String, AndroidPackageRClass> rClassMap = Maps.newHashMap();
    Map<String, PsiPackage> rClassPackages = Maps.newHashMap();

    private final PsiManager psiManager;

    public Builder(Project project) {
      this.psiManager = PsiManager.getInstance(project);
    }

    public void addRClass(String resourceJavaPackage, Module module) {
      AndroidPackageRClass rClass = new AndroidPackageRClass(
        psiManager,
        resourceJavaPackage,
        module
      );
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
        rClassPackages.put(resourceJavaPackage, new AndroidResourcePackage(psiManager, resourceJavaPackage));
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

  @NotNull
  public List<PsiClass> getLightRClasses(
    @NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    AndroidPackageRClass rClass = this.rClasses.get(qualifiedName);
    if (rClass != null) {
      if (scope.isSearchInModuleContent(rClass.getModule())) {
        return ImmutableList.of(rClass);
      }
    }
    return ImmutableList.of();
  }

  @Nullable
  public PsiPackage findRClassPackage(String qualifiedName) {
    return rClassPackages.get(qualifiedName);
  }
}

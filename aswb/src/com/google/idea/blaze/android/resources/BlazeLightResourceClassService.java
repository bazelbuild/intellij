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
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

/** Implementation of {@link LightResourceClassService} set up at Blaze sync time. */
public class BlazeLightResourceClassService implements LightResourceClassService {

  private Map<String, BlazeRClass> rClasses = Maps.newHashMap();
  private Map<String, PsiPackage> rClassPackages = Maps.newHashMap();

  // It should be harmless to create stub resource PsiPackages which shadow any "real" PsiPackages.
  // Based on the ordering of PsiElementFinder it would prefer the real package
  // (PsiElementFinderImpl has 'order="first"').
  // Put under experiment just in case we find a problem w/ other element finders.
  private static final BoolExperiment CREATE_STUB_RESOURCE_PACKAGES =
      new BoolExperiment("create.stub.resource.packages", true);

  public static BlazeLightResourceClassService getInstance(Project project) {
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
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet == null) {
        return; // Do not register R class if android facet is not present.
      }
      BlazeRClass rClass = new BlazeRClass(psiManager, androidFacet, resourceJavaPackage);
      rClassMap.put(getQualifiedRClassName(resourceJavaPackage), rClass);
      if (CREATE_STUB_RESOURCE_PACKAGES.getValue()) {
        addStubPackages(resourceJavaPackage);
      }
    }

    private static String getQualifiedRClassName(String packageName) {
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
  public List<PsiClass> getLightRClasses(String qualifiedName, GlobalSearchScope scope) {
    BlazeRClass rClass = this.rClasses.get(qualifiedName);
    if (rClass != null) {
      if (scope.isSearchInModuleContent(rClass.getModule())) {
        return ImmutableList.of(rClass);
      }
    }
    return ImmutableList.of();
  }

  @Override
  public Collection<? extends PsiClass> getLightRClassesAccessibleFromModule(
      Module module, boolean includeTest) {
    return rClasses.values();
  }

  @Override
  public Collection<? extends PsiClass> getLightRClassesContainingModuleResources(Module module) {
    return rClasses.values();
  }

  @Override
  @Nullable
  public PsiPackage findRClassPackage(String qualifiedName) {
    return rClassPackages.get(qualifiedName);
  }

  @Override
  public Collection<? extends PsiClass> getAllLightRClasses() {
    return rClasses.values();
  }
}

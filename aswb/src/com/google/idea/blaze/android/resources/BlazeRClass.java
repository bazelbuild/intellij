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
package com.google.idea.blaze.android.resources;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.res.ResourceRepositoryRClass;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.augment.AndroidLightField;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/** Blaze implementation of an R class based on resource repositories. */
public class BlazeRClass extends ResourceRepositoryRClass {

  @NotNull private final AndroidFacet androidFacet;

  public BlazeRClass(
      @NotNull PsiManager psiManager,
      @NotNull AndroidFacet androidFacet,
      @NotNull String packageName) {
    super(
        psiManager,
        new ResourcesSource() {
          @NotNull
          @Override
          public String getPackageName() {
            return packageName;
          }

          @NotNull
          @Override
          public LocalResourceRepository getResourceRepository() {
            return ResourceRepositoryManager.getAppResources(androidFacet);
          }

          @NotNull
          @Override
          public ResourceNamespace getResourceNamespace() {
            return ResourceNamespace.RES_AUTO;
          }

          @NotNull
          @Override
          public AndroidLightField.FieldModifier getFieldModifier() {
            return AndroidLightField.FieldModifier.NON_FINAL;
          }

          @Override
          public boolean isPublic(
              @NotNull ResourceType resourceType, @NotNull String resourceName) {
            return true;
          }
        });
    this.androidFacet = androidFacet;
    setModuleInfo(getModule(), false);
  }

  @NotNull
  public Module getModule() {
    return androidFacet.getModule();
  }
}

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
package com.google.idea.blaze.android.projectsystem;

import static org.jetbrains.android.facet.SourceProviderUtil.createIdeaSourceProviderFromModelSourceProvider;
import static org.jetbrains.android.facet.SourceProviderUtil.createSourceProvidersForLegacyModule;

import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.IdeaSourceProvider;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.android.tools.idea.projectsystem.SourceProvidersFactory;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.sync.model.idea.BlazeAndroidModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.SourceProvidersImpl;
import org.jetbrains.annotations.NotNull;

/** Blaze Implementation of {@link AndroidProjectSystem}. */
public class BlazeProjectSystem extends BlazeProjectSystemBase {

  public BlazeProjectSystem(Project project) {
    super(project);
  }

  @NotNull
  @Override
  public SourceProvidersFactory getSourceProvidersFactory() {
    return new SourceProvidersFactory() {
      @Nullable
      @Override
      public SourceProviders createSourceProvidersFor(@NotNull AndroidFacet facet) {
        BlazeAndroidModel model = ((BlazeAndroidModel) AndroidModel.get(facet));
        if (model != null) {
          IdeaSourceProvider mainSourceProvider =
              createIdeaSourceProviderFromModelSourceProvider(model.getDefaultSourceProvider());
          return new SourceProvidersImpl(
              mainSourceProvider,
              ImmutableList.of(mainSourceProvider),
              ImmutableList.of(mainSourceProvider),
              ImmutableList.of(mainSourceProvider),
              ImmutableList.of(mainSourceProvider));
        } else {
          return createSourceProvidersForLegacyModule(facet);
        }
      }
    };
  }

  @NotNull
  @Override
  public Collection<Module> getSubmodules() {
    return ImmutableList.of();
  }
}

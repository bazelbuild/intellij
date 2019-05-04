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
package com.google.idea.blaze.android.sync.importer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.sync.importer.aggregators.AndroidResourceModuleFactory;
import com.google.idea.blaze.android.sync.importer.problems.GeneratedResourceWarnings;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Output;
import com.intellij.openapi.project.Project;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Builds a BlazeWorkspace. */
public final class BlazeAndroidWorkspaceImporter {

  private final Project project;
  private final Consumer<Output> context;
  private final BlazeImportInput input;
  ImmutableSet<String> whitelistedGenResourcePaths;
  private final WhitelistFilter whitelistFilter;

  public BlazeAndroidWorkspaceImporter(
      Project project, BlazeContext context, BlazeImportInput input) {

    this(project, BlazeImportUtil.asConsumer(context), input);
  }

  public BlazeAndroidWorkspaceImporter(
      Project project, Consumer<Output> context, BlazeImportInput input) {
    this.context = context;
    this.input = input;
    this.project = project;
    whitelistedGenResourcePaths =
        BlazeImportUtil.getWhitelistedGenResourcePaths(input.projectViewSet);
    whitelistFilter = new WhitelistFilter(whitelistedGenResourcePaths);
  }

  public BlazeAndroidImportResult importWorkspace(
      AndroidResourceModuleFactory androidResourceModuleFactory) {
    List<TargetIdeInfo> sourceTargets = BlazeImportUtil.getSourceTargets(input);
    LibraryFactory libraries = new LibraryFactory();
    ImmutableList.Builder<AndroidResourceModule> resourceModules = new ImmutableList.Builder<>();
    Map<TargetKey, AndroidResourceModule.Builder> targetKeyToAndroidResourceModuleBuilder =
        new HashMap<>();

    ImmutableSet<String> whitelistedGenResourcePaths =
        BlazeImportUtil.getWhitelistedGenResourcePaths(input.projectViewSet);
    for (TargetIdeInfo target : sourceTargets) {
      if (ModuleTester.shouldCreateModule(target.getAndroidIdeInfo(), whitelistFilter)) {
        AndroidResourceModule.Builder androidResourceModuleBuilder =
            androidResourceModuleFactory.getOrCreateResourceModuleBuilder(
                target, libraries, targetKeyToAndroidResourceModuleBuilder);
        resourceModules.add(androidResourceModuleBuilder.build());
      }
    }

    GeneratedResourceWarnings.submit(
        context::accept,
        project,
        input.projectViewSet,
        input.artifactLocationDecoder,
        whitelistFilter.testedAgainstWhitelist,
        whitelistedGenResourcePaths);

    ImmutableList<AndroidResourceModule> androidResourceModules =
        androidResourceModuleFactory.buildAndroidResourceModules(resourceModules.build());
    return new BlazeAndroidImportResult(
        androidResourceModules,
        libraries.getBlazeResourceLibs(),
        libraries.getAarLibs(),
        BlazeImportUtil.getJavacJars(input.targetMap.targets()));
  }

  public BlazeAndroidImportResult importWorkspace() {
    return importWorkspace(new AndroidResourceModuleFactory(context, whitelistFilter, input));
  }
}

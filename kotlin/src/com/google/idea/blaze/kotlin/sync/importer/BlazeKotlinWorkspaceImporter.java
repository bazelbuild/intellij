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
package com.google.idea.blaze.kotlin.sync.importer;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.kotlin.BlazeKotlin;
import com.google.idea.blaze.kotlin.sync.model.BlazeKotlinImportResult;
import com.google.idea.blaze.kotlin.sync.model.BlazeKotlinToolchainIdeInfo;
import com.intellij.openapi.project.Project;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Stream;

public class BlazeKotlinWorkspaceImporter {
  private static final Gson gson =
      new GsonBuilder()
          .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
          .create();

  private final TargetMap targetMap;
  private final ProjectViewTargetImportFilter importFilter;
  private final ArtifactLocationDecoder artifactLocationDecoder;
  private final BlazeContext context;

  public BlazeKotlinWorkspaceImporter(
      Project project,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      TargetMap targetMap,
      BlazeContext context,
      ArtifactLocationDecoder artifactLocationDecoder) {
    this.targetMap = targetMap;
    importFilter = new ProjectViewTargetImportFilter(project, workspaceRoot, projectViewSet);
    this.context = context;
    this.artifactLocationDecoder = artifactLocationDecoder;
  }

  public BlazeKotlinImportResult importWorkspace() {
    HashMap<LibraryKey, BlazeJarLibrary> libraries = new HashMap<>();
    HashMap<TargetIdeInfo, ImmutableList<BlazeJarLibrary>> targetLibraryMap = new HashMap<>();
    HashSet<TargetIdeInfo> toolchainTargetIdeInfos = new HashSet<>();

    collectTransitiveLibsFromKotlinSourceTargets(
        libraries, targetLibraryMap, toolchainTargetIdeInfos);

    return new BlazeKotlinImportResult(
        ImmutableList.copyOf(libraries.values()),
        ImmutableMap.copyOf(targetLibraryMap),
        renderToolchainInfo(toolchainTargetIdeInfos));
  }

  private void collectTransitiveLibsFromKotlinSourceTargets(
      HashMap<LibraryKey, BlazeJarLibrary> libraries,
      HashMap<TargetIdeInfo, ImmutableList<BlazeJarLibrary>> targetLibraryMap,
      HashSet<TargetIdeInfo> toolchainInfos) {
    targetMap
        .targets()
        .stream()
        .filter(target -> target.kind.languageClass.equals(LanguageClass.KOTLIN))
        .peek(
            target -> {
              if (target.kind.equals(Kind.KT_TOOLCHAIN_IDE_INFO)) toolchainInfos.add(target);
            })
        .filter(importFilter::isSourceTarget)
        .flatMap(this::expandWithKotlinTargets)
        .forEach(
            depIdeInfo -> {
              //noinspection ConstantConditions
              BlazeJarLibrary[] transitiveLibraries =
                  depIdeInfo
                      .javaIdeInfo
                      .jars
                      .stream()
                      .map(BlazeJarLibrary::new)
                      .peek(depJar -> libraries.putIfAbsent(depJar.key, depJar))
                      .toArray(BlazeJarLibrary[]::new);
              targetLibraryMap.put(depIdeInfo, ImmutableList.copyOf(transitiveLibraries));
            });
  }

  private Stream<TargetIdeInfo> expandWithKotlinTargets(TargetIdeInfo target) {
    return Stream.concat(
        Stream.of(target),
        // all transitive targets with a java ide info that are also kotlin providers.
        TransitiveDependencyMap.getTransitiveDependencies(target.key, targetMap)
            .stream()
            .map(targetMap::get)
            .filter(Objects::nonNull)
            .filter(info -> info.javaIdeInfo != null)
            .filter(depIdeInfo -> depIdeInfo.kind.languageClass == LanguageClass.KOTLIN));
  }

  @Nullable
  private BlazeKotlinToolchainIdeInfo renderToolchainInfo(HashSet<TargetIdeInfo> targetIdeInfos) {
    switch (targetIdeInfos.size()) {
      case 0:
        return null;
      case 1:
        File location =
            artifactLocationDecoder.decode(
                Objects.requireNonNull(targetIdeInfos.iterator().next().ktToolchainIdeInfo)
                    .location);
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(location))) {
          return gson.fromJson(reader, BlazeKotlinToolchainIdeInfo.class);
        } catch (Exception e) {
          IssueOutput.error(
                  BlazeKotlin.Issues.ERROR_RENDERING_KT_TOOLCHAIN_IDE_INFO.apply(
                      Throwables.getRootCause(e).getMessage()))
              .submit(context);
          return null;
        }
      default:
        // A toolchain ide info should be a singleton. It is currently populated from the singleton
        // rule kt_toolchain_ide_info. The toolchain Ide info is
        // used to configure the project -- this is global. Whilst it is conceivable for the
        // workspace to have multiple compiler configurations active
        // for Kotlin for now it is an error for multiple configurations to enter the sync model, at
        // least configurations generated from the
        // kt_toolchain_ide_info rule.
        IssueOutput.error(BlazeKotlin.Issues.MULTIPLE_KT_TOOLCHAIN_IDE_INFO).submit(context);
        return null;
    }
  }
}

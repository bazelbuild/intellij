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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.idea.blaze.android.projectview.GeneratedAndroidResourcesSection;
import com.google.idea.blaze.android.sync.importer.aggregators.TransitiveResourceMap;
import com.google.idea.blaze.android.sync.importer.problems.GeneratedResourceWarnings;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.android.sync.model.BlazeResourceLibrary;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PerformanceWarning;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.importer.JavaSourceFilter;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/** Builds a BlazeWorkspace. */
public final class BlazeAndroidWorkspaceImporter {

  private final Project project;
  private final BlazeContext context;
  private final TargetMap targetMap;
  private final ProjectViewTargetImportFilter importFilter;
  private final ProjectViewSet projectViewSet;
  private final JavaSourceFilter sourceFilter;
  private final ArtifactLocationDecoder artifactLocationDecoder;
  private final ImmutableSet<String> whitelistedGenResourcePaths;

  public BlazeAndroidWorkspaceImporter(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      TargetMap targetMap,
      JavaSourceFilter sourceFilter,
      ArtifactLocationDecoder artifactLocationDecoder) {
    this.context = context;
    this.targetMap = targetMap;
    this.importFilter =
        new ProjectViewTargetImportFilter(
            Blaze.getBuildSystem(project), workspaceRoot, projectViewSet);
    this.projectViewSet = projectViewSet;
    this.sourceFilter = sourceFilter;
    this.artifactLocationDecoder = artifactLocationDecoder;
    this.project = project;
    this.whitelistedGenResourcePaths =
        ImmutableSet.copyOf(
            projectViewSet.listItems(GeneratedAndroidResourcesSection.KEY).stream()
                .map(genfilesPath -> genfilesPath.relativePath)
                .collect(Collectors.toSet()));
  }

  public BlazeAndroidImportResult importWorkspace() {
    List<TargetIdeInfo> sourceTargets =
        targetMap.targets().stream()
            .filter(target -> target.getKind().languageClass == LanguageClass.ANDROID)
            .filter(target -> target.getAndroidIdeInfo() != null)
            .filter(importFilter::isSourceTarget)
            .filter(target -> !importFilter.excludeTarget(target))
            .collect(Collectors.toList());

    TransitiveResourceMap transitiveResourceMap = new TransitiveResourceMap(targetMap);

    WorkspaceBuilder workspaceBuilder = new WorkspaceBuilder();

    for (TargetIdeInfo target : sourceTargets) {
      addSourceTarget(workspaceBuilder, transitiveResourceMap, target);
    }

    GeneratedResourceWarnings.submit(
        project,
        context,
        projectViewSet,
        artifactLocationDecoder,
        workspaceBuilder.generatedResourceLocations,
        whitelistedGenResourcePaths);

    ImmutableList<AndroidResourceModule> androidResourceModules =
        buildAndroidResourceModules(workspaceBuilder);
    BlazeResourceLibrary resourceLibrary = createResourceLibrary(androidResourceModules);
    ImmutableList<AarLibrary> aarLibraries = createAarLibraries(sourceFilter.getLibraryTargets());

    return new BlazeAndroidImportResult(
        androidResourceModules, resourceLibrary, aarLibraries, getJavacJar(targetMap.targets()));
  }

  private static ArtifactLocation getJavacJar(Collection<TargetIdeInfo> targets) {
    return targets.stream()
        .filter(target -> target.getKind() == Kind.JAVA_TOOLCHAIN)
        .map(TargetIdeInfo::getJavaToolchainIdeInfo)
        .filter(Objects::nonNull)
        .map(JavaToolchainIdeInfo::getJavacJar)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private void addSourceTarget(
      WorkspaceBuilder workspaceBuilder,
      TransitiveResourceMap transitiveResourceMap,
      TargetIdeInfo target) {
    AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
    assert androidIdeInfo != null;
    if (shouldGenerateResources(androidIdeInfo)
        && shouldGenerateResourceModule(androidIdeInfo, whitelistedGenResourcePaths)) {
      AndroidResourceModule.Builder builder = new AndroidResourceModule.Builder(target.getKey());
      workspaceBuilder.androidResourceModules.add(builder);

      for (ArtifactLocation artifactLocation : androidIdeInfo.getResources()) {
        if (artifactLocation.isSource()) {
          builder.addResource(artifactLocation);
        } else {
          workspaceBuilder.generatedResourceLocations.add(artifactLocation);
          if (whitelistedGenResourcePaths.contains(artifactLocation.getRelativePath())) {
            // Still track location in generatedResourceLocations, so that we can warn if a
            // whitelist entry goes unused and can be removed.
            builder.addResource(artifactLocation);
          }
        }
      }

      TransitiveResourceMap.TransitiveResourceInfo transitiveResourceInfo =
          transitiveResourceMap.get(target.getKey());
      for (ArtifactLocation artifactLocation : transitiveResourceInfo.transitiveResources) {
        if (artifactLocation.isSource()) {
          builder.addTransitiveResource(artifactLocation);
        } else {
          workspaceBuilder.generatedResourceLocations.add(artifactLocation);
          if (whitelistedGenResourcePaths.contains(artifactLocation.getRelativePath())) {
            builder.addTransitiveResource(artifactLocation);
          }
        }
      }
      for (TargetKey resourceDependency : transitiveResourceInfo.transitiveResourceTargets) {
        if (!resourceDependency.equals(target.getKey())) {
          builder.addTransitiveResourceDependency(resourceDependency);
        }
      }
    }
  }

  public static boolean shouldGenerateResources(AndroidIdeInfo androidIdeInfo) {
    // Generate an android resource module if this rule defines resources
    // We don't want to generate one if this depends on a legacy resource rule through :resources
    // In this case, the resource information is redundantly forwarded to this class for
    // backwards compatibility, but the android_resource rule itself is already generating
    // the android resource module
    return androidIdeInfo.generateResourceClass() && androidIdeInfo.getLegacyResources() == null;
  }

  public static boolean shouldGenerateResourceModule(
      AndroidIdeInfo androidIdeInfo, Set<String> whitelistedGenResourcePaths) {
    return androidIdeInfo.getResources().stream()
        .anyMatch(location -> isSourceOrWhitelistedGenPath(location, whitelistedGenResourcePaths));
  }

  private static boolean isSourceOrWhitelistedGenPath(
      ArtifactLocation artifactLocation, Set<String> whitelistedGenResourcePaths) {
    return artifactLocation.isSource()
        || whitelistedGenResourcePaths.contains(artifactLocation.getRelativePath());
  }

  @Nullable
  private BlazeResourceLibrary createResourceLibrary(
      Collection<AndroidResourceModule> androidResourceModules) {
    Set<ArtifactLocation> result = Sets.newHashSet();
    for (AndroidResourceModule androidResourceModule : androidResourceModules) {
      result.addAll(androidResourceModule.transitiveResources);
    }
    for (AndroidResourceModule androidResourceModule : androidResourceModules) {
      result.removeAll(androidResourceModule.resources);
    }
    if (!result.isEmpty()) {
      return new BlazeResourceLibrary(
          ImmutableList.copyOf(result.stream().sorted().collect(Collectors.toList())));
    }
    return null;
  }

  private ImmutableList<AarLibrary> createAarLibraries(Iterable<TargetIdeInfo> libraryTargets) {
    ImmutableList.Builder<AarLibrary> builder = ImmutableList.builder();
    for (TargetIdeInfo target : libraryTargets) {
      // NOTE: we are not doing jdeps optimization, even though we have the jdeps data for the AAR's
      // jar. The aar might still have resources that are used (e.g., @string/foo in .xml), and we
      // don't have the equivalent of jdeps data.
      if (target.getAndroidAarIdeInfo() == null
          || target.getJavaIdeInfo() == null
          || target.getJavaIdeInfo().getJars().isEmpty()) {
        continue;
      }
      // aar_import should only have one jar (a merged jar from the AAR's jars).
      LibraryArtifact firstJar = target.getJavaIdeInfo().getJars().iterator().next();
      builder.add(new AarLibrary(firstJar, target.getAndroidAarIdeInfo().getAar()));
    }
    return builder.build();
  }

  private ImmutableList<AndroidResourceModule> buildAndroidResourceModules(
      WorkspaceBuilder workspaceBuilder) {
    // Filter empty resource modules
    Stream<AndroidResourceModule> androidResourceModuleStream =
        workspaceBuilder.androidResourceModules.stream()
            .map(AndroidResourceModule.Builder::build)
            .filter(androidResourceModule -> !androidResourceModule.isEmpty())
            .filter(androidResourceModule -> !androidResourceModule.resources.isEmpty());
    List<AndroidResourceModule> androidResourceModules =
        androidResourceModuleStream.collect(Collectors.toList());

    // Detect, filter, and warn about multiple R classes
    Multimap<String, AndroidResourceModule> javaPackageToResourceModule =
        ArrayListMultimap.create();
    for (AndroidResourceModule androidResourceModule : androidResourceModules) {
      TargetIdeInfo target = targetMap.get(androidResourceModule.targetKey);
      AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
      assert androidIdeInfo != null;
      javaPackageToResourceModule.put(
          androidIdeInfo.getResourceJavaPackage(), androidResourceModule);
    }

    List<AndroidResourceModule> result = Lists.newArrayList();
    for (String resourceJavaPackage : javaPackageToResourceModule.keySet()) {
      Collection<AndroidResourceModule> androidResourceModulesWithJavaPackage =
          javaPackageToResourceModule.get(resourceJavaPackage);

      if (androidResourceModulesWithJavaPackage.size() == 1) {
        result.addAll(androidResourceModulesWithJavaPackage);
      } else {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder
            .append("Multiple R classes generated with the same java package ")
            .append(resourceJavaPackage)
            .append(".R: ");
        messageBuilder.append('\n');
        for (AndroidResourceModule androidResourceModule : androidResourceModulesWithJavaPackage) {
          messageBuilder.append("  ").append(androidResourceModule.targetKey).append('\n');
        }
        String message = messageBuilder.toString();
        context.output(new PerformanceWarning(message));
        IssueOutput.warn(message).submit(context);

        result.add(selectBestAndroidResourceModule(androidResourceModulesWithJavaPackage));
      }
    }

    Collections.sort(result, (lhs, rhs) -> lhs.targetKey.compareTo(rhs.targetKey));
    return ImmutableList.copyOf(result);
  }

  private AndroidResourceModule selectBestAndroidResourceModule(
      Collection<AndroidResourceModule> androidResourceModulesWithJavaPackage) {
    return androidResourceModulesWithJavaPackage.stream()
        .max(
            (lhs, rhs) ->
                ComparisonChain.start()
                    .compare(lhs.resources.size(), rhs.resources.size()) // Most resources wins
                    .compare(
                        lhs.transitiveResources.size(),
                        rhs.transitiveResources.size()) // Most transitive resources wins
                    .compare(
                        rhs.targetKey.toString().length(),
                        lhs.targetKey
                            .toString()
                            .length()) // Shortest label wins - note lhs, rhs are flipped
                    .result())
        .get();
  }

  static class WorkspaceBuilder {
    List<AndroidResourceModule.Builder> androidResourceModules = Lists.newArrayList();
    Set<ArtifactLocation> generatedResourceLocations = Sets.newHashSet();
  }
}

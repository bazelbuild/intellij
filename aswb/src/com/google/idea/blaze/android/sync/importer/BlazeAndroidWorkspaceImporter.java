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
package com.google.idea.blaze.android.sync.importer;

import com.google.common.collect.*;
import com.google.idea.blaze.android.sync.importer.aggregators.TransitiveResourceMap;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.base.experiments.BoolExperiment;
import com.google.idea.blaze.base.ideinfo.AndroidRuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PerformanceWarning;
import com.google.idea.blaze.base.sync.projectview.ProjectViewRuleImportFilter;
import com.google.idea.blaze.java.sync.model.BlazeLibrary;
import com.google.idea.blaze.java.sync.model.LibraryKey;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds a BlazeWorkspace.
 */
public final class BlazeAndroidWorkspaceImporter {
  private static final Logger LOG = Logger.getInstance(BlazeAndroidWorkspaceImporter.class);
  private static final BoolExperiment DISCARD_ANDROID_BINARY_RESOURCE_JAR = new BoolExperiment("discard.android.binary.resource.jar", true);

  private final Project project;
  private final BlazeContext context;
  private final WorkspaceRoot workspaceRoot;
  private final ImmutableMap<Label, RuleIdeInfo> ruleMap;
  private final ProjectViewRuleImportFilter importFilter;
  private final boolean discardAndroidBinaryResourceJar;

  public BlazeAndroidWorkspaceImporter(
    Project project,
    BlazeContext context,
    WorkspaceRoot workspaceRoot,
    ProjectViewSet projectViewSet,
    ImmutableMap<Label, RuleIdeInfo> ruleMap) {
    this.project = project;
    this.context = context;
    this.workspaceRoot = workspaceRoot;
    this.ruleMap = ruleMap;
    this.importFilter = new ProjectViewRuleImportFilter(project, workspaceRoot, projectViewSet);
    this.discardAndroidBinaryResourceJar = DISCARD_ANDROID_BINARY_RESOURCE_JAR.getValue();
  }

  public BlazeAndroidImportResult importWorkspace() {
    List<RuleIdeInfo> rules = ruleMap.values()
      .stream()
      .filter(rule -> rule.kind.getLanguageClass() == LanguageClass.ANDROID)
      .filter(importFilter::isSourceRule)
      .filter(rule -> !importFilter.excludeTarget(rule))
      .collect(Collectors.toList());

    TransitiveResourceMap transitiveResourceMap = new TransitiveResourceMap(ruleMap);

    WorkspaceBuilder workspaceBuilder = new WorkspaceBuilder();

    for (RuleIdeInfo rule : rules) {
      addRule(
        workspaceBuilder,
        transitiveResourceMap,
        rule
      );
    }

    ImmutableList<AndroidResourceModule> androidResourceModules = buildAndroidResourceModules(workspaceBuilder);
    BlazeLibrary resourceLibrary = createResourceLibrary(androidResourceModules);
    if (resourceLibrary != null) {
      workspaceBuilder.libraries.add(resourceLibrary);
    }

    return new BlazeAndroidImportResult(
      androidResourceModules,
      workspaceBuilder.libraries.build()
    );
  }

  private void addRule(
    WorkspaceBuilder workspaceBuilder,
    TransitiveResourceMap transitiveResourceMap,
    RuleIdeInfo rule) {

    AndroidRuleIdeInfo androidRuleIdeInfo = rule.androidRuleIdeInfo;
    if (androidRuleIdeInfo != null) {
      // Generate an android resource module if this rule defines resources
      // We don't want to generate one if this depends on a legacy resource rule through :resources
      // In this case, the resource information is redundantly forwarded to this class for
      // backwards compatibility, but the android_resource rule itself is already generating
      // the android resource module
      if (androidRuleIdeInfo.generateResourceClass && androidRuleIdeInfo.legacyResources == null) {
        List<ArtifactLocation> nonGeneratedResources = Lists.newArrayList();
        for (ArtifactLocation artifactLocation : androidRuleIdeInfo.resources) {
          if (!artifactLocation.isGenerated()) {
            nonGeneratedResources.add(artifactLocation);
          }
        }

        // Only create a resource module if there are any non-generated resources
        // Empty R classes or ones with only generated sources are added as jars
        if (!nonGeneratedResources.isEmpty()) {
          AndroidResourceModule.Builder builder = new AndroidResourceModule.Builder(rule.label);
          workspaceBuilder.androidResourceModules.add(builder);

          builder.addAllResources(nonGeneratedResources);

          TransitiveResourceMap.TransitiveResourceInfo transitiveResourceInfo = transitiveResourceMap.get(rule.label);
          for (ArtifactLocation artifactLocation : transitiveResourceInfo.transitiveResources) {
            if (!artifactLocation.isGenerated()) {
              builder.addTransitiveResource(artifactLocation);
            }
          }
          for (Label resourceDependency : transitiveResourceInfo.transitiveResourceRules) {
            if (!resourceDependency.equals(rule.label)) {
              builder.addTransitiveResourceDependency(resourceDependency);
            }
          }
        } else {
          // Add blaze's output unless it's a top level rule. In these cases the resource jar contains the entire
          // transitive closure of R classes. It's unlikely this is wanted to resolve in the IDE.
          boolean discardResourceJar = discardAndroidBinaryResourceJar && rule.kindIsOneOf(Kind.ANDROID_BINARY, Kind.ANDROID_TEST);
          if (!discardResourceJar) {
            LibraryArtifact resourceJar = androidRuleIdeInfo.resourceJar;
            if (resourceJar != null) {
              BlazeLibrary library = new BlazeLibrary(LibraryKey.fromJarFile(resourceJar.jar.getFile()), resourceJar);
              workspaceBuilder.libraries.add(library);
            }
          }
        }
      }

      LibraryArtifact idlJar = androidRuleIdeInfo.idlJar;
      if (idlJar != null) {
        BlazeLibrary library = new BlazeLibrary(LibraryKey.fromJarFile(idlJar.jar.getFile()), idlJar);
        workspaceBuilder.libraries.add(library);
      }
    }
  }

  @Nullable
  private BlazeLibrary createResourceLibrary(Collection<AndroidResourceModule> androidResourceModules) {
    Set<File> result = Sets.newHashSet();
    for (AndroidResourceModule androidResourceModule : androidResourceModules) {
      result.addAll(androidResourceModule.transitiveResources);
    }
    for (AndroidResourceModule androidResourceModule : androidResourceModules) {
      result.removeAll(androidResourceModule.resources);
    }
    if (!result.isEmpty()) {
      return new BlazeLibrary(LibraryKey.forResourceLibrary(),
                              ImmutableList.copyOf(result.stream().sorted().collect(Collectors.toList())));
    }
    return null;
  }

  @NotNull
  private ImmutableList<AndroidResourceModule> buildAndroidResourceModules(WorkspaceBuilder workspaceBuilder) {
    // Filter empty resource modules
    Stream<AndroidResourceModule> androidResourceModuleStream = workspaceBuilder.androidResourceModules
      .stream()
      .map(AndroidResourceModule.Builder::build)
      .filter(androidResourceModule -> !androidResourceModule.isEmpty())
      .filter(androidResourceModule -> !androidResourceModule.resources.isEmpty());
    List<AndroidResourceModule> androidResourceModules = androidResourceModuleStream.collect(Collectors.toList());

    // Detect, filter, and warn about multiple R classes
    Multimap<String, AndroidResourceModule> javaPackageToResourceModule = ArrayListMultimap.create();
    for (AndroidResourceModule androidResourceModule : androidResourceModules) {
      RuleIdeInfo rule = ruleMap.get(androidResourceModule.label);
      AndroidRuleIdeInfo androidRuleIdeInfo = rule.androidRuleIdeInfo;
      assert androidRuleIdeInfo != null;
      javaPackageToResourceModule.put(androidRuleIdeInfo.resourceJavaPackage, androidResourceModule);
    }

    List<AndroidResourceModule> result = Lists.newArrayList();
    for (String resourceJavaPackage : javaPackageToResourceModule.keySet()) {
      Collection<AndroidResourceModule> androidResourceModulesWithJavaPackage = javaPackageToResourceModule.get(resourceJavaPackage);

      if (androidResourceModulesWithJavaPackage.size() == 1) {
        result.addAll(androidResourceModulesWithJavaPackage);
      }
      else {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("Multiple R classes generated with the same java package ").append(resourceJavaPackage).append(".R: ");
        messageBuilder.append('\n');
        for (AndroidResourceModule androidResourceModule : androidResourceModulesWithJavaPackage) {
          messageBuilder.append("  ").append(androidResourceModule.label).append('\n');
        }
        String message = messageBuilder.toString();
        context.output(new PerformanceWarning(message));
        IssueOutput
          .warn(message)
          .submit(context);

        result.add(selectBestAndroidResourceModule(androidResourceModulesWithJavaPackage));
      }
    }

    Collections.sort(result, (lhs, rhs) -> Label.COMPARATOR.compare(lhs.label, rhs.label));
    return ImmutableList.copyOf(result);
  }

  private AndroidResourceModule selectBestAndroidResourceModule(Collection<AndroidResourceModule> androidResourceModulesWithJavaPackage) {
    return androidResourceModulesWithJavaPackage
      .stream()
      .max((lhs, rhs) -> ComparisonChain.start()
        .compare(lhs.resources.size(), rhs.resources.size()) // Most resources wins
        .compare(lhs.transitiveResources.size(), rhs.transitiveResources.size()) // Most transitive resources wins
        .compare(rhs.label.toString().length(), lhs.label.toString().length()) // Shortest label wins - note lhs, rhs are flipped
        .result())
      .get();
  }

  static class WorkspaceBuilder {
    List<AndroidResourceModule.Builder> androidResourceModules = Lists.newArrayList();
    ImmutableList.Builder<BlazeLibrary> libraries = ImmutableList.builder();
  }
}

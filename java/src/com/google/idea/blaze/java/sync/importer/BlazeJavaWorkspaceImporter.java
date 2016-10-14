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
package com.google.idea.blaze.java.sync.importer;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaRuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.ProtoLibraryLegacyInfo;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleKey;
import com.google.idea.blaze.base.ideinfo.RuleMap;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.SourceTestConfig;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.DuplicateSourceDetector;
import com.google.idea.blaze.java.sync.jdeps.JdepsMap;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.model.LibraryKey;
import com.google.idea.blaze.java.sync.source.SourceArtifact;
import com.google.idea.blaze.java.sync.source.SourceDirectoryCalculator;
import com.google.idea.blaze.java.sync.workingset.JavaWorkingSet;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Builds a BlazeWorkspace. */
public final class BlazeJavaWorkspaceImporter {
  private final Project project;
  private final WorkspaceRoot workspaceRoot;
  private final ImportRoots importRoots;
  private final RuleMap ruleMap;
  private final SourceTestConfig sourceTestConfig;
  private final JdepsMap jdepsMap;
  @Nullable private final JavaWorkingSet workingSet;
  private final ArtifactLocationDecoder artifactLocationDecoder;
  private final DuplicateSourceDetector duplicateSourceDetector = new DuplicateSourceDetector();
  private final Collection<BlazeJavaSyncAugmenter> augmenters;
  private final JavaSourceFilter sourceFilter;

  public BlazeJavaWorkspaceImporter(
      Project project,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      RuleMap ruleMap,
      JavaSourceFilter sourceFilter,
      JdepsMap jdepsMap,
      @Nullable JavaWorkingSet workingSet,
      ArtifactLocationDecoder artifactLocationDecoder) {
    this.project = project;
    this.workspaceRoot = workspaceRoot;
    this.importRoots =
        ImportRoots.builder(workspaceRoot, Blaze.getBuildSystem(project))
            .add(projectViewSet)
            .build();
    this.ruleMap = ruleMap;
    this.sourceFilter = sourceFilter;
    this.jdepsMap = jdepsMap;
    this.workingSet = workingSet;
    this.artifactLocationDecoder = artifactLocationDecoder;
    this.sourceTestConfig = new SourceTestConfig(projectViewSet);
    this.augmenters = BlazeJavaSyncAugmenter.getActiveSyncAgumenters(workspaceLanguageSettings);
  }

  public BlazeJavaImportResult importWorkspace(BlazeContext context) {
    WorkspaceBuilder workspaceBuilder = new WorkspaceBuilder();
    for (RuleIdeInfo rule : sourceFilter.sourceRules) {
      addRuleAsSource(workspaceBuilder, rule, sourceFilter.ruleToJavaSources.get(rule.key));
    }

    SourceDirectoryCalculator sourceDirectoryCalculator = new SourceDirectoryCalculator();
    ImmutableList<BlazeContentEntry> contentEntries =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            sourceTestConfig,
            artifactLocationDecoder,
            importRoots.rootDirectories(),
            workspaceBuilder.sourceArtifacts,
            workspaceBuilder.javaPackageManifests);

    int totalContentEntryCount = 0;
    for (BlazeContentEntry contentEntry : contentEntries) {
      totalContentEntryCount += contentEntry.sources.size();
    }
    context.output(PrintOutput.log("Java content entry count: " + totalContentEntryCount));

    ImmutableMap<LibraryKey, BlazeJarLibrary> libraries =
        buildLibraries(
            workspaceBuilder, ruleMap, sourceFilter.libraryRules, sourceFilter.protoLibraries);

    duplicateSourceDetector.reportDuplicates(context);

    String sourceVersion = findSourceVersion(ruleMap);

    return new BlazeJavaImportResult(
        contentEntries,
        libraries,
        ImmutableList.copyOf(
            workspaceBuilder.buildOutputJars.stream().sorted().collect(Collectors.toList())),
        ImmutableSet.copyOf(workspaceBuilder.addedSourceFiles),
        sourceVersion);
  }

  private ImmutableMap<LibraryKey, BlazeJarLibrary> buildLibraries(
      WorkspaceBuilder workspaceBuilder,
      RuleMap ruleMap,
      List<RuleIdeInfo> libraryRules,
      List<RuleIdeInfo> protoLibraries) {
    // Build library maps
    Multimap<RuleKey, BlazeJarLibrary> ruleKeyToLibrary = ArrayListMultimap.create();
    Map<String, BlazeJarLibrary> jdepsPathToLibrary = Maps.newHashMap();

    // Add any output jars from source rules
    for (RuleKey key : workspaceBuilder.outputJarsFromSourceRules.keySet()) {
      Collection<BlazeJarLibrary> jars = workspaceBuilder.outputJarsFromSourceRules.get(key);
      ruleKeyToLibrary.putAll(key, jars);
      for (BlazeJarLibrary library : jars) {
        addLibraryToJdeps(jdepsPathToLibrary, library);
      }
    }

    for (RuleIdeInfo rule : libraryRules) {
      JavaRuleIdeInfo javaRuleIdeInfo = rule.javaRuleIdeInfo;
      if (javaRuleIdeInfo == null) {
        continue;
      }
      List<LibraryArtifact> allJars = Lists.newArrayList();
      allJars.addAll(javaRuleIdeInfo.jars);
      Collection<BlazeJarLibrary> libraries =
          allJars
              .stream()
              .map(library -> new BlazeJarLibrary(library, rule.key))
              .collect(Collectors.toList());

      ruleKeyToLibrary.putAll(rule.key, libraries);
      for (BlazeJarLibrary library : libraries) {
        addLibraryToJdeps(jdepsPathToLibrary, library);
      }
    }

    // proto legacy jdeps support
    for (RuleIdeInfo rule : protoLibraries) {
      ProtoLibraryLegacyInfo protoLibraryLegacyInfo = rule.protoLibraryLegacyInfo;
      if (protoLibraryLegacyInfo == null) {
        continue;
      }
      for (LibraryArtifact libraryArtifact :
          Iterables.concat(
              protoLibraryLegacyInfo.jarsV1,
              protoLibraryLegacyInfo.jarsMutable,
              protoLibraryLegacyInfo.jarsImmutable)) {
        addLibraryToJdeps(jdepsPathToLibrary, new BlazeJarLibrary(libraryArtifact, rule.key));
      }
    }

    Map<LibraryKey, BlazeJarLibrary> result = Maps.newHashMap();

    // Collect jars from jdep references
    for (String jdepsPath : workspaceBuilder.jdeps) {
      BlazeJarLibrary library = jdepsPathToLibrary.get(jdepsPath);
      if (library != null) {
        result.put(library.key, library);
      }
    }

    // Collect jars referenced by direct deps from your working set
    for (RuleKey deps : workspaceBuilder.directDeps) {
      for (BlazeJarLibrary library : ruleKeyToLibrary.get(deps)) {
        result.put(library.key, library);
      }
    }

    // Collect legacy proto libraries from direct deps
    addProtoLegacyLibrariesFromDirectDeps(workspaceBuilder, ruleMap, result);

    // Collect generated jars from source rules
    for (BlazeJarLibrary library : workspaceBuilder.generatedJarsFromSourceRules) {
      result.put(library.key, library);
    }

    return ImmutableMap.copyOf(result);
  }

  private void addProtoLegacyLibrariesFromDirectDeps(
      WorkspaceBuilder workspaceBuilder, RuleMap ruleMap, Map<LibraryKey, BlazeJarLibrary> result) {
    List<RuleKey> version1Roots = Lists.newArrayList();
    List<RuleKey> immutableRoots = Lists.newArrayList();
    List<RuleKey> mutableRoots = Lists.newArrayList();
    for (RuleKey ruleKey : workspaceBuilder.directDeps) {
      RuleIdeInfo rule = ruleMap.get(ruleKey);
      if (rule == null) {
        continue;
      }
      ProtoLibraryLegacyInfo protoLibraryLegacyInfo = rule.protoLibraryLegacyInfo;
      if (protoLibraryLegacyInfo == null) {
        continue;
      }
      switch (protoLibraryLegacyInfo.apiFlavor) {
        case VERSION_1:
          version1Roots.add(ruleKey);
          break;
        case IMMUTABLE:
          immutableRoots.add(ruleKey);
          break;
        case MUTABLE:
          mutableRoots.add(ruleKey);
          break;
        case BOTH:
          mutableRoots.add(ruleKey);
          immutableRoots.add(ruleKey);
          break;
        default:
          // Can't happen
          break;
      }
    }

    addProtoLegacyLibrariesFromDirectDepsForFlavor(
        ruleMap, ProtoLibraryLegacyInfo.ApiFlavor.VERSION_1, version1Roots, result);
    addProtoLegacyLibrariesFromDirectDepsForFlavor(
        ruleMap, ProtoLibraryLegacyInfo.ApiFlavor.IMMUTABLE, immutableRoots, result);
    addProtoLegacyLibrariesFromDirectDepsForFlavor(
        ruleMap, ProtoLibraryLegacyInfo.ApiFlavor.MUTABLE, mutableRoots, result);
  }

  private void addProtoLegacyLibrariesFromDirectDepsForFlavor(
      RuleMap ruleMap,
      ProtoLibraryLegacyInfo.ApiFlavor apiFlavor,
      List<RuleKey> roots,
      Map<LibraryKey, BlazeJarLibrary> result) {
    Set<RuleKey> seen = Sets.newHashSet();
    while (!roots.isEmpty()) {
      RuleKey ruleKey = roots.remove(roots.size() - 1);
      if (!seen.add(ruleKey)) {
        continue;
      }
      RuleIdeInfo rule = ruleMap.get(ruleKey);
      if (rule == null) {
        continue;
      }
      ProtoLibraryLegacyInfo protoLibraryLegacyInfo = rule.protoLibraryLegacyInfo;
      if (protoLibraryLegacyInfo == null) {
        continue;
      }
      final Collection<LibraryArtifact> libraries;
      switch (apiFlavor) {
        case VERSION_1:
          libraries = protoLibraryLegacyInfo.jarsV1;
          break;
        case MUTABLE:
          libraries = protoLibraryLegacyInfo.jarsMutable;
          break;
        case IMMUTABLE:
          libraries = protoLibraryLegacyInfo.jarsImmutable;
          break;
        default:
          // Can't happen
          libraries = null;
          break;
      }

      if (libraries != null) {
        for (LibraryArtifact libraryArtifact : libraries) {
          BlazeJarLibrary library = new BlazeJarLibrary(libraryArtifact, ruleKey);
          result.put(library.key, library);
        }
      }

      for (Label dep : rule.dependencies) {
        roots.add(RuleKey.forDependency(rule, dep));
      }
    }
  }

  private void addLibraryToJdeps(
      Map<String, BlazeJarLibrary> jdepsPathToLibrary, BlazeJarLibrary library) {
    LibraryArtifact libraryArtifact = library.libraryArtifact;
    ArtifactLocation interfaceJar = libraryArtifact.interfaceJar;
    if (interfaceJar != null) {
      jdepsPathToLibrary.put(interfaceJar.getExecutionRootRelativePath(), library);
    }
    ArtifactLocation classJar = libraryArtifact.classJar;
    if (classJar != null) {
      jdepsPathToLibrary.put(classJar.getExecutionRootRelativePath(), library);
    }
  }

  private void addRuleAsSource(
      WorkspaceBuilder workspaceBuilder,
      RuleIdeInfo rule,
      Collection<ArtifactLocation> javaSources) {
    JavaRuleIdeInfo javaRuleIdeInfo = rule.javaRuleIdeInfo;
    if (javaRuleIdeInfo == null) {
      return;
    }

    RuleKey ruleKey = rule.key;
    Collection<String> jars = jdepsMap.getDependenciesForRule(ruleKey);
    if (jars != null) {
      workspaceBuilder.jdeps.addAll(jars);
    }

    // Add all deps if this rule is in the current working set
    if (workingSet == null || workingSet.isRuleInWorkingSet(rule)) {
      // Add self, so we pick up our own gen jars if in working set
      workspaceBuilder.directDeps.add(ruleKey);
      for (Label dep : rule.dependencies) {
        workspaceBuilder.directDeps.add(RuleKey.forDependency(rule, dep));
      }
    }

    for (ArtifactLocation artifactLocation : javaSources) {
      if (artifactLocation.isSource()) {
        duplicateSourceDetector.add(ruleKey, artifactLocation);
        workspaceBuilder.sourceArtifacts.add(new SourceArtifact(ruleKey, artifactLocation));
        workspaceBuilder.addedSourceFiles.add(artifactLocation);
      }
    }

    ArtifactLocation manifest = javaRuleIdeInfo.packageManifest;
    if (manifest != null) {
      workspaceBuilder.javaPackageManifests.put(ruleKey, manifest);
    }
    for (LibraryArtifact libraryArtifact : javaRuleIdeInfo.jars) {
      ArtifactLocation classJar = libraryArtifact.classJar;
      if (classJar != null) {
        workspaceBuilder.buildOutputJars.add(classJar);
      }
    }
    workspaceBuilder.generatedJarsFromSourceRules.addAll(
        javaRuleIdeInfo
            .generatedJars
            .stream()
            .map(libraryArtifact -> new BlazeJarLibrary(libraryArtifact, ruleKey))
            .collect(Collectors.toList()));
    if (javaRuleIdeInfo.filteredGenJar != null) {
      workspaceBuilder.generatedJarsFromSourceRules.add(
          new BlazeJarLibrary(javaRuleIdeInfo.filteredGenJar, ruleKey));
    }

    for (BlazeJavaSyncAugmenter augmenter : augmenters) {
      augmenter.addJarsForSourceRule(
          rule,
          workspaceBuilder.outputJarsFromSourceRules.get(ruleKey),
          workspaceBuilder.generatedJarsFromSourceRules);
    }
  }

  @Nullable
  private String findSourceVersion(RuleMap ruleMap) {
    for (RuleIdeInfo rule : ruleMap.rules()) {
      if (rule.javaToolchainIdeInfo != null) {
        return rule.javaToolchainIdeInfo.sourceVersion;
      }
    }
    return null;
  }

  private static class WorkspaceBuilder {
    Set<String> jdeps = Sets.newHashSet();
    Set<RuleKey> directDeps = Sets.newHashSet();
    Set<ArtifactLocation> addedSourceFiles = Sets.newHashSet();
    Multimap<RuleKey, BlazeJarLibrary> outputJarsFromSourceRules = ArrayListMultimap.create();
    List<BlazeJarLibrary> generatedJarsFromSourceRules = Lists.newArrayList();
    List<ArtifactLocation> buildOutputJars = Lists.newArrayList();
    List<SourceArtifact> sourceArtifacts = Lists.newArrayList();
    Map<RuleKey, ArtifactLocation> javaPackageManifests = Maps.newHashMap();
  }
}

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
import com.google.idea.blaze.base.model.RuleMap;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.ProjectViewRuleImportFilter;
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
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Builds a BlazeWorkspace. */
public final class BlazeJavaWorkspaceImporter {
  private static final Logger LOG = Logger.getInstance(BlazeJavaWorkspaceImporter.class);

  private static final BoolExperiment NO_EMPTY_SOURCE_RULES =
      new BoolExperiment("no.empty.source.rules", true);

  private final Project project;
  private final BlazeContext context;
  private final WorkspaceRoot workspaceRoot;
  private final ImportRoots importRoots;
  private final RuleMap ruleMap;
  private final SourceTestConfig sourceTestConfig;
  private final JdepsMap jdepsMap;
  @Nullable private final JavaWorkingSet workingSet;
  private final ArtifactLocationDecoder artifactLocationDecoder;
  private final ProjectViewRuleImportFilter importFilter;
  private final DuplicateSourceDetector duplicateSourceDetector = new DuplicateSourceDetector();
  private final Collection<BlazeJavaSyncAugmenter> augmenters;

  public BlazeJavaWorkspaceImporter(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      RuleMap ruleMap,
      JdepsMap jdepsMap,
      @Nullable JavaWorkingSet workingSet,
      ArtifactLocationDecoder artifactLocationDecoder) {
    this.project = project;
    this.context = context;
    this.workspaceRoot = workspaceRoot;
    this.importRoots =
        ImportRoots.builder(workspaceRoot, Blaze.getBuildSystem(project))
            .add(projectViewSet)
            .build();
    this.ruleMap = ruleMap;
    this.jdepsMap = jdepsMap;
    this.workingSet = workingSet;
    this.artifactLocationDecoder = artifactLocationDecoder;
    this.importFilter = new ProjectViewRuleImportFilter(project, workspaceRoot, projectViewSet);
    this.sourceTestConfig = new SourceTestConfig(projectViewSet);
    this.augmenters = BlazeJavaSyncAugmenter.getActiveSyncAgumenters(workspaceLanguageSettings);
  }

  public BlazeJavaImportResult importWorkspace(BlazeContext context) {
    List<RuleIdeInfo> includedRules =
        ruleMap
            .rules()
            .stream()
            .filter(rule -> !importFilter.excludeTarget(rule))
            .collect(Collectors.toList());

    List<RuleIdeInfo> javaRules =
        includedRules
            .stream()
            .filter(rule -> rule.javaRuleIdeInfo != null)
            .collect(Collectors.toList());

    Map<Label, Collection<ArtifactLocation>> ruleToJavaSources = Maps.newHashMap();
    for (RuleIdeInfo rule : javaRules) {
      List<ArtifactLocation> javaSources =
          rule.sources
              .stream()
              .filter(source -> source.getRelativePath().endsWith(".java"))
              .collect(Collectors.toList());
      ruleToJavaSources.put(rule.label, javaSources);
    }

    boolean noEmptySourceRules = NO_EMPTY_SOURCE_RULES.getValue();
    List<RuleIdeInfo> sourceRules = Lists.newArrayList();
    List<RuleIdeInfo> libraryRules = Lists.newArrayList();
    for (RuleIdeInfo rule : javaRules) {
      boolean importAsSource =
          importFilter.isSourceRule(rule)
              && canImportAsSource(rule)
              && (noEmptySourceRules
                  ? anyNonGeneratedSources(ruleToJavaSources.get(rule.label))
                  : !allSourcesGenerated(ruleToJavaSources.get(rule.label)));

      if (importAsSource) {
        sourceRules.add(rule);
      } else {
        libraryRules.add(rule);
      }
    }

    List<RuleIdeInfo> protoLibraries =
        includedRules
            .stream()
            .filter(rule -> rule.kind == Kind.PROTO_LIBRARY)
            .collect(Collectors.toList());

    WorkspaceBuilder workspaceBuilder = new WorkspaceBuilder();
    for (RuleIdeInfo rule : sourceRules) {
      addRuleAsSource(workspaceBuilder, rule, ruleToJavaSources.get(rule.label));
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
        buildLibraries(workspaceBuilder, ruleMap, libraryRules, protoLibraries);

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

  private boolean canImportAsSource(RuleIdeInfo rule) {
    return !rule.kindIsOneOf(Kind.JAVA_WRAP_CC, Kind.JAVA_IMPORT);
  }

  private boolean allSourcesGenerated(Collection<ArtifactLocation> sources) {
    return !sources.isEmpty() && sources.stream().allMatch(ArtifactLocation::isGenerated);
  }

  private boolean anyNonGeneratedSources(Collection<ArtifactLocation> sources) {
    return sources.stream().anyMatch(ArtifactLocation::isSource);
  }

  private ImmutableMap<LibraryKey, BlazeJarLibrary> buildLibraries(
      WorkspaceBuilder workspaceBuilder,
      RuleMap ruleMap,
      List<RuleIdeInfo> libraryRules,
      List<RuleIdeInfo> protoLibraries) {
    // Build library maps
    Multimap<Label, BlazeJarLibrary> labelToLibrary = ArrayListMultimap.create();
    Map<String, BlazeJarLibrary> jdepsPathToLibrary = Maps.newHashMap();

    // Add any output jars from source rules
    for (Label label : workspaceBuilder.outputJarsFromSourceRules.keySet()) {
      Collection<BlazeJarLibrary> jars = workspaceBuilder.outputJarsFromSourceRules.get(label);
      labelToLibrary.putAll(label, jars);
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
              .map(library -> new BlazeJarLibrary(library, rule.label))
              .collect(Collectors.toList());

      labelToLibrary.putAll(rule.label, libraries);
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
        addLibraryToJdeps(jdepsPathToLibrary, new BlazeJarLibrary(libraryArtifact, rule.label));
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
    for (Label deps : workspaceBuilder.directDeps) {
      for (BlazeJarLibrary library : labelToLibrary.get(deps)) {
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
    List<Label> version1Roots = Lists.newArrayList();
    List<Label> immutableRoots = Lists.newArrayList();
    List<Label> mutableRoots = Lists.newArrayList();
    for (Label label : workspaceBuilder.directDeps) {
      RuleIdeInfo rule = ruleMap.get(label);
      if (rule == null) {
        continue;
      }
      ProtoLibraryLegacyInfo protoLibraryLegacyInfo = rule.protoLibraryLegacyInfo;
      if (protoLibraryLegacyInfo == null) {
        continue;
      }
      switch (protoLibraryLegacyInfo.apiFlavor) {
        case VERSION_1:
          version1Roots.add(label);
          break;
        case IMMUTABLE:
          immutableRoots.add(label);
          break;
        case MUTABLE:
          mutableRoots.add(label);
          break;
        case BOTH:
          mutableRoots.add(label);
          immutableRoots.add(label);
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
      List<Label> roots,
      Map<LibraryKey, BlazeJarLibrary> result) {
    Set<Label> seen = Sets.newHashSet();
    while (!roots.isEmpty()) {
      Label label = roots.remove(roots.size() - 1);
      if (!seen.add(label)) {
        continue;
      }
      RuleIdeInfo rule = ruleMap.get(label);
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
          BlazeJarLibrary library = new BlazeJarLibrary(libraryArtifact, label);
          result.put(library.key, library);
        }
      }

      roots.addAll(rule.dependencies);
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

    Label label = rule.label;
    Collection<String> jars = jdepsMap.getDependenciesForRule(label);
    if (jars != null) {
      workspaceBuilder.jdeps.addAll(jars);
    }

    // Add all deps if this rule is in the current working set
    if (workingSet == null || workingSet.isRuleInWorkingSet(rule)) {
      workspaceBuilder.directDeps.add(
          label); // Add self, so we pick up our own gen jars if in working set
      workspaceBuilder.directDeps.addAll(rule.dependencies);
    }

    for (ArtifactLocation artifactLocation : javaSources) {
      if (artifactLocation.isSource()) {
        duplicateSourceDetector.add(label, artifactLocation);
        workspaceBuilder.sourceArtifacts.add(new SourceArtifact(label, artifactLocation));
        workspaceBuilder.addedSourceFiles.add(artifactLocation.getFile());
      }
    }

    ArtifactLocation manifest = javaRuleIdeInfo.packageManifest;
    if (manifest != null) {
      workspaceBuilder.javaPackageManifests.put(label, manifest);
    }
    for (LibraryArtifact libraryArtifact : javaRuleIdeInfo.jars) {
      ArtifactLocation classJar = libraryArtifact.classJar;
      if (classJar != null) {
        workspaceBuilder.buildOutputJars.add(classJar.getFile());
      }
    }
    workspaceBuilder.generatedJarsFromSourceRules.addAll(
        javaRuleIdeInfo
            .generatedJars
            .stream()
            .map(libraryArtifact -> new BlazeJarLibrary(libraryArtifact, label))
            .collect(Collectors.toList()));
    if (javaRuleIdeInfo.filteredGenJar != null) {
      workspaceBuilder.generatedJarsFromSourceRules.add(
          new BlazeJarLibrary(javaRuleIdeInfo.filteredGenJar, label));
    }

    for (BlazeJavaSyncAugmenter augmenter : augmenters) {
      augmenter.addJarsForSourceRule(
          rule,
          workspaceBuilder.outputJarsFromSourceRules.get(label),
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

  static class WorkspaceBuilder {
    Set<String> jdeps = Sets.newHashSet();
    Set<Label> directDeps = Sets.newHashSet();
    Set<File> addedSourceFiles = Sets.newHashSet();
    Multimap<Label, BlazeJarLibrary> outputJarsFromSourceRules = ArrayListMultimap.create();
    List<BlazeJarLibrary> generatedJarsFromSourceRules = Lists.newArrayList();
    List<File> buildOutputJars = Lists.newArrayList();
    List<SourceArtifact> sourceArtifacts = Lists.newArrayList();
    Map<Label, ArtifactLocation> javaPackageManifests = Maps.newHashMap();
  }
}

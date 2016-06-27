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

import com.google.common.collect.*;
import com.google.idea.blaze.base.ideinfo.*;
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
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.DuplicateSourceDetector;
import com.google.idea.blaze.java.sync.jdeps.JdepsMap;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.model.BlazeLibrary;
import com.google.idea.blaze.java.sync.model.LibraryKey;
import com.google.idea.blaze.java.sync.source.SourceArtifact;
import com.google.idea.blaze.java.sync.source.SourceDirectoryCalculator;
import com.google.idea.blaze.java.sync.workingset.JavaWorkingSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a BlazeWorkspace.
 */
public final class BlazeJavaWorkspaceImporter {
  private static final Logger LOG = Logger.getInstance(BlazeJavaWorkspaceImporter.class);

  private final Project project;
  private final WorkspaceRoot workspaceRoot;
  private final ImportRoots importRoots;
  private final ImmutableMap<Label, RuleIdeInfo> ruleMap;
  private final SourceTestConfig sourceTestConfig;
  private final JdepsMap jdepsMap;
  @Nullable private final JavaWorkingSet workingSet;
  private final ArtifactLocationDecoder artifactLocationDecoder;
  private final ProjectViewRuleImportFilter importFilter;
  private final DuplicateSourceDetector duplicateSourceDetector = new DuplicateSourceDetector();

  public BlazeJavaWorkspaceImporter(
    Project project,
    WorkspaceRoot workspaceRoot,
    ProjectViewSet projectViewSet,
    ImmutableMap<Label, RuleIdeInfo> ruleMap,
    JdepsMap jdepsMap,
    @Nullable JavaWorkingSet workingSet,
    ArtifactLocationDecoder artifactLocationDecoder) {
    this.project = project;
    this.workspaceRoot = workspaceRoot;
    this.importRoots = ImportRoots.builder(workspaceRoot, Blaze.getBuildSystem(project))
      .add(projectViewSet)
      .build();
    this.ruleMap = ruleMap;
    this.jdepsMap = jdepsMap;
    this.workingSet = workingSet;
    this.artifactLocationDecoder = artifactLocationDecoder;
    this.importFilter = new ProjectViewRuleImportFilter(project, workspaceRoot, projectViewSet);
    this.sourceTestConfig = new SourceTestConfig(projectViewSet);
  }

  public BlazeJavaImportResult importWorkspace(BlazeContext context) {
    List<RuleIdeInfo> includedRules = ruleMap.values().stream()
      .filter(rule -> !importFilter.excludeTarget(rule))
      .collect(Collectors.toList());

    List<RuleIdeInfo> javaRules = includedRules.stream()
      .filter(rule -> rule.javaRuleIdeInfo != null)
      .collect(Collectors.toList());

    List<RuleIdeInfo> sourceRules = Lists.newArrayList();
    List<RuleIdeInfo> libraryRules = Lists.newArrayList();
    for (RuleIdeInfo rule : javaRules) {
      boolean importAsSource =
        importFilter.isSourceRule(rule)
        && canImportAsSource(rule)
        && !allSourcesGenerated(rule);

      if (importAsSource) {
        sourceRules.add(rule);
      } else {
        libraryRules.add(rule);
      }
    }

    List<RuleIdeInfo> protoLibraries = includedRules.stream()
      .filter(rule -> rule.kind == Kind.PROTO_LIBRARY)
      .collect(Collectors.toList());

    WorkspaceBuilder workspaceBuilder = new WorkspaceBuilder();
    for (RuleIdeInfo rule : sourceRules) {
      addRuleAsSource(workspaceBuilder, rule);
    }

    SourceDirectoryCalculator sourceDirectoryCalculator = new SourceDirectoryCalculator();
    ImmutableList<BlazeContentEntry> contentEntries = sourceDirectoryCalculator.calculateContentEntries(
      context,
      workspaceRoot,
      sourceTestConfig,
      artifactLocationDecoder,
      importRoots.rootDirectories(),
      workspaceBuilder.sourceArtifacts,
      workspaceBuilder.javaPackageManifests
    );

    int totalContentEntryCount = 0;
    for (BlazeContentEntry contentEntry : contentEntries) {
      totalContentEntryCount += contentEntry.sources.size();
    }
    context.output(PrintOutput.output("Java content entry count: " + totalContentEntryCount));

    ImmutableMap<LibraryKey, BlazeLibrary> libraries = buildLibraries(workspaceBuilder, ruleMap, libraryRules, protoLibraries);

    duplicateSourceDetector.reportDuplicates(context);

    String sourceVersion = findSourceVersion(ruleMap);

    return new BlazeJavaImportResult(
      contentEntries,
      libraries,
      ImmutableList.copyOf(workspaceBuilder.buildOutputJars
                             .stream()
                             .sorted()
                             .collect(Collectors.toList())),
      ImmutableSet.copyOf(workspaceBuilder.addedSourceFiles),
      sourceVersion
    );
  }

  private boolean canImportAsSource(RuleIdeInfo rule) {
    return !rule.kindIsOneOf(Kind.JAVA_WRAP_CC, Kind.JAVA_IMPORT);
  }

  private boolean allSourcesGenerated(RuleIdeInfo rule) {
    return !rule.sources.isEmpty() && rule.sources.stream().allMatch(ArtifactLocation::isGenerated);
  }

  private ImmutableMap<LibraryKey, BlazeLibrary> buildLibraries(WorkspaceBuilder workspaceBuilder,
                                                                Map<Label, RuleIdeInfo> ruleMap,
                                                                List<RuleIdeInfo> libraryRules,
                                                                List<RuleIdeInfo> protoLibraries) {
    // Build library maps
    Multimap<Label, LibraryArtifact> labelToLibrary = ArrayListMultimap.create();
    Map<String, LibraryArtifact> jdepsPathToLibrary = Maps.newHashMap();
    for (RuleIdeInfo rule : libraryRules) {
      JavaRuleIdeInfo javaRuleIdeInfo = rule.javaRuleIdeInfo;
      if (javaRuleIdeInfo == null) {
        continue;
      }
      Iterable<LibraryArtifact> libraries = Iterables.concat(javaRuleIdeInfo.jars, javaRuleIdeInfo.generatedJars);
      labelToLibrary.putAll(rule.label, libraries);
      for (LibraryArtifact libraryArtifact : libraries) {
        addLibraryToJdeps(jdepsPathToLibrary, libraryArtifact);
      }
    }

    // proto legacy jdeps support
    for (RuleIdeInfo rule : protoLibraries) {
      ProtoLibraryLegacyInfo protoLibraryLegacyInfo = rule.protoLibraryLegacyInfo;
      if (protoLibraryLegacyInfo == null) {
        continue;
      }
      for (LibraryArtifact libraryArtifact : Iterables.concat(protoLibraryLegacyInfo.jarsV1,
                                                              protoLibraryLegacyInfo.jarsMutable,
                                                              protoLibraryLegacyInfo.jarsImmutable)) {
        addLibraryToJdeps(jdepsPathToLibrary, libraryArtifact);
      }
    }

    // Collect jars from jdep references
    Set<LibraryArtifact> libraries = Sets.newHashSet();
    for (String jdepsPath : workspaceBuilder.jdeps) {
      LibraryArtifact libraryArtifact = jdepsPathToLibrary.get(jdepsPath);
      if (libraryArtifact != null) {
        libraries.add(libraryArtifact);
      }
    }

    // Collect jars referenced by direct deps from your working set
    for (Label deps : workspaceBuilder.directDeps) {
      libraries.addAll(labelToLibrary.get(deps));
    }

    // Collect legacy proto libraries from direct deps
    addProtoLegacyLibrariesFromDirectDeps(workspaceBuilder, ruleMap, libraries);

    // Collect generated jars from source rules
    libraries.addAll(workspaceBuilder.generatedJars);

    ImmutableMap.Builder<LibraryKey, BlazeLibrary> result = ImmutableMap.builder();
    for (LibraryArtifact libraryArtifact : libraries) {
      File jar = libraryArtifact.jar.getFile();
      LibraryKey key = LibraryKey.fromJarFile(jar);
      BlazeLibrary blazeLibrary = new BlazeLibrary(key, libraryArtifact);
      result.put(key, blazeLibrary);
    }
    return result.build();
  }

  private void addProtoLegacyLibrariesFromDirectDeps(WorkspaceBuilder workspaceBuilder,
                                                     Map<Label, RuleIdeInfo> ruleMap,
                                                     Set<LibraryArtifact> result) {
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

    addProtoLegacyLibrariesFromDirectDepsForFlavor(ruleMap, ProtoLibraryLegacyInfo.ApiFlavor.VERSION_1, version1Roots, result);
    addProtoLegacyLibrariesFromDirectDepsForFlavor(ruleMap, ProtoLibraryLegacyInfo.ApiFlavor.IMMUTABLE, immutableRoots, result);
    addProtoLegacyLibrariesFromDirectDepsForFlavor(ruleMap, ProtoLibraryLegacyInfo.ApiFlavor.MUTABLE, mutableRoots, result);
  }

  private void addProtoLegacyLibrariesFromDirectDepsForFlavor(Map<Label, RuleIdeInfo> ruleMap,
                                                              ProtoLibraryLegacyInfo.ApiFlavor apiFlavor,
                                                              List<Label> roots,
                                                              Set<LibraryArtifact> result) {
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
      switch (apiFlavor) {
        case VERSION_1:
          result.addAll(protoLibraryLegacyInfo.jarsV1);
          break;
        case MUTABLE:
          result.addAll(protoLibraryLegacyInfo.jarsMutable);
          break;
        case IMMUTABLE:
          result.addAll(protoLibraryLegacyInfo.jarsImmutable);
          break;
        default:
          // Can't happen
          break;
      }

      roots.addAll(rule.dependencies);
    }
  }

  private void addLibraryToJdeps(Map<String, LibraryArtifact> jdepsPathToLibrary, LibraryArtifact libraryArtifact) {
    ArtifactLocation jar = libraryArtifact.jar;
    jdepsPathToLibrary.put(jar.getExecutionRootRelativePath(), libraryArtifact);
    ArtifactLocation runtimeJar = libraryArtifact.runtimeJar;
    if (runtimeJar != null) {
      jdepsPathToLibrary.put(runtimeJar.getExecutionRootRelativePath(), libraryArtifact);
    }
  }

  private void addRuleAsSource(
    WorkspaceBuilder workspaceBuilder,
    RuleIdeInfo rule) {
    JavaRuleIdeInfo javaRuleIdeInfo = rule.javaRuleIdeInfo;
    if (javaRuleIdeInfo == null) {
      return;
    }

    Collection<String> jars = jdepsMap.getDependenciesForRule(rule.label);
    if (jars != null) {
      workspaceBuilder.jdeps.addAll(jars);
    }

    // Add all deps if this rule is in the current working set
    if (workingSet == null || workingSet.isRuleInWorkingSet(rule)) {
      workspaceBuilder.directDeps.addAll(rule.dependencies);
    }

    for (ArtifactLocation artifactLocation : rule.sources) {
      if (!artifactLocation.isGenerated()) {
        duplicateSourceDetector.add(rule.label, artifactLocation);
        workspaceBuilder.sourceArtifacts.add(new SourceArtifact(rule.label, artifactLocation));
        workspaceBuilder.addedSourceFiles.add(artifactLocation.getFile());
      }
    }

    ArtifactLocation manifest = javaRuleIdeInfo.packageManifest;
    if (manifest != null) {
      workspaceBuilder.javaPackageManifests.put(rule.label, manifest);
    }
    for (LibraryArtifact libraryArtifact : javaRuleIdeInfo.jars) {
      ArtifactLocation runtimeJar = libraryArtifact.runtimeJar;
      if (runtimeJar != null) {
        workspaceBuilder.buildOutputJars.add(runtimeJar.getFile());
      }
    }
    workspaceBuilder.generatedJars.addAll(javaRuleIdeInfo.generatedJars);
  }

  @Nullable
  private String findSourceVersion(ImmutableMap<Label, RuleIdeInfo> ruleMap) {
    for (RuleIdeInfo rule : ruleMap.values()) {
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
    List<LibraryArtifact> generatedJars = Lists.newArrayList();
    List<File> buildOutputJars = Lists.newArrayList();
    List<SourceArtifact> sourceArtifacts = Lists.newArrayList();
    Map<Label, ArtifactLocation> javaPackageManifests = Maps.newHashMap();
  }
}

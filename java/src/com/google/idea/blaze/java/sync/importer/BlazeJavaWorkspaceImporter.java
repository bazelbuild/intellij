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
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.Dependency.DependencyType;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.ProtoLibraryLegacyInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.DuplicateSourceDetector;
import com.google.idea.blaze.java.sync.jdeps.JdepsMap;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.source.SourceArtifact;
import com.google.idea.blaze.java.sync.source.SourceDirectoryCalculator;
import com.google.idea.blaze.java.sync.workingset.JavaWorkingSet;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
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
  private final TargetMap targetMap;
  private final JdepsMap jdepsMap;
  @Nullable private final JavaWorkingSet workingSet;
  private final ArtifactLocationDecoder artifactLocationDecoder;
  private final DuplicateSourceDetector duplicateSourceDetector = new DuplicateSourceDetector();
  private final JavaSourceFilter sourceFilter;
  private final WorkspaceLanguageSettings workspaceLanguageSettings;
  private final List<BlazeJavaSyncAugmenter> augmenters;
  private final ProjectViewSet projectViewSet;

  public BlazeJavaWorkspaceImporter(
      Project project,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      TargetMap targetMap,
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
    this.targetMap = targetMap;
    this.sourceFilter = sourceFilter;
    this.jdepsMap = jdepsMap;
    this.workingSet = workingSet;
    this.artifactLocationDecoder = artifactLocationDecoder;
    this.workspaceLanguageSettings = workspaceLanguageSettings;
    this.augmenters = Arrays.asList(BlazeJavaSyncAugmenter.EP_NAME.getExtensions());
    this.projectViewSet = projectViewSet;
  }

  public BlazeJavaImportResult importWorkspace(BlazeContext context) {
    WorkspaceBuilder workspaceBuilder = new WorkspaceBuilder();
    for (TargetIdeInfo target : sourceFilter.sourceTargets) {
      addTargetAsSource(workspaceBuilder, target, sourceFilter.targetToJavaSources.get(target.key));
    }

    SourceDirectoryCalculator sourceDirectoryCalculator = new SourceDirectoryCalculator();
    ImmutableList<BlazeContentEntry> contentEntries =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            artifactLocationDecoder,
            importRoots,
            workspaceBuilder.sourceArtifacts,
            workspaceBuilder.javaPackageManifests);

    int totalContentEntryCount = 0;
    for (BlazeContentEntry contentEntry : contentEntries) {
      totalContentEntryCount += contentEntry.sources.size();
    }
    context.output(PrintOutput.log("Java content entry count: " + totalContentEntryCount));

    ImmutableMap<LibraryKey, BlazeJarLibrary> libraries =
        buildLibraries(
            workspaceBuilder, targetMap, sourceFilter.libraryTargets, sourceFilter.protoLibraries);

    duplicateSourceDetector.reportDuplicates(context);

    String sourceVersion = findSourceVersion(targetMap);

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
      TargetMap targetMap,
      List<TargetIdeInfo> libraryTargets,
      List<TargetIdeInfo> protoLibraries) {
    // Build library maps
    Multimap<TargetKey, BlazeJarLibrary> targetKeyToLibrary = ArrayListMultimap.create();
    Map<String, BlazeJarLibrary> jdepsPathToLibrary = Maps.newHashMap();

    // Add any output jars from source rules
    for (TargetKey key : workspaceBuilder.outputJarsFromSourceTargets.keySet()) {
      Collection<BlazeJarLibrary> jars = workspaceBuilder.outputJarsFromSourceTargets.get(key);
      targetKeyToLibrary.putAll(key, jars);
      for (BlazeJarLibrary library : jars) {
        addLibraryToJdeps(jdepsPathToLibrary, library);
      }
    }

    for (TargetIdeInfo target : libraryTargets) {
      JavaIdeInfo javaIdeInfo = target.javaIdeInfo;
      if (javaIdeInfo == null) {
        continue;
      }
      List<LibraryArtifact> allJars = Lists.newArrayList();
      allJars.addAll(javaIdeInfo.jars);
      Collection<BlazeJarLibrary> libraries =
          allJars.stream().map(BlazeJarLibrary::new).collect(Collectors.toList());

      targetKeyToLibrary.putAll(target.key, libraries);
      for (BlazeJarLibrary library : libraries) {
        addLibraryToJdeps(jdepsPathToLibrary, library);
      }
    }

    // proto legacy jdeps support
    for (TargetIdeInfo target : protoLibraries) {
      ProtoLibraryLegacyInfo protoLibraryLegacyInfo = target.protoLibraryLegacyInfo;
      if (protoLibraryLegacyInfo == null) {
        continue;
      }
      for (LibraryArtifact libraryArtifact :
          Iterables.concat(
              protoLibraryLegacyInfo.jarsV1,
              protoLibraryLegacyInfo.jarsMutable,
              protoLibraryLegacyInfo.jarsImmutable)) {
        addLibraryToJdeps(jdepsPathToLibrary, new BlazeJarLibrary(libraryArtifact));
      }
    }

    Map<LibraryKey, BlazeJarLibrary> result = Maps.newHashMap();

    // Collect jars from jdep references
    for (String jdepsPath : workspaceBuilder.jdeps) {
      if (sourceFilter.jdepsPathsForExcludedJars.contains(jdepsPath)) {
        continue;
      }
      BlazeJarLibrary library = jdepsPathToLibrary.get(jdepsPath);
      if (library == null) {
        // It's in the target's jdeps, but our aspect never attached to the target building it
        // Perhaps it's an implicit dependency, or not referenced in an attribute we propagate along
        // Make a best-effort attempt to add it to the project anyway
        ArtifactLocation location =
            ArtifactLocation.builder()
                .setIsSource(false)
                .setRootExecutionPathFragment(jdepsPath)
                .setRelativePath("")
                .build();
        library = new BlazeJarLibrary(new LibraryArtifact(location, null, ImmutableList.of()));
      }
      result.put(library.key, library);
    }

    // Collect jars referenced by direct deps from your working set
    for (TargetKey deps : workspaceBuilder.directDeps) {
      for (BlazeJarLibrary library : targetKeyToLibrary.get(deps)) {
        result.put(library.key, library);
      }
    }

    // Collect legacy proto libraries from direct deps
    addProtoLegacyLibrariesFromDirectDeps(workspaceBuilder, targetMap, result);

    // Collect generated jars from source rules
    for (BlazeJarLibrary library : workspaceBuilder.generatedJarsFromSourceTargets) {
      result.put(library.key, library);
    }

    return ImmutableMap.copyOf(result);
  }

  private void addProtoLegacyLibrariesFromDirectDeps(
      WorkspaceBuilder workspaceBuilder,
      TargetMap targetMap,
      Map<LibraryKey, BlazeJarLibrary> result) {
    List<TargetKey> version1Targets = Lists.newArrayList();
    List<TargetKey> immutableTargets = Lists.newArrayList();
    List<TargetKey> mutableTargets = Lists.newArrayList();
    for (TargetKey targetKey : workspaceBuilder.directDeps) {
      TargetIdeInfo target = targetMap.get(targetKey);
      if (target == null) {
        continue;
      }
      ProtoLibraryLegacyInfo protoLibraryLegacyInfo = target.protoLibraryLegacyInfo;
      if (protoLibraryLegacyInfo == null) {
        continue;
      }
      switch (protoLibraryLegacyInfo.apiFlavor) {
        case VERSION_1:
          version1Targets.add(targetKey);
          break;
        case IMMUTABLE:
          immutableTargets.add(targetKey);
          break;
        case MUTABLE:
          mutableTargets.add(targetKey);
          break;
        case BOTH:
          mutableTargets.add(targetKey);
          immutableTargets.add(targetKey);
          break;
        default:
          // Can't happen
          break;
      }
    }

    addProtoLegacyLibrariesFromDirectDepsForFlavor(
        targetMap, ProtoLibraryLegacyInfo.ApiFlavor.VERSION_1, version1Targets, result);
    addProtoLegacyLibrariesFromDirectDepsForFlavor(
        targetMap, ProtoLibraryLegacyInfo.ApiFlavor.IMMUTABLE, immutableTargets, result);
    addProtoLegacyLibrariesFromDirectDepsForFlavor(
        targetMap, ProtoLibraryLegacyInfo.ApiFlavor.MUTABLE, mutableTargets, result);
  }

  private void addProtoLegacyLibrariesFromDirectDepsForFlavor(
      TargetMap targetMap,
      ProtoLibraryLegacyInfo.ApiFlavor apiFlavor,
      List<TargetKey> targetKeys,
      Map<LibraryKey, BlazeJarLibrary> result) {
    for (TargetKey key : targetKeys) {
      TargetIdeInfo target = targetMap.get(key);
      if (target == null) {
        continue;
      }
      ProtoLibraryLegacyInfo protoLibraryLegacyInfo = target.protoLibraryLegacyInfo;
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
          BlazeJarLibrary library = new BlazeJarLibrary(libraryArtifact);
          result.put(library.key, library);
        }
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

  private void addTargetAsSource(
      WorkspaceBuilder workspaceBuilder,
      TargetIdeInfo target,
      Collection<ArtifactLocation> javaSources) {
    JavaIdeInfo javaIdeInfo = target.javaIdeInfo;
    if (javaIdeInfo == null) {
      return;
    }

    TargetKey targetKey = target.key;
    Collection<String> jars = jdepsMap.getDependenciesForTarget(targetKey);
    if (jars != null) {
      workspaceBuilder.jdeps.addAll(jars);
    }

    // Add all deps if this rule is in the current working set
    if (workingSet == null || workingSet.isTargetInWorkingSet(target)) {
      // Add self, so we pick up our own gen jars if in working set
      workspaceBuilder.directDeps.add(targetKey);
      for (Dependency dep : target.dependencies) {
        if (dep.dependencyType != DependencyType.COMPILE_TIME) {
          continue;
        }
        // forward deps from java proto_library aspect targets
        TargetIdeInfo depTarget = targetMap.get(dep.targetKey);
        if (depTarget != null && Kind.JAVA_PROTO_LIBRARY_KINDS.contains(depTarget.kind)) {
          workspaceBuilder.directDeps.addAll(
              depTarget.dependencies.stream().map(d -> d.targetKey).collect(Collectors.toList()));
        } else {
          workspaceBuilder.directDeps.add(dep.targetKey);
        }
      }
    }

    for (ArtifactLocation artifactLocation : javaSources) {
      if (artifactLocation.isSource()) {
        duplicateSourceDetector.add(targetKey, artifactLocation);
        workspaceBuilder.sourceArtifacts.add(new SourceArtifact(targetKey, artifactLocation));
        workspaceBuilder.addedSourceFiles.add(artifactLocation);
      }
    }

    ArtifactLocation manifest = javaIdeInfo.packageManifest;
    if (manifest != null) {
      workspaceBuilder.javaPackageManifests.put(targetKey, manifest);
    }
    for (LibraryArtifact libraryArtifact : javaIdeInfo.jars) {
      ArtifactLocation classJar = libraryArtifact.classJar;
      if (classJar != null) {
        workspaceBuilder.buildOutputJars.add(classJar);
      }
    }
    workspaceBuilder.generatedJarsFromSourceTargets.addAll(
        javaIdeInfo.generatedJars.stream().map(BlazeJarLibrary::new).collect(Collectors.toList()));
    if (javaIdeInfo.filteredGenJar != null) {
      workspaceBuilder.generatedJarsFromSourceTargets.add(
          new BlazeJarLibrary(javaIdeInfo.filteredGenJar));
    }

    for (BlazeJavaSyncAugmenter augmenter : augmenters) {
      augmenter.addJarsForSourceTarget(
          workspaceLanguageSettings,
          projectViewSet,
          target,
          workspaceBuilder.outputJarsFromSourceTargets.get(targetKey),
          workspaceBuilder.generatedJarsFromSourceTargets);
    }
  }

  @Nullable
  private String findSourceVersion(TargetMap targetMap) {
    for (TargetIdeInfo target : targetMap.targets()) {
      if (target.javaToolchainIdeInfo != null) {
        return target.javaToolchainIdeInfo.sourceVersion;
      }
    }
    return null;
  }

  private static class WorkspaceBuilder {
    Set<String> jdeps = Sets.newHashSet();
    Set<TargetKey> directDeps = Sets.newHashSet();
    Set<ArtifactLocation> addedSourceFiles = Sets.newHashSet();
    Multimap<TargetKey, BlazeJarLibrary> outputJarsFromSourceTargets = ArrayListMultimap.create();
    List<BlazeJarLibrary> generatedJarsFromSourceTargets = Lists.newArrayList();
    List<ArtifactLocation> buildOutputJars = Lists.newArrayList();
    List<SourceArtifact> sourceArtifacts = Lists.newArrayList();
    Map<TargetKey, ArtifactLocation> javaPackageManifests = Maps.newHashMap();
  }
}

/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

import com.android.tools.idea.projectsystem.ClassContent;
import com.android.tools.idea.projectsystem.ClassFileFinder;
import com.android.tools.idea.projectsystem.ClassFileFinderUtil;
import com.android.tools.idea.rendering.classloading.loaders.JarManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.android.targetmaps.TargetToBinaryMap;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.BlazeSyncModificationTracker;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

/**
 * A {@link ClassFileFinder} inspired by {@link RenderJarClassFileFinder}.
 * This finder uses binary target dependencies to locate the class file.
 *
 * <br><p>When a class is being loaded, this finder prioritizes binary targets and their transitive dependencies.
 * This finder is optimized for large projects in the following ways:
 * <ul>
 *   <li>It utilizes {@link ClassContentCache} for recurring lookups, which improves performance by avoiding redundant searches.</li>
 *   <li>It maintains a local cache that maps package names to their corresponding target keys, which accelerates the lookup process.</li>
 *   <li>It employs the Jaccard index as a heuristic for initial lookups to cover cache misses. This heuristic helps in identifying the most relevant targets for a given class.</li>
 * </ul>
 */
public class BinaryTargetClassFileFinder implements ClassFileFinder {
  /** Experiment to control whether class file finding from render jars should be enabled. */
  @VisibleForTesting
  static final BoolExperiment enabled =
      new BoolExperiment("aswb.lookup.binary.deps.render.jar", true);

  private static final String REGEX_WORDS = "[^a-zA-Z0-9]+";

  private static final Logger log = Logger.getInstance(BinaryTargetClassFileFinder.class);

  // create a ignored packages collection and add kotlinx.coroutines.test.internal.TestMainDispatcherFactory
  private static final Set<String> IGNORED_FQCN = Set.of("kotlinx.coroutines.test.internal.TestMainDispatcherFactory");

  private static final String INTERNAL_PACKAGE = "_layoutlib_._internal_.";

  // matches foo.bar.R or foo.bar.R$baz
  private static final Pattern RESOURCE_CLASS_NAME = Pattern.compile(".+\\.R(\\$[^.]+)?$");
  public static final int MAX_SORTED_TARGET_KEY_SIZE = 100;

  // create a map to store package names and their corresponding target keys to improve performance
  private final Map<String, TargetKey> packageToTargetKeyMap = new HashMap<>();
  private final Module module;
  private final Project project;
  private final JarManager jarManager;

  // tracks the binary targets that depend resource targets
  // will be recalculated after every sync
  private ImmutableSet<TargetKey> binaryTargets = ImmutableSet.of();

  // tracks the value of {@link BlazeSyncModificationTracker} when binaryTargets is calculated
  // binaryTargets is calculated when the value of {@link BlazeSyncModificationTracker} does not
  // equal lastSyncCount
  long lastSyncCount = -1;

  // true if the current module is the .workspace Module
  private final boolean isWorkspaceModule;

  public BinaryTargetClassFileFinder(Module module) {
    this.module = module;
    this.project = module.getProject();
    this.isWorkspaceModule = BlazeDataStorage.WORKSPACE_MODULE_NAME.equals(module.getName());
    this.jarManager = JarManager.getInstance(project);
  }

  @Nullable
  @Override
  public ClassContent findClassFile(@NotNull String fqcn) {
    if (!isEnabled()) {
      return null;
    }

    try {
      return findClassContent(fqcn);
    } catch (Error e) {
      log.warn(
          String.format(
              "Unexpected error while finding the class file for `%1$s`: %2$s",
              fqcn, Throwables.getRootCause(e).getMessage()));
      return null;
    }
  }

  @Nullable
  public ClassContent findClassContent(String fqcn) {
    if (isResourceClass(fqcn)) {
      log.warn(String.format("Skipping resource class loading: %s", fqcn));
      return null;
    }

    if(IGNORED_FQCN.contains(fqcn)) {
      log.debug(String.format("Skipping ignored class loading: %s", fqcn));
      return null;
    }

    if (Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC)) {
      //TODO: add support for QuerySync
      return null;
    }
    return findClass(fqcn);
  }

  private ClassContent findClass(String fqcn) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      log.warn("Could not find BlazeProjectData for project " + project.getName());
      return null;
    }

    ImmutableSet<TargetKey> binaryTargets = getBinaryTargets();
    if (binaryTargets.isEmpty()) {
      log.warn(
          String.format(
              "No binaries for module %s. Adding a binary target is the best practice when using Compose/Layout preview.",
              module.getName()));
    }

    // Remove internal package prefix if present
    fqcn = StringUtil.trimStart(fqcn, INTERNAL_PACKAGE);

    ClassContentCache classContentCache = ClassContentCache.getInstance(project);
    String packageName = StringUtil.getPackageName(fqcn);
    Set<TargetKey> visitedTargets = new HashSet<>();
    // 1st attempt from cache
    ClassContent classContent = classContentCache.getClassContent(fqcn);
    if (classContent != null) {
      return classContent;
    }

    // 2nd attempt from the target key that contains the package
    if(packageToTargetKeyMap.containsKey(packageName)) {
      TargetKey targetKey = packageToTargetKeyMap.get(packageName);
      classContent = getClassFromClassJar(projectData, fqcn, targetKey);
      if (classContent != null) {
        classContentCache.putEntry(fqcn, classContent);
        return classContent;
      }
    }

    // 3rd attempt from the binary target transitive dependencies
    for (TargetKey binaryTarget : binaryTargets) {
      ImmutableCollection<TargetKey> transitiveKeys = TransitiveDependencyMap.getInstance(project).getTransitiveDependencies(binaryTarget);
       classContent = getClassFromTargetKeys(projectData, fqcn, visitedTargets, transitiveKeys);
        if (classContent != null) {
          classContentCache.putEntry(fqcn, classContent);
          return classContent;
      }
    }

    // 4th attempt from non visited targets in the entire target map
    List<TargetKey> nonVisitedTargetKeys = projectData.getTargetMap().targets().stream()
            .map(TargetIdeInfo::getKey)
            .filter(key -> !visitedTargets.contains(key))
            .collect(Collectors.toList());
    List<TargetKey> sortedTargetKeys = sortTargetKeysWithPackageName(nonVisitedTargetKeys, packageName);
    for (TargetKey targetKey : sortedTargetKeys) {
      classContent = getClassFromClassJar(projectData, fqcn, targetKey);
      if (classContent != null) {
        packageToTargetKeyMap.put(packageName, targetKey);
        classContentCache.putEntry(fqcn, classContent);
        return classContent;
      }
    }

    log.warn(String.format("Could not find class `%1$s` (module: `%2$s`)", fqcn, module.getName()));
    return null;
  }

  @Nullable
  private ClassContent getClassFromTargetKeys(BlazeProjectData projectData, String fqcn, Set<TargetKey> visitedTargets, Collection<TargetKey> targetKeys) {
    String packageName  = StringUtil.getPackageName(fqcn);
    List<TargetKey> targetKeysSorted = sortTargetKeysWithPackageName(targetKeys, packageName);
    for (TargetKey targetKey : targetKeysSorted) {
      if(visitedTargets.contains(targetKey)) {
        continue;
      }
      ClassContent classFile = getClassFromClassJar(projectData, fqcn, targetKey);
      visitedTargets.add(targetKey);
      if (classFile != null) {
        packageToTargetKeyMap.put(packageName, targetKey);
        return classFile;
      }
    }
    return null;
  }

  @VisibleForTesting
  static boolean isResourceClass(String fqcn) {
    return RESOURCE_CLASS_NAME.matcher(fqcn).matches();
  }

  private ImmutableSet<TargetKey> getBinaryTargets() {
    long currentSyncCount =
        BlazeSyncModificationTracker.getInstance(project).getModificationCount();
    if (currentSyncCount == lastSyncCount) {
      // Return the cached set if there hasn't been a sync since last calculation
      return binaryTargets;
    }
    lastSyncCount = currentSyncCount;

    AndroidResourceModule androidResourceModule =
        AndroidResourceModuleRegistry.getInstance(project).get(module);
    if (androidResourceModule != null) {
      binaryTargets =
          TargetToBinaryMap.getInstance(project)
              .getBinariesDependingOn(androidResourceModule.sourceTargetKeys);
    } else if (isWorkspaceModule) {
      binaryTargets = TargetToBinaryMap.getInstance(project).getSourceBinaryTargets();
    } else {
      binaryTargets = ImmutableSet.of();
      log.warn("Could not find AndroidResourceModule for " + module.getName());
    }
    log.info(
        String.format(
            "Binary targets for module `%1$s`: %2$s",
            module.getName(),
            binaryTargets.stream()
                .limit(5)
                .map(t -> t.getLabel().toString())
                .collect(joining(", "))));
    return binaryTargets;
  }

  private ClassContent findClassInJar(File renderResolveJarFile, String fqcn) {
    String relativePath = ClassFileFinderUtil.getPathFromFqcn(fqcn);
    final byte[] bytes;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      try {
        Path targetPath = renderResolveJarFile.toPath().resolve("!" + relativePath);
        bytes = Files.isRegularFile(targetPath) ? Files.readAllBytes(targetPath) : new byte[0];
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      bytes =
          jarManager.loadFileFromJar(
              renderResolveJarFile.toPath(), ClassFileFinderUtil.getPathFromFqcn(fqcn));
    }
    if (bytes == null) {
      return null;
    }

    return ClassContent.fromJarEntryContent(renderResolveJarFile, bytes);
  }

  @Nullable
  private ClassContent getClassFromClassJar(
      BlazeProjectData projectData, String fqcn, TargetKey targetKey) {
    log.debug("Reading class from jar. Class name: " + fqcn + ", Target key label: " + targetKey.getLabel());

    ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
    TargetIdeInfo ideInfo = projectData.getTargetMap().get(targetKey);
    if (ideInfo == null
        || ideInfo.getJavaIdeInfo() == null
        || ideInfo.getJavaIdeInfo().getJars().isEmpty()
        || decoder == null) {
      return null;
    }

    File classJar = OutputArtifactResolver.resolve(project, decoder, ideInfo.getJavaIdeInfo().getJars().get(0).getClassJar());
    if (classJar == null || !classJar.exists()) {
      return null;
    }


    return findClassInJar(classJar, fqcn);
  }

  public static boolean isEnabled() {
    return enabled.getValue();
  }

  private static double jaccardIndex(String key, Collection<String> packageWords) {
    String[] keyWords = key.split(REGEX_WORDS);

    long intersectionCount = Arrays.stream(keyWords)
        .filter(packageWords::contains)
        .count();

    long unionCount = Arrays.stream(keyWords).distinct().count()
        + packageWords.size()
        - intersectionCount;

    return (double) intersectionCount / unionCount;
  }

  public static List<TargetKey> sortTargetKeysWithPackageName(Collection<TargetKey> targetKeys, String packageName) {
    Set<String> packageWords = Set.of(packageName.split(REGEX_WORDS));
    Map<TargetKey, Double> jaccardIndexes = targetKeys.stream()
        .collect(Collectors.toMap(Function.identity(), tk -> jaccardIndex(tk.toString(), packageWords)));

    return targetKeys.stream()
        .sorted(Comparator.comparing(jaccardIndexes::get).reversed())
        .limit(MAX_SORTED_TARGET_KEY_SIZE)
        .collect(Collectors.toList());
  }
}

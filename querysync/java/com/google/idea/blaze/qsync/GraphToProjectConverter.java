/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.project.BlazeProjectDataStorage;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.ContentRoot.Base;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/** Converts a {@link BuildGraphData} instance into a project proto. */
public class GraphToProjectConverter {

  private final PackageReader packageReader;
  private final Context context;

  private final ProjectDefinition projectDefinition;

  public GraphToProjectConverter(
      PackageReader packageReader, Context context, ProjectDefinition projectDefinition) {
    this.packageReader = packageReader;
    this.context = context;
    this.projectDefinition = projectDefinition;
  }

  @VisibleForTesting
  public Map<Path, Map<Path, String>> calculateRootSources(Collection<Path> files)
      throws IOException {

    Map<Path, Path> allDirs = new TreeMap<>();
    // Map directories to a source file they contain
    for (Path file : files) {
      allDirs.putIfAbsent(file.getParent(), file);
    }

    // Keep only one
    Map<Path, Path> dirs = new HashMap<>();
    // They are sorted, so prefixes should work:
    Map.Entry<Path, Path>[] dirsArray = allDirs.entrySet().toArray(new Map.Entry[0]);
    int last = -1;
    for (int i = 0; i < dirsArray.length; i++) {
      if (last == -1 || !dirsArray[i].getKey().startsWith(dirsArray[last].getKey())) {
        dirs.put(dirsArray[i].getKey(), dirsArray[i].getValue());
        last = i;
      }
    }

    // Group per root:
    Map<Path, Map<Path, Path>> rootDirs = new HashMap<>();
    for (Path root : projectDefinition.projectIncludes()) {
      Map<Path, Path> inRoot = new TreeMap<>(); // Must be sorted to do prefix later
      for (Entry<Path, Path> entry : dirs.entrySet()) {
        Path rel = entry.getKey();
        if (rel.startsWith(root)) {
          Path relToRoot = root.relativize(rel);
          inRoot.put(relToRoot, entry.getValue());
        }
      }
      rootDirs.put(root, inRoot);
    }

    Map<Path, Map<Path, String>> rootToPrefix = new HashMap<>();
    for (Entry<Path, Map<Path, Path>> entry : rootDirs.entrySet()) {
      Path root = entry.getKey();
      Map<Path, String> thisRootDirPrefixes = new HashMap<>();
      String lastRel = null;
      for (Entry<Path, Path> relToFile : entry.getValue().entrySet()) {
        if (lastRel == null || !relToFile.getKey().startsWith(lastRel)) {
          String[] relToPrefix =
              calculatePrefix(relToFile.getKey().toString(), relToFile.getValue());
          lastRel = relToPrefix[0];
          thisRootDirPrefixes.put(Path.of(relToPrefix[0]), relToPrefix[1]);
        }
      }
      rootToPrefix.put(root, thisRootDirPrefixes);
    }
    return rootToPrefix;
  }

  private String[] calculatePrefix(String rel, Path path) throws IOException {
    String pkg = Preconditions.checkNotNull(packageReader.readPackage(path), path);

    String pkgAsDir = "/" + pkg.replaceAll("\\.", "/");
    rel = "/" + rel;
    String suffix = Strings.commonSuffix(pkgAsDir, rel);

    int ix = suffix.indexOf('/');
    if (ix == -1) {
      suffix = "";
    } else {
      suffix = suffix.substring(ix);
    }
    rel = rel.substring(0, rel.length() - suffix.length());
    pkgAsDir = pkgAsDir.substring(0, pkgAsDir.length() - suffix.length());
    if (pkgAsDir.startsWith("/")) {
      pkgAsDir = pkgAsDir.substring(1);
    }
    if (rel.startsWith("/")) {
      rel = rel.substring(1);
    }
    return new String[] {rel, pkgAsDir.replaceAll("/", ".")};
  }

  public ProjectProto.Project createProject(BuildGraphData graph) throws IOException {
    Map<Path, Map<Path, String>> rootToPrefix = calculateRootSources(graph.getJavaSourceFiles());
    ImmutableSet<Path> dirs = computeAndroidResourceDirectories(graph.getAllSourceFiles());
    ImmutableSet<String> pkgs =
        computeAndroidSourcePackages(graph.getAndroidSourceFiles(), rootToPrefix);

    context.output(PrintOutput.log("%-10d Android resource directories", dirs.size()));
    context.output(PrintOutput.log("%-10d Android resource packages", pkgs.size()));

    ProjectProto.Library depsLib =
        ProjectProto.Library.newBuilder()
            .setName(BlazeProjectDataStorage.DEPENDENCIES_LIBRARY)
            .addClassesJar(
                ProjectProto.JarDirectory.newBuilder()
                    .setPath(
                        Paths.get(
                                BlazeProjectDataStorage.BLAZE_DATA_SUBDIRECTORY,
                                BlazeProjectDataStorage.LIBRARY_DIRECTORY)
                            .toString())
                    .setRecursive(false))
            .build();

    ProjectProto.Module.Builder workspaceModule =
        ProjectProto.Module.newBuilder()
            .setName(BlazeProjectDataStorage.WORKSPACE_MODULE_NAME)
            .setType(ProjectProto.ModuleType.MODULE_TYPE_DEFAULT)
            .addLibraryName(depsLib.getName())
            .addAllAndroidResourceDirectories(
                dirs.stream().map(Path::toString).collect(toImmutableList()))
            .addAllAndroidSourcePackages(pkgs);

    String generatedSourcePath =
        Paths.get(
                BlazeProjectDataStorage.BLAZE_DATA_SUBDIRECTORY,
                BlazeProjectDataStorage.GEN_SRC_DIRECTORY)
            .toString();
    ProjectProto.ContentEntry genSourcesContentEntry =
        ProjectProto.ContentEntry.newBuilder()
            .setRoot(
                ProjectProto.ContentRoot.newBuilder()
                    .setBase(Base.PROJECT)
                    .setPath(generatedSourcePath))
            .addSources(
                ProjectProto.SourceFolder.newBuilder()
                    .setPath(generatedSourcePath)
                    .setIsTest(false)
                    .setIsGenerated(true)
                    .build())
            .build();
    workspaceModule.addContentEntries(genSourcesContentEntry);

    ListMultimap<Path, Path> excludesByRootDirectory =
        projectDefinition.getExcludesByRootDirectory();
    for (Path dir : projectDefinition.projectIncludes()) {
      ProjectProto.ContentEntry.Builder contentEntry =
          ProjectProto.ContentEntry.newBuilder()
              .setRoot(
                  ProjectProto.ContentRoot.newBuilder()
                      .setPath(dir.toString())
                      .setBase(Base.WORKSPACE));
      Map<Path, String> sourceRootsWithPrefixes = rootToPrefix.get(dir);
      for (Entry<Path, String> entry : sourceRootsWithPrefixes.entrySet()) {
        Path path = dir.resolve(entry.getKey());
        contentEntry.addSources(
            ProjectProto.SourceFolder.newBuilder()
                .setPath(path.toString())
                .setPackagePrefix(entry.getValue())
                .setIsTest(false) // TODO
                .build());
      }
      for (Path exclude : excludesByRootDirectory.get(dir)) {
        contentEntry.addExcludes(exclude.toString());
      }
      workspaceModule.addContentEntries(contentEntry);
    }

    return ProjectProto.Project.newBuilder()
        .addLibrary(depsLib)
        .addModules(workspaceModule)
        .build();
  }

  /**
   * Heuristic for determining Android resource directories, by searching for .xml source files with
   * /res/ somewhere in the path. To be replaced by a more robust implementation.
   */
  @VisibleForTesting
  public static ImmutableSet<Path> computeAndroidResourceDirectories(List<Path> sourceFiles) {
    Set<Path> directories = new HashSet<>();
    for (Path sourceFile : sourceFiles) {

      if (sourceFile.getFileName().toString().endsWith(".xml")) {
        List<Path> pathParts = Lists.newArrayList(sourceFile);
        int resPos = pathParts.indexOf(Path.of("res"));
        if (resPos > 0) {
          directories.add(sourceFile.subpath(0, resPos + 1));
        }
      }
    }
    return ImmutableSet.copyOf(directories);
  }

  /**
   * Heuristic for computing android source java packages (used in generating R classes). Examines
   * packages of source files owned by Android targets (at most one file per target). Inefficient
   * for large projects with many android targets. To be replaced by a more robust implementation.
   */
  @VisibleForTesting
  public ImmutableSet<String> computeAndroidSourcePackages(
      List<Path> androidSourceFiles, Map<Path, Map<Path, String>> rootToPrefix) {
    ImmutableSet.Builder<String> androidSourcePackages = ImmutableSet.builder();
    for (Path androidSourceFile : androidSourceFiles) {
      boolean found = false;
      for (Entry<Path, Map<Path, String>> root : rootToPrefix.entrySet()) {
        if (androidSourceFile.startsWith(root.getKey())) {
          String inRoot =
              androidSourceFile.toString().substring(root.getKey().toString().length() + 1);
          Map<Path, String> sourceDirs = root.getValue();
          for (Entry<Path, String> prefixes : sourceDirs.entrySet()) {
            if (inRoot.startsWith(prefixes.getKey().toString())) {
              found = true;
              String inSource = inRoot.substring(prefixes.getKey().toString().length());
              int ix = inSource.lastIndexOf('/');
              String suffix = ix != -1 ? inSource.substring(0, ix) : "";
              if (suffix.startsWith("/")) {
                suffix = suffix.substring(1);
              }
              String pkg = prefixes.getValue();
              if (!suffix.isEmpty()) {
                if (pkg.length() > 0) {
                  pkg += ".";
                }
                pkg += suffix.replace('/', '.');
              }
              androidSourcePackages.add(pkg);
              break;
            }
          }
          if (found) {
            break;
          }
        }
      }
      if (!found) {
        context.output(
            PrintOutput.log(
                String.format("Android source %s not found in any root", androidSourceFile)));
      }
    }
    return androidSourcePackages.build();
  }
}

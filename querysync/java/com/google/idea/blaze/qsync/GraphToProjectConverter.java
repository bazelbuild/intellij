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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Comparator.comparingInt;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import com.google.idea.blaze.qsync.query.PackageSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Converts a {@link BuildGraphData} instance into a project proto. */
public class GraphToProjectConverter {

  private final PackageReader packageReader;
  private final Predicate<Path> fileExistanceCheck;
  private final Context<?> context;

  private final ProjectDefinition projectDefinition;

  public GraphToProjectConverter(
      PackageReader packageReader,
      Path workspaceRoot,
      Context<?> context,
      ProjectDefinition projectDefinition) {
    this.packageReader = packageReader;
    this.fileExistanceCheck = p -> Files.isRegularFile(workspaceRoot.resolve(p));
    this.context = context;
    this.projectDefinition = projectDefinition;
  }

  @VisibleForTesting
  public GraphToProjectConverter(
      PackageReader packageReader,
      Predicate<Path> fileExistanceCheck,
      Context<?> context,
      ProjectDefinition projectDefinition) {
    this.packageReader = packageReader;
    this.fileExistanceCheck = fileExistanceCheck;
    this.context = context;
    this.projectDefinition = projectDefinition;
  }

  /**
   * Calculates the source roots for all files in the project. While the vast majority of projects
   * will fall into the standard java/javatest packages, there are projects that do not conform with
   * this convention.
   *
   * <p>Mapping blaze projects to .imls will always be an aproximation, because blaze does not
   * impose any restrictions on how the source files are on disk. IntelliJ does.
   *
   * <p>The code in .imls is organized as follows (simplified view):
   *
   * <p>A project is a collection of modules. (For now we only have one module, so we do not model
   * dependencies yet). A module is a collection of content roots. A content root, is a directory
   * were code of different kind is located. Inside a content root there can be different source
   * roots. A source root is a directory inside the content root, that has a coherent group of
   * source files. A source root can be test only. Source roots can be nested. These source files
   * *must* be organized in a package-friendly directory structure. Most importantly, the directory
   * structure does not have to start at the root of the package, for that source roots can have a
   * package prefix that is a applied to the inner structure.
   *
   * <p>The algorithm implemented here makes one assumption over the code. All source files within
   * the same blaze package that are children of other source files, are correctly structured. This
   * is evidently not true for the general case, but even the most complex projects in our
   * repository follow this rule. And this is a rule, easy to workaround by a user if it doesn't
   * hold on their project.
   *
   * <pre>
   * The algorithm works as follows:
   *   1.- The top-most source files (most one per directory) is chosen per blaze package.
   *   2.- Read the actual package of each java file, and use that as the directories prefix.
   *   3.- Split all the packages by content root.
   *   4.- Merge compatible packages. This is a heuristic step, where each source root
   *       is bubbled up as far as possible, merging compatible siblings. For a better description
   *       see the comment on that function.
   * </pre>
   *
   * @param srcFiles all the files that should be included.
   * @param packages the BUILD files to create source roots for.
   * @return the content roots in the following form : Content Root -> Source Root -> package
   *     prefix. A content root contains multiple source roots, each one with a package prefix.
   */
  @VisibleForTesting
  public Map<Path, Map<Path, String>> calculateRootSources(
      Collection<Path> srcFiles, PackageSet packages) throws IOException {

    // A map from package to the file chosen to represent it.
    ImmutableList<Path> chosenFiles = chooseTopLevelFiles(srcFiles, packages);

    // A map from a directory to its prefix
    ImmutableMap<Path, String> prefixes = readPackages(chosenFiles);

    // All packages split by their content roots
    Map<Path, Map<Path, String>> rootToPrefix = splitByRoot(prefixes);

    // Merging packages that can share the same prefix
    mergeCompatibleSourceRoots(rootToPrefix);

    return rootToPrefix;
  }

  @VisibleForTesting
  Map<Path, Map<Path, String>> splitByRoot(Map<Path, String> prefixes) {
    Map<Path, Map<Path, String>> split = new HashMap<>();
    for (Path root : projectDefinition.projectIncludes()) {
      Map<Path, String> inRoot = new HashMap<>();
      for (Entry<Path, String> pkg : prefixes.entrySet()) {
        Path rel = pkg.getKey();
        if (rel.startsWith(root)) {
          Path relToRoot = root.relativize(rel);
          inRoot.put(relToRoot, pkg.getValue());
        }
      }
      split.put(root, inRoot);
    }
    return split;
  }

  private ImmutableMap<Path, String> readPackages(Collection<Path> files) throws IOException {
    long now = System.currentTimeMillis();
    ArrayList<Path> allFiles = new ArrayList<>(files);
    List<String> allPackages = packageReader.readPackages(allFiles);
    long elapsed = System.currentTimeMillis() - now;
    context.output(PrintOutput.log("%-10d Java files read (%d ms)", files.size(), elapsed));

    ImmutableMap.Builder<Path, String> prefixes = ImmutableMap.builder();
    Iterator<Path> i = allFiles.iterator();
    Iterator<String> j = allPackages.iterator();
    while (i.hasNext() && j.hasNext()) {
      prefixes.put(i.next().getParent(), j.next());
    }
    return prefixes.build();
  }

  @VisibleForTesting
  protected ImmutableList<Path> chooseTopLevelFiles(Collection<Path> files, PackageSet packages) {

    // A map from directory to the candidate chosen to represent that directory
    Map<Path, Path> candidates =
        files.stream()
            .filter(fileExistanceCheck)
            .collect(
                Collectors.toMap(
                    Path::getParent,
                    Function.identity(),
                    BinaryOperator.minBy(Comparator.comparing(Path::getFileName))));

    // Filter the files that are top level files only
    return candidates.values().stream()
        .filter(file -> isTopLevel(packages, candidates, file))
        .collect(ImmutableList.toImmutableList());
  }

  private static boolean isTopLevel(PackageSet packages, Map<Path, Path> candidates, Path file) {
    Path dir = relativeParentOf(file);
    while (dir != null) {
      Path existing = candidates.get(dir);
      if (existing != null && existing != file) {
        return false;
      }
      if (packages.contains(dir)) {
        return true;
      }
      dir = relativeParentOf(dir);
    }
    return false;
  }

  @Nullable
  private static Path relativeParentOf(Path path) {
    Preconditions.checkState(!path.isAbsolute());
    if (path.toString().isEmpty()) {
      return null;
    }
    Path parent = path.getParent();
    return parent == null ? Path.of("") : parent;
  }

  private static String lastSubpackageOf(String pkg) {
    return pkg.substring(pkg.lastIndexOf('.') + 1);
  }

  private static String parentPackageOf(String pkg) {
    int ix = pkg.lastIndexOf('.');
    return ix == -1 ? "" : pkg.substring(0, ix);
  }

  /**
   * Merges source roots that are compatible. Consider the following example, where source roots are
   * written like "directory" ["prefix"]:
   *
   * <pre>
   *   1.- Two sibling roots:
   *     "a/b/c/d" ["com.google.d"]
   *     "a/b/c/e" ["com.google.e"]
   *   Can be merged to:
   *     "a/b/c" ["com.google"]
   *
   *   2.- Nested roots:
   *     "a/b/c/d" ["com.google.d"]
   *     "a/b/c/d/e" ["com.google.d.e"]
   *   Can be merged to:
   *     "a/b/c" ["com.google"]
   * </pre>
   *
   * This function works by trying to move a source root up as far as possible (until it reaches the
   * content root). When it finds a source root above, there can be two scenarios: a) the parent
   * source root is compatible (like example 2 above), in which case they are merged. b) the parent
   * root is not compatible, in which case it needs to stop there and cannot be moved further up.
   * This is true even if the parent source root is later moved up.
   */
  @VisibleForTesting
  static void mergeCompatibleSourceRoots(Map<Path, Map<Path, String>> srcRoots) {
    for (Entry<Path, Map<Path, String>> contentRoot : srcRoots.entrySet()) {
      Map<Path, String> sourceRoots = contentRoot.getValue();
      Set<Path> directories = new TreeSet<>(sourceRoots.keySet());
      for (Path directory : directories) {
        String prefix = sourceRoots.remove(directory);
        while (!prefix.isEmpty()
            && lastSubpackageOf(prefix).equals(directory.getFileName().toString())) {
          Path parentDirectory = relativeParentOf(directory);
          String parentPrefix = parentPackageOf(prefix);
          String existing = sourceRoots.get(parentDirectory);
          if (existing != null) {
            if (existing.equals(parentPrefix)) {
              // Exists and it's the same it would have been, go up and merge both
              // Note that if the existing was or not already processed does not matter
              directory = parentDirectory;
              prefix = parentPrefix;
              break;
            } else {
              // The roots are not compatible, stop here
              break;
            }
          } else {
            // We can move one up, and keep trying
            directory = parentDirectory;
            prefix = parentPrefix;
          }
        }
        sourceRoots.putIfAbsent(directory, prefix);
      }
    }
  }

  public ProjectProto.Project createProject(BuildGraphData graph) throws IOException {
    Map<Path, Map<Path, String>> rootToPrefix =
        calculateRootSources(graph.getJavaSourceFiles(), graph.packages());
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

    // Map entries are sorted by path length to ensure that, if the map contains keys k1 and k2,
    // where k1 is a prefix of k2, then k2 is checked before k1. We check by string length to ensure
    // the empty path is checked last.
    ImmutableMap<Path, ImmutableList<Entry<Path, String>>> sortedRootToPrefix =
        rootToPrefix.entrySet().stream()
            .map(
                entry -> {
                  Map<Path, String> sourceDirs = entry.getValue();
                  ImmutableList<Entry<Path, String>> sortedEntries =
                      ImmutableList.sortedCopyOf(
                          Collections.reverseOrder(
                              comparingInt(e -> e.getKey().toString().length())),
                          sourceDirs.entrySet());
                  return new SimpleEntry<>(entry.getKey(), sortedEntries);
                })
            .collect(toImmutableMap(Entry::getKey, Entry::getValue));

    for (Path androidSourceFile : androidSourceFiles) {
      boolean found = false;
      for (Entry<Path, ImmutableList<Entry<Path, String>>> root : sortedRootToPrefix.entrySet()) {
        if (androidSourceFile.startsWith(root.getKey())) {
          String inRoot =
              androidSourceFile.toString().substring(root.getKey().toString().length() + 1);
          ImmutableList<Entry<Path, String>> sourceDirs = root.getValue();
          for (Entry<Path, String> prefixes : sourceDirs) {
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

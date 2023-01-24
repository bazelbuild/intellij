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

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.project.ProjectProto;
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

/** Converts a {@link com.google.idea.blaze.qsync.BuildGraph} instance into a project proto. */
public class GraphToProjectConverter {

  private final PackageReader packageReader;
  private final Context context;
  private final Path workspaceRoot;

  private final ImmutableList<Path> importRoots;
  private final ImmutableList<Path> excludePaths;

  public GraphToProjectConverter(
      PackageReader packageReader,
      Context context,
      Path workspaceRoot,
      ImmutableList<Path> importRoots,
      ImmutableList<Path> excludePaths) {
    this.packageReader = packageReader;
    this.context = context;
    this.workspaceRoot = workspaceRoot;
    this.importRoots = importRoots;
    this.excludePaths = excludePaths;
  }

  private Map<String, Map<String, String>> calculateRootSources(List<String> files)
      throws IOException {

    Map<String, Path> allDirs = new TreeMap<>();
    // Convert to directories
    for (String file : files) {
      Path path = Paths.get(file);
      Path dir = path.getParent();
      String rel = workspaceRoot.relativize(dir).toString();
      allDirs.computeIfAbsent(rel, x -> path);
    }

    // Keep only one
    Map<String, Path> dirs = new HashMap<>();
    // They are sorted, so prefixes should work:
    Map.Entry<String, Path>[] dirsArray = allDirs.entrySet().toArray(new Map.Entry[0]);
    int last = -1;
    for (int i = 0; i < dirsArray.length; i++) {
      if (last == -1 || !dirsArray[i].getKey().startsWith(dirsArray[last].getKey())) {
        dirs.put(dirsArray[i].getKey(), dirsArray[i].getValue());
        last = i;
      }
    }

    // Group per root:
    Map<String, Map<String, Path>> rootDirs = new HashMap<>();
    for (Path root : importRoots) {
      Map<String, Path> inRoot = new TreeMap<>(); // Must be sorted to do prefix later
      for (Entry<String, Path> entry : dirs.entrySet()) {
        String rel = entry.getKey();
        if (rel.startsWith(root.toString())) {
          Path relToRoot = Paths.get(root.toString()).relativize(Paths.get(rel));
          inRoot.put(relToRoot.toString(), entry.getValue());
        }
      }
      rootDirs.put(root.toString(), inRoot);
    }

    Map<String, Map<String, String>> rootToPrefix = new HashMap<>();
    long now = System.nanoTime();
    long filesRead = 0;
    for (Entry<String, Map<String, Path>> entry : rootDirs.entrySet()) {
      String root = entry.getKey();
      Map<String, String> thisRootDirPrefixes = new HashMap<>();
      String lastRel = null;
      for (Entry<String, Path> relToFile : entry.getValue().entrySet()) {
        if (lastRel == null || !relToFile.getKey().startsWith(lastRel)) {
          String[] relToPrefix = calculatePrefix(relToFile.getKey(), relToFile.getValue());
          filesRead++;
          lastRel = relToPrefix[0];
          thisRootDirPrefixes.put(relToPrefix[0], relToPrefix[1]);
        }
      }
      rootToPrefix.put(root, thisRootDirPrefixes);
    }
    long elapsedMs = (System.nanoTime() - now) / 1000000L;
    context.output(
        PrintOutput.log((String.format("Read %d files in %d ms", filesRead, elapsedMs))));
    return rootToPrefix;
  }

  private String[] calculatePrefix(String rel, Path path) throws IOException {
    String pkg = packageReader.readPackage(path);

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
    Map<String, Map<String, String>> rootToPrefix =
        calculateRootSources(graph.getJavaSourceFiles());
    ImmutableSet<String> androidResourceDirectories =
        computeAndroidResourceDirectories(graph.getAllSourceFiles());
    ImmutableSet<String> androidSourcePackages =
        computeAndroidSourcePackages(graph.getAndroidSourceFiles(), rootToPrefix);

    context.output(
        PrintOutput.log(
            String.format(
                "Detected %d android resource directories", androidResourceDirectories.size())));
    context.output(
        PrintOutput.log(
            String.format("Detected %d android resource packages", androidSourcePackages.size())));

    ProjectProto.Library depsLib =
        ProjectProto.Library.newBuilder()
            .setName(".dependencies")
            .addClassesJar(
                ProjectProto.JarDirectory.newBuilder()
                    .setPath(".blaze/libraries")
                    .setRecursive(false))
            .build();

    ProjectProto.Module.Builder workspaceModule =
        ProjectProto.Module.newBuilder()
            .setName(".workspace")
            .setType(ProjectProto.ModuleType.MODULE_TYPE_DEFAULT)
            .addLibraryName(depsLib.getName())
            .addAllAndroidResourceDirectories(androidResourceDirectories)
            .addAllAndroidSourcePackages(androidSourcePackages);

    ListMultimap<Path, Path> excludesByRootDirectory =
        sortExcludesByRootDirectory(importRoots, excludePaths);
    for (Path dir : importRoots) {
      Path rootFile = workspaceRoot.resolve(dir);
      ProjectProto.ContentEntry.Builder contentEntry =
          ProjectProto.ContentEntry.newBuilder().setRoot(rootFile.toString());
      Map<String, String> sourceRootsWithPrefixes = rootToPrefix.get(dir.toString());
      for (Entry<String, String> entry : sourceRootsWithPrefixes.entrySet()) {
        Path rDir = workspaceRoot.resolve(dir);
        Path path = rDir.resolve(entry.getKey());
        contentEntry.addSources(
            ProjectProto.SourceFolder.newBuilder()
                .setPath(path.toString())
                .setPackagePrefix(entry.getValue())
                .setIsTest(false) // TODO
                .build());
      }
      for (Path exclude : excludesByRootDirectory.get(dir)) {
        Path excludeFolder = workspaceRoot.resolve(exclude);
        contentEntry.addExcludes(excludeFolder.toString());
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
  private ImmutableSet<String> computeAndroidResourceDirectories(List<String> sourceFiles) {
    Set<String> directories = new HashSet<>();
    for (String sourceFile : sourceFiles) {
      if (sourceFile.endsWith(".xml") && sourceFile.contains("/res/")) {
        directories.add(sourceFile.substring(0, sourceFile.indexOf("/res/")) + "/res");
      }
    }
    return ImmutableSet.copyOf(directories);
  }

  /**
   * Heuristic for computing android source java packages (used in generating R classes). Examines
   * packages of source files owned by Android targets (at most one file per target). Inefficient
   * for large projects with many android targets. To be replaced by a more robust implementation.
   */
  private ImmutableSet<String> computeAndroidSourcePackages(
      List<String> androidSourceFiles, Map<String, Map<String, String>> rootToPrefix) {
    ImmutableSet.Builder<String> androidSourcePackages = ImmutableSet.builder();
    for (String androidSourceFile : androidSourceFiles) {
      boolean found = false;
      for (Entry<String, Map<String, String>> root : rootToPrefix.entrySet()) {
        String workspacePath = workspaceRoot.relativize(Paths.get(androidSourceFile)).toString();
        if (workspacePath.startsWith(root.getKey())) {
          String inRoot = workspacePath.substring(root.getKey().length() + 1);
          Map<String, String> sourceDirs = root.getValue();
          for (Entry<String, String> prefixes : sourceDirs.entrySet()) {
            if (inRoot.startsWith(prefixes.getKey())) {
              found = true;
              String inSource = inRoot.substring(prefixes.getKey().length());
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

  public static ListMultimap<Path, Path> sortExcludesByRootDirectory(
      Collection<Path> rootDirectories, Collection<Path> excludedDirectories) {

    ListMultimap<Path, Path> result = ArrayListMultimap.create();
    for (Path exclude : excludedDirectories) {
      rootDirectories.stream()
          .filter(rootDirectory -> isUnderRootDirectory(rootDirectory, exclude))
          .findFirst()
          .ifPresent(foundWorkspacePath -> result.put(foundWorkspacePath, exclude));
    }
    return result;
  }

  private static boolean isUnderRootDirectory(Path rootDirectory, Path relativePath) {
    // TODO this can probably be cleaned up (or removed?) by using Path API properly.
    if (rootDirectory.toString().equals(".") || rootDirectory.toString().isEmpty()) {
      return true;
    }
    String rootDirectoryString = rootDirectory.toString();
    return relativePath.startsWith(rootDirectoryString)
        && (relativePath.toString().length() == rootDirectoryString.length()
            || (relativePath.toString().charAt(rootDirectoryString.length()) == '/'));
  }

}

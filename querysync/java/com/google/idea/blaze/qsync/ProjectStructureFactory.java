/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the basic project structure, and contains logic for deriving it from a {@link
 * BuildGraph}.
 */
public class ProjectStructureFactory {

  private final Path workspaceRoot;
  private final ImmutableList<Path> importRoots;

  public ProjectStructureFactory(Path workspaceRoot, ImmutableList<Path> importRoots) {
    this.workspaceRoot = workspaceRoot;
    this.importRoots = importRoots;
  }

  public ProjectStructure forBuildGraph(Context context, BuildGraph graph) throws IOException {
    return forSourceFiles(
        context,
        graph.getJavaSourceFiles(),
        graph.getAllSourceFiles(),
        graph.getAndroidSourceFiles());
  }

  @VisibleForTesting
  ProjectStructure forSourceFiles(
      Context context,
      List<String> javaSourceFiles,
      List<String> allSourceFiles,
      List<String> androidSourceFiles)
      throws IOException {
    ImmutableMap<String, ImmutableMap<String, String>> sourceRoots =
        calculateJavaRootSources(context, javaSourceFiles);
    ImmutableSet<String> androidResourceDirectories =
        computeAndroidResourceDirectories(allSourceFiles);
    ImmutableSet<String> androidSourcePackages =
        computeAndroidSourcePackages(context, sourceRoots, androidSourceFiles);
    return ProjectStructure.create(sourceRoots, androidResourceDirectories, androidSourcePackages);
  }

  private ImmutableMap<String, ImmutableMap<String, String>> calculateJavaRootSources(
      Context context, List<String> files) throws IOException {

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

    ImmutableMap.Builder<String, ImmutableMap<String, String>> rootToPrefix =
        ImmutableMap.builder();
    long now = System.nanoTime();
    long filesRead = 0;
    for (Entry<String, Map<String, Path>> entry : rootDirs.entrySet()) {
      String root = entry.getKey();
      ImmutableMap.Builder<String, String> thisRootDirPrefixes = ImmutableMap.builder();
      String lastRel = null;
      for (Entry<String, Path> relToFile : entry.getValue().entrySet()) {
        if (lastRel == null || !relToFile.getKey().startsWith(lastRel)) {
          String[] relToPrefix = calculatePrefix(relToFile.getKey(), relToFile.getValue());
          filesRead++;
          lastRel = relToPrefix[0];
          thisRootDirPrefixes.put(relToPrefix[0], relToPrefix[1]);
        }
      }
      rootToPrefix.put(root, thisRootDirPrefixes.buildOrThrow());
    }
    long elapsedMs = (System.nanoTime() - now) / 1000000L;
    context.output(
        PrintOutput.log((String.format("Read %d files in %d ms", filesRead, elapsedMs))));
    return rootToPrefix.buildOrThrow();
  }

  private String[] calculatePrefix(String rel, Path path) throws IOException {
    String pkg = readPackage(path.toString());

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

  private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w\\.]+)");

  private static String readPackage(String file) throws IOException {
    BufferedReader javaReader =
        new BufferedReader(new InputStreamReader(new FileInputStream(file)));
    String javaLine;
    while ((javaLine = javaReader.readLine()) != null) {
      Matcher packageMatch = PACKAGE_PATTERN.matcher(javaLine);
      if (packageMatch.find()) {
        return packageMatch.group(1);
      }
    }
    return "";
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
      Context context,
      Map<String, ? extends Map<String, String>> rootToPrefix,
      List<String> androidSourceFiles) {
    ImmutableSet.Builder<String> androidSourcePackages = ImmutableSet.builder();
    for (String androidSourceFile : androidSourceFiles) {
      boolean found = false;
      for (Entry<String, ? extends Map<String, String>> root : rootToPrefix.entrySet()) {
        String workspacePath = workspaceRoot.relativize(Paths.get(androidSourceFile)).toString();
        if (workspacePath.startsWith(root.getKey())) {
          String inRoot = workspacePath.substring(root.getKey().length() + 1);
          Map<String, String> sourceDirs = root.getValue();
          for (Entry<String, String> prefixes : sourceDirs.entrySet()) {
            if (inRoot.startsWith(prefixes.getKey())) {
              found = true;
              String inSource = inRoot.substring(prefixes.getKey().length());
              int ix = inRoot.lastIndexOf('/');
              String suffix = ix != -1 ? inSource.substring(0, ix) : "";
              if (suffix.startsWith("/")) {
                suffix = suffix.substring(1);
              }
              String pkg = prefixes.getValue();
              if (!suffix.isEmpty()) {
                pkg = pkg + "." + suffix.replace('/', '.');
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

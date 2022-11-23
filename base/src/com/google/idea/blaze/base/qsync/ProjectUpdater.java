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
package com.google.idea.blaze.base.qsync;

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.qsync.BuildGraph.BuildGraphListener;
import com.google.idea.blaze.base.qsync.BuildGraph.Location;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.util.UrlUtil;
import com.google.idea.common.util.Transactions;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.Library.ModifiableModel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** An object that monitors the build graph and applies the changes to the project structure. */
public class ProjectUpdater implements BuildGraphListener {

  private Project project;
  private final BuildGraph graph;

  public ProjectUpdater(Project project, BuildGraph graph) {
    this.project = project;
    this.graph = graph;
    graph.addListener(this);
  }

  public void update(BlazeContext context) throws IOException {}

  private Map<String, Map<String, String>> calculateRootSources(
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ImportRoots ir,
      Map<String, Location> locations,
      Set<String> javaSources)
      throws IOException {

    Map<String, Path> allDirs = new TreeMap<>();
    // Convert to directories
    for (String src : javaSources) {
      Location location = locations.get(src);
      if (location == null) {
        continue;
      }
      Path path = Paths.get(location.file);
      Path dir = path.getParent();
      String rel = workspaceRoot.workspacePathFor(dir.toFile()).toString();
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
    for (WorkspacePath root : ir.rootDirectories()) {
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

  public String readPackage(String file) throws IOException {
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

  @Override
  public void graphCreated(BlazeContext context) throws IOException {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);

    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    ImportRoots ir =
        ImportRoots.builder(workspaceRoot, importSettings.getBuildSystem())
            .add(projectViewSet)
            .build();

    Map<String, Map<String, String>> rootToPrefix =
        calculateRootSources(context, workspaceRoot, ir, graph.locations, graph.javaSources);

    ModuleManager moduleManager = ModuleManager.getInstance(project);

    File imlDirectory = new File(BlazeDataStorage.getProjectDataDir(importSettings), "modules");
    ModuleType<?> mt = ModuleTypeManager.getInstance().getDefaultModuleType();

    Transactions.submitWriteActionTransactionAndWait(
        () -> {
          for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
            syncPlugin.updateProjectSdk(project, context, projectViewSet);
          }

          Module module =
              moduleManager.newModule(imlDirectory.toPath().resolve(".workspace.iml"), mt.getId());

          ModifiableRootModel roots = ModuleRootManager.getInstance(module).getModifiableModel();
          roots.inheritSdk();
          Multimap<WorkspacePath, WorkspacePath> excludesByRootDirectory =
              sortExcludesByRootDirectory(ir.rootDirectories(), ir.excludeDirectories());

          for (WorkspacePath dir : ir.rootDirectories()) {
            File rootFile = workspaceRoot.fileForPath(dir);
            ContentEntry contentEntry =
                roots.addContentEntry(UrlUtil.pathToUrl(rootFile.getPath()));
            Map<String, String> sourceRootsWithPrefixes = rootToPrefix.get(dir.toString());
            for (Entry<String, String> entry : sourceRootsWithPrefixes.entrySet()) {
              Path rDir = workspaceRoot.directory().toPath().resolve(dir.toString());
              File path = rDir.resolve(entry.getKey()).toFile();
              SourceFolder sourceFolder =
                  contentEntry.addSourceFolder(UrlUtil.fileToIdeaUrl(path), false);
              sourceFolder.setPackagePrefix(entry.getValue());
            }
            for (WorkspacePath exclude : excludesByRootDirectory.get(dir)) {
              File excludeFolder = workspaceRoot.fileForPath(exclude);
              contentEntry.addExcludeFolder(UrlUtil.fileToIdeaUrl(excludeFolder));
            }
          }

          IdeModifiableModelsProvider models =
              ProjectDataManager.getInstance().createModifiableModelsProvider(project);
          Library library = models.createLibrary(".dependencies");
          ModifiableModel modifiableModel = library.getModifiableModel();
          Path libs = Paths.get(project.getBasePath()).resolve(".blaze/libraries");
          modifiableModel.addJarDirectory(UrlUtil.fileToIdeaUrl(libs.toFile()), false);
          modifiableModel.commit();

          LibraryOrderEntry entry = roots.addLibraryEntry(library);
          entry.setScope(DependencyScope.COMPILE);
          entry.setExported(false);
          models.commit();
          roots.commit();
        });
  }

  public static Multimap<WorkspacePath, WorkspacePath> sortExcludesByRootDirectory(
      Collection<WorkspacePath> rootDirectories, Collection<WorkspacePath> excludedDirectories) {

    Multimap<WorkspacePath, WorkspacePath> result = ArrayListMultimap.create();
    for (WorkspacePath exclude : excludedDirectories) {
      rootDirectories.stream()
          .filter(rootDirectory -> isUnderRootDirectory(rootDirectory, exclude.relativePath()))
          .findFirst()
          .ifPresent(foundWorkspacePath -> result.put(foundWorkspacePath, exclude));
    }
    return result;
  }

  private static boolean isUnderRootDirectory(WorkspacePath rootDirectory, String relativePath) {
    if (rootDirectory.isWorkspaceRoot()) {
      return true;
    }
    String rootDirectoryString = rootDirectory.toString();
    return relativePath.startsWith(rootDirectoryString)
        && (relativePath.length() == rootDirectoryString.length()
            || (relativePath.charAt(rootDirectoryString.length()) == '/'));
  }
}

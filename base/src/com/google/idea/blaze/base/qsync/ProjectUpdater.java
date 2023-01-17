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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.util.UrlUtil;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BuildGraph;
import com.google.idea.blaze.qsync.BuildGraph.BuildGraphListener;
import com.google.idea.blaze.qsync.project.ProjectProto;
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
import com.intellij.openapi.roots.OrderRootType;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
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

  private Map<String, Map<String, String>> calculateRootSources(
      Context context, Path workspaceRoot, List<Path> importRoots, List<String> files)
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

  public static ModuleType<?> mapModuleType(ProjectProto.ModuleType type) {
    switch (type) {
      case MODULE_TYPE_DEFAULT:
        return ModuleTypeManager.getInstance().getDefaultModuleType();
      case UNRECOGNIZED:
        break;
    }
    throw new IllegalStateException("Unrecognised module type " + type);
  }

  @Override
  public void graphCreated(Context context) throws IOException {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);

    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    ImportRoots ir =
        ImportRoots.builder(workspaceRoot, importSettings.getBuildSystem())
            .add(projectViewSet)
            .build();

    Map<String, Map<String, String>> rootToPrefix =
        calculateRootSources(
            context,
            workspaceRoot.directory().toPath(),
            ir.rootPaths(),
            graph.getJavaSourceFiles());

    ImmutableSet<String> androidResourceDirectories =
        computeAndroidResourceDirectories(graph.getAllSourceFiles());
    ImmutableSet<String> androidSourcePackages =
        computeAndroidSourcePackages(context, workspaceRoot.directory().toPath(), rootToPrefix);

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

    Multimap<WorkspacePath, WorkspacePath> excludesByRootDirectory =
        sortExcludesByRootDirectory(ir.rootDirectories(), ir.excludeDirectories());
    for (WorkspacePath dir : ir.rootDirectories()) {
      File rootFile = workspaceRoot.fileForPath(dir);
      ProjectProto.ContentEntry.Builder contentEntry =
          ProjectProto.ContentEntry.newBuilder().setRoot(rootFile.getAbsolutePath());
      Map<String, String> sourceRootsWithPrefixes = rootToPrefix.get(dir.toString());
      for (Entry<String, String> entry : sourceRootsWithPrefixes.entrySet()) {
        Path rDir = workspaceRoot.directory().toPath().resolve(dir.toString());
        Path path = rDir.resolve(entry.getKey());
        contentEntry.addSources(
            ProjectProto.SourceFolder.newBuilder()
                .setPath(path.toString())
                .setPackagePrefix(entry.getValue())
                .setIsTest(false) // TODO
                .build());
      }
      for (WorkspacePath exclude : excludesByRootDirectory.get(dir)) {
        File excludeFolder = workspaceRoot.fileForPath(exclude);
        contentEntry.addExcludes(excludeFolder.toPath().toString());
      }
      workspaceModule.addContentEntries(contentEntry);
    }

    ProjectProto.Project project =
        ProjectProto.Project.newBuilder().addLibrary(depsLib).addModules(workspaceModule).build();

    updateProjectModel(project, importSettings, projectViewSet, workspaceRoot, context);
  }

  private void updateProjectModel(
      ProjectProto.Project spec,
      BlazeImportSettings importSettings,
      ProjectViewSet projectViewSet,
      WorkspaceRoot workspaceRoot,
      Context context) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    File imlDirectory = new File(BlazeDataStorage.getProjectDataDir(importSettings), "modules");
    Transactions.submitWriteActionTransactionAndWait(
        () -> {
          for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
            syncPlugin.updateProjectSdk(project, context, projectViewSet);
          }

          IdeModifiableModelsProvider models =
              ProjectDataManager.getInstance().createModifiableModelsProvider(project);
          int removedLibCount = removeUnusedLibraries(models, spec.getLibraryList());
          if (removedLibCount > 0) {
            context.output(PrintOutput.output("Removed " + removedLibCount + " libs"));
          }
          ImmutableMap.Builder<String, Library> libMapBuilder = ImmutableMap.builder();
          for (ProjectProto.Library libSpec : spec.getLibraryList()) {
            Library library = getOrCreateLibrary(models, libSpec);
            libMapBuilder.put(libSpec.getName(), library);
          }
          ImmutableMap<String, Library> libMap = libMapBuilder.buildOrThrow();
          models.commit();

          for (ProjectProto.Module moduleSpec : spec.getModulesList()) {
            Module module =
                moduleManager.newModule(
                    imlDirectory.toPath().resolve(moduleSpec.getName() + ".iml"),
                    mapModuleType(moduleSpec.getType()).getId());

            ModifiableRootModel roots = ModuleRootManager.getInstance(module).getModifiableModel();
            // TODO: should this be encapsulated in ProjectProto.Module?
            roots.inheritSdk();

            // TODO instead of removing all content entries and re-adding, we should calculate the
            //  diff.
            for (ContentEntry entry : roots.getContentEntries()) {
              roots.removeContentEntry(entry);
            }
            for (ProjectProto.ContentEntry ceSpec : moduleSpec.getContentEntriesList()) {

              ContentEntry contentEntry =
                  roots.addContentEntry(UrlUtil.pathToUrl(ceSpec.getRoot()));
              for (ProjectProto.SourceFolder sfSpec : ceSpec.getSourcesList()) {
                SourceFolder sourceFolder =
                    contentEntry.addSourceFolder(
                        UrlUtil.pathToUrl(sfSpec.getPath()), sfSpec.getIsTest());
                sourceFolder.setPackagePrefix(sfSpec.getPackagePrefix());
              }
              for (String exclude : ceSpec.getExcludesList()) {
                contentEntry.addExcludeFolder(UrlUtil.pathToUrl(exclude));
              }
            }

            for (String lib : moduleSpec.getLibraryNameList()) {
              Library library = libMap.get(lib);
              if (library == null) {
                throw new IllegalStateException(
                    "Module refers to library " + lib + " not present in the project spec");
              }
              LibraryOrderEntry entry = roots.addLibraryEntry(library);
              // TODO should this stuff be specified by the Module proto too?
              entry.setScope(DependencyScope.COMPILE);
              entry.setExported(false);
            }

            for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
              // TODO update ProjectProto.Module and updateProjectStructure() to allow a more
              // suitable
              //   data type to be passed in here instead of androidResourceDirectories and
              //   androidSourcePackages
              syncPlugin.updateProjectStructure(
                  project,
                  context,
                  workspaceRoot,
                  module,
                  ImmutableSet.copyOf(moduleSpec.getAndroidResourceDirectoriesList()),
                  ImmutableSet.copyOf(moduleSpec.getAndroidSourcePackagesList()));
            }
            roots.commit();
          }
        });
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
      Context context, Path workspaceRoot, Map<String, Map<String, String>> rootToPrefix) {
    ImmutableSet.Builder<String> androidSourcePackages = ImmutableSet.builder();
    for (String androidSourceFile : graph.getAndroidSourceFiles()) {
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

  private Library getOrCreateLibrary(
      IdeModifiableModelsProvider models, ProjectProto.Library libSpec) {
    // TODO this needs more work, it's a bit messy.
    Library library = models.getLibraryByName(libSpec.getName());
    if (library == null) {
      library = models.createLibrary(libSpec.getName());
    }
    Path projectBase = Paths.get(project.getBasePath());
    ImmutableMap<String, ProjectProto.JarDirectory> dirs =
        libSpec.getClassesJarList().stream()
            .collect(
                ImmutableMap.toImmutableMap(
                    d -> UrlUtil.pathToIdeaUrl(projectBase.resolve(d.getPath())),
                    Function.identity()));

    // make sure the library contains only jar directory urls we want
    ModifiableModel modifiableModel = library.getModifiableModel();

    Set<String> foundJarDirectories = Sets.newHashSet();
    for (String url : modifiableModel.getUrls(OrderRootType.CLASSES)) {
      if (modifiableModel.isJarDirectory(url) && dirs.containsKey(url)) {
        foundJarDirectories.add(url);
      } else {
        modifiableModel.removeRoot(url, OrderRootType.CLASSES);
      }
    }
    for (String notFound : Sets.difference(dirs.keySet(), foundJarDirectories)) {
      ProjectProto.JarDirectory dir = dirs.get(notFound);
      modifiableModel.addJarDirectory(notFound, dir.getRecursive(), OrderRootType.CLASSES);
    }
    modifiableModel.commit();
    return library;
  }

  /**
   * Removes any existing library that should not be used by this project e.g. inherit from old
   * project.
   */
  private int removeUnusedLibraries(
      IdeModifiableModelsProvider models, List<ProjectProto.Library> libraries) {
    ImmutableSet<String> librariesToKeep =
        libraries.stream().map(ProjectProto.Library::getName).collect(toImmutableSet());
    int removedLibCount = 0;
    for (Library library : models.getAllLibraries()) {
      if (!librariesToKeep.contains(library.getName())) {
        removedLibCount++;
        models.removeLibrary(library);
      }
    }
    return removedLibCount;
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

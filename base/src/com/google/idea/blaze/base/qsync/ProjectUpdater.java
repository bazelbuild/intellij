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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
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
import com.google.idea.blaze.qsync.ProjectStructure;
import com.google.idea.blaze.qsync.ProjectStructureFactory;
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map.Entry;

/** An object that monitors the build graph and applies the changes to the project structure. */
public class ProjectUpdater implements BuildGraphListener {

  private Project project;
  private final BuildGraph graph;

  public ProjectUpdater(Project project, BuildGraph graph) {
    this.project = project;
    this.graph = graph;
    graph.addListener(this);
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

    ProjectStructureFactory projectStructureBuilder =
        new ProjectStructureFactory(workspaceRoot.directory().toPath(), ir.rootPaths());

    ProjectStructure projectStructure = projectStructureBuilder.forBuildGraph(context, graph);

    context.output(
        PrintOutput.log(
            String.format(
                "Detected %d android resource directories",
                projectStructure.androidResourceDirectories().size())));
    context.output(
        PrintOutput.log(
            String.format(
                "Detected %d android resource packages",
                projectStructure.androidSourcePackages().size())));

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
            ImmutableMap<String, String> sourceRootsWithPrefixes =
                projectStructure.javaSourceRoots().get(dir.toString());
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
          String libraryName = ".dependencies";
          int removedLibCount = removeUnusedLibraries(models, libraryName);
          if (removedLibCount > 0) {
            context.output(PrintOutput.output("Removed " + removedLibCount + " libs"));
          }
          Library library = getOrCreateLibrary(models, libraryName);
          models.commit();

          LibraryOrderEntry entry = roots.addLibraryEntry(library);
          entry.setScope(DependencyScope.COMPILE);
          entry.setExported(false);
          for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
            syncPlugin.updateProjectStructure(
                project,
                context,
                workspaceRoot,
                module,
                projectStructure.androidResourceDirectories(),
                projectStructure.androidSourcePackages());
          }
          roots.commit();
        });
  }

  private Library getOrCreateLibrary(IdeModifiableModelsProvider models, String libraryName) {
    Library library = models.getLibraryByName(libraryName);
    if (library == null) {
      library = models.createLibrary(libraryName);
    }
    // make sure the library contains only jar directory url
    ModifiableModel modifiableModel = library.getModifiableModel();
    Path libs = Paths.get(project.getBasePath()).resolve(".blaze/libraries");

    boolean findJarDirectory = false;
    for (String url : modifiableModel.getUrls(OrderRootType.CLASSES)) {
      if (url.equals(UrlUtil.fileToIdeaUrl(libs.toFile())) && modifiableModel.isJarDirectory(url)) {
        findJarDirectory = true;
      } else {
        modifiableModel.removeRoot(url, OrderRootType.CLASSES);
      }
    }
    if (!findJarDirectory) {
      modifiableModel.addJarDirectory(UrlUtil.fileToIdeaUrl(libs.toFile()), false);
    }
    modifiableModel.commit();
    return library;
  }

  /**
   * Removes any existing library that should not be used by this project e.g. inherit rom old
   * project.
   */
  private int removeUnusedLibraries(IdeModifiableModelsProvider models, String libraryToKeep) {
    int removedLibCount = 0;
    for (Library library : models.getAllLibraries()) {
      if (!libraryToKeep.equals(library.getName())) {
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

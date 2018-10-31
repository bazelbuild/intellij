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
package com.google.idea.blaze.android.sync.projectstructure;

import static com.google.idea.blaze.android.sync.importer.BlazeImportInput.createLooksLikeAarLibrary;
import static java.util.stream.Collectors.toSet;

import com.android.builder.model.SourceProvider;
import com.android.ide.common.gradle.model.SourceProviderUtil;
import com.android.ide.common.util.PathStringUtil;
import com.android.projectmodel.AndroidPathType;
import com.android.projectmodel.SourceSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.idea.blaze.android.projectview.GeneratedAndroidResourcesSection;
import com.google.idea.blaze.android.resources.BlazeLightResourceClassService;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.android.sync.model.idea.BlazeAndroidModel;
import com.google.idea.blaze.android.sync.sdk.SdkUtil;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorProvider;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTable;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Updates the IDE's project structure. */
public class BlazeAndroidProjectStructureSyncer {
  private static final Logger logger = Logger.getInstance(BlazeAndroidProjectStructureSyncer.class);
  private static final BoolExperiment useCyclicResourceDependency =
      new BoolExperiment("cyclic.resource.dependency", true);
  private static final BoolExperiment useLibraryResourcesModule =
      new BoolExperiment("library.resources.module", true);
  public static final String LIBRARY_RESOURCES_MODULE_NAME = ".android-resources";

  public static void updateProjectStructure(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      BlazeSyncPlugin.ModuleEditor moduleEditor,
      Module workspaceModule,
      ModifiableRootModel workspaceModifiableModel,
      boolean isAndroidWorkspace) {
    if (!isAndroidWorkspace) {
      AndroidFacetModuleCustomizer.removeAndroidFacet(workspaceModule);
      return;
    }

    BlazeAndroidSyncData syncData = blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class);
    if (syncData == null) {
      return;
    }
    AndroidSdkPlatform androidSdkPlatform = syncData.androidSdkPlatform;
    if (androidSdkPlatform == null) {
      return;
    }

    // Configure workspace module as an android module
    AndroidFacetModuleCustomizer.createAndroidFacet(workspaceModule, false);

    // Create android resource modules
    // Because we're setting up dependencies, the modules have to exist before we configure them
    Map<TargetKey, AndroidResourceModule> targetToAndroidResourceModule = Maps.newHashMap();
    for (AndroidResourceModule androidResourceModule :
        syncData.importResult.androidResourceModules) {
      targetToAndroidResourceModule.put(androidResourceModule.targetKey, androidResourceModule);
      String moduleName = moduleNameForAndroidModule(androidResourceModule.targetKey);
      Module module = moduleEditor.createModule(moduleName, StdModuleTypes.JAVA);
      TargetIdeInfo target = blazeProjectData.getTargetMap().get(androidResourceModule.targetKey);
      AndroidFacetModuleCustomizer.createAndroidFacet(
          module, target != null && target.kindIsOneOf(Kind.ANDROID_BINARY, Kind.ANDROID_TEST));
    }

    // Configure android resource modules
    int totalOrderEntries = 0;
    Set<File> existingRoots = Sets.newHashSet();
    for (AndroidResourceModule androidResourceModule : targetToAndroidResourceModule.values()) {
      TargetIdeInfo target = blazeProjectData.getTargetMap().get(androidResourceModule.targetKey);
      AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
      assert androidIdeInfo != null;

      String moduleName = moduleNameForAndroidModule(target.getKey());
      Module module = moduleEditor.findModule(moduleName);
      assert module != null;
      ModifiableRootModel modifiableRootModel = moduleEditor.editModule(module);
      LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);

      Collection<File> resources =
          blazeProjectData.getArtifactLocationDecoder().decodeAll(androidResourceModule.resources);
      if (useCyclicResourceDependency.getValue()) {
        // Remove existing resource roots to silence the duplicate content root error.
        // We can only do this if we have cyclic resource dependencies, since otherwise we risk
        // breaking dependencies within this resource module.
        resources.removeAll(existingRoots);
        existingRoots.addAll(resources);
      }
      ResourceModuleContentRootCustomizer.setupContentRoots(modifiableRootModel, resources);

      if (useCyclicResourceDependency.getValue()) {
        modifiableRootModel.addModuleOrderEntry(workspaceModule);
        ++totalOrderEntries;
      } else {
        for (TargetKey resourceDependency : androidResourceModule.transitiveResourceDependencies) {
          if (!targetToAndroidResourceModule.containsKey(resourceDependency)) {
            continue;
          }
          String dependencyModuleName = moduleNameForAndroidModule(resourceDependency);
          Module dependency = moduleEditor.findModule(dependencyModuleName);
          if (dependency == null) {
            continue;
          }
          modifiableRootModel.addModuleOrderEntry(dependency);
          ++totalOrderEntries;
        }
      }

      if (createLooksLikeAarLibrary.getValue()) {
        for (String libraryName : androidResourceModule.resourceLibraryKeys) {
          modifiableRootModel.addLibraryEntry(libraryTable.getLibraryByName(libraryName));
        }
      } else if (useLibraryResourcesModule.getValue()) {
        Module libraryResourcesModule =
            moduleEditor.createModule(LIBRARY_RESOURCES_MODULE_NAME, StdModuleTypes.JAVA);
        AndroidFacetModuleCustomizer.createAndroidFacet(libraryResourcesModule, false);
        modifiableRootModel.addModuleOrderEntry(libraryResourcesModule);
        ++totalOrderEntries;
      }
      // Add a dependency from the workspace to the resource module
      ModuleOrderEntry orderEntry = workspaceModifiableModel.addModuleOrderEntry(module);
      ++totalOrderEntries;
      if (useCyclicResourceDependency.getValue()) {
        orderEntry.setExported(true);
      }
    }

    List<TargetIdeInfo> runConfigurationTargets =
        getRunConfigurationTargets(
            project, projectViewSet, blazeProjectData, targetToAndroidResourceModule.keySet());
    for (TargetIdeInfo target : runConfigurationTargets) {
      TargetKey targetKey = target.getKey();
      String moduleName = moduleNameForAndroidModule(targetKey);
      Module module = moduleEditor.createModule(moduleName, StdModuleTypes.JAVA);
      AndroidFacetModuleCustomizer.createAndroidFacet(module, true);
    }

    int whitelistedGenResources =
        projectViewSet.listItems(GeneratedAndroidResourcesSection.KEY).size();
    context.output(
        PrintOutput.log(
            String.format(
                "Android resource module count: %d, run config modules: %d, order entries: %d, "
                    + "generated resources: %d",
                syncData.importResult.androidResourceModules.size(),
                runConfigurationTargets.size(),
                totalOrderEntries,
                whitelistedGenResources)));
  }

  // Collect potential android run configuration targets
  private static List<TargetIdeInfo> getRunConfigurationTargets(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Set<TargetKey> androidResourceModules) {
    List<TargetIdeInfo> result = Lists.newArrayList();
    Set<Label> runConfigurationModuleTargets = Sets.newHashSet();

    // Get all explicitly mentioned targets
    // Doing this now will cut down on root changes later
    for (TargetExpression targetExpression : projectViewSet.listItems(TargetSection.KEY)) {
      if (!(targetExpression instanceof Label)) {
        continue;
      }
      Label label = (Label) targetExpression;
      runConfigurationModuleTargets.add(label);
    }
    // Get any pre-existing targets
    for (RunConfiguration runConfiguration :
        RunManager.getInstance(project).getAllConfigurationsList()) {
      BlazeAndroidRunConfigurationHandler handler =
          BlazeAndroidRunConfigurationHandler.getHandlerFrom(runConfiguration);
      if (handler == null) {
        continue;
      }
      runConfigurationModuleTargets.add(handler.getLabel());
    }

    for (Label label : runConfigurationModuleTargets) {
      TargetKey targetKey = TargetKey.forPlainTarget(label);
      // If it's a resource module, it will already have been created
      if (androidResourceModules.contains(targetKey)) {
        continue;
      }
      // Ensure the label is a supported android rule that exists
      TargetIdeInfo target = blazeProjectData.getTargetMap().get(targetKey);
      if (target == null) {
        continue;
      }
      if (!target.kindIsOneOf(Kind.ANDROID_BINARY, Kind.ANDROID_TEST)) {
        continue;
      }
      result.add(target);
    }
    return result;
  }

  /** Ensures a suitable module exists for the given android target. */
  @Nullable
  public static Module ensureRunConfigurationModule(Project project, Label label) {
    TargetKey targetKey = TargetKey.forPlainTarget(label);
    String moduleName = moduleNameForAndroidModule(targetKey);
    Module module = ModuleFinder.getInstance(project).findModuleByName(moduleName);
    if (module != null) {
      return module;
    }

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    AndroidSdkPlatform androidSdkPlatform = SdkUtil.getAndroidSdkPlatform(blazeProjectData);
    if (androidSdkPlatform == null) {
      return null;
    }
    TargetIdeInfo target = blazeProjectData.getTargetMap().get(targetKey);
    if (target == null) {
      return null;
    }
    if (target.getAndroidIdeInfo() == null) {
      return null;
    }
    // We can't run a write action outside the dispatch thread, and can't
    // invokeAndWait it because the caller may have a read action.
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      return null;
    }

    BlazeSyncPlugin.ModuleEditor moduleEditor =
        ModuleEditorProvider.getInstance()
            .getModuleEditor(
                project, BlazeImportSettingsManager.getInstance(project).getImportSettings());
    Module newModule = moduleEditor.createModule(moduleName, StdModuleTypes.JAVA);
    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              AndroidFacetModuleCustomizer.createAndroidFacet(newModule, true);
              moduleEditor.commit();
            });
    File moduleDirectory =
        moduleDirectoryForAndroidTarget(WorkspaceRoot.fromProject(project), target);
    updateModuleFacetInMemoryState(
        project,
        androidSdkPlatform,
        newModule,
        moduleDirectory,
        manifestFileForAndroidTarget(
            blazeProjectData.getArtifactLocationDecoder(),
            target.getAndroidIdeInfo(),
            moduleDirectory),
        target.getAndroidIdeInfo().getResourceJavaPackage(),
        ImmutableList.of());
    return newModule;
  }

  public static String moduleNameForAndroidModule(TargetKey targetKey) {
    return targetKey
        .toString()
        .substring(2) // Skip initial "//"
        .replace('/', '.')
        .replace(':', '.');
  }

  public static void updateInMemoryState(
      Project project,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Module workspaceModule,
      boolean isAndroidWorkspace) {
    BlazeLightResourceClassService.Builder rClassBuilder =
        new BlazeLightResourceClassService.Builder(project);
    AndroidResourceModuleRegistry registry = AndroidResourceModuleRegistry.getInstance(project);
    registry.clear();
    if (isAndroidWorkspace) {
      updateInMemoryState(
          project,
          workspaceRoot,
          projectViewSet,
          blazeProjectData,
          workspaceModule,
          registry,
          rClassBuilder);
    }
    BlazeLightResourceClassService.getInstance(project).installRClasses(rClassBuilder);
  }

  private static void updateInMemoryState(
      Project project,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Module workspaceModule,
      AndroidResourceModuleRegistry registry,
      BlazeLightResourceClassService.Builder rClassBuilder) {
    BlazeAndroidSyncData syncData = blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class);
    if (syncData == null) {
      return;
    }
    AndroidSdkPlatform androidSdkPlatform = syncData.androidSdkPlatform;
    if (androidSdkPlatform == null) {
      return;
    }

    updateWorkspaceModuleFacetInMemoryState(
        project, workspaceRoot, workspaceModule, androidSdkPlatform);

    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.getArtifactLocationDecoder();
    ModuleFinder moduleFinder = ModuleFinder.getInstance(project);

    if (!createLooksLikeAarLibrary.getValue()) {
      Module libraryResourcesModule = moduleFinder.findModuleByName(LIBRARY_RESOURCES_MODULE_NAME);
      if (libraryResourcesModule != null) {
        updateLibraryResourcesModuleFacetInMemoryState(
            project,
            workspaceRoot,
            libraryResourcesModule,
            androidSdkPlatform,
            syncData.importResult.resourceLibraries == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(
                    syncData.importResult.resourceLibraries.values().stream()
                        .map(library -> artifactLocationDecoder.decode(library.root))
                        .collect(Collectors.toList())));
      } else if (useLibraryResourcesModule.getValue()) {
        logger.warn("Library resources module missing.");
      }
    }

    for (AndroidResourceModule androidResourceModule :
        syncData.importResult.androidResourceModules) {
      TargetIdeInfo target = blazeProjectData.getTargetMap().get(androidResourceModule.targetKey);
      String moduleName = moduleNameForAndroidModule(target.getKey());
      Module module = moduleFinder.findModuleByName(moduleName);
      if (module == null) {
        logger.warn("No module found for resource target: " + target.getKey());
        continue;
      }
      registry.put(module, androidResourceModule);

      AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
      assert androidIdeInfo != null;

      List<File> resources =
          artifactLocationDecoder.decodeAll(
              useLibraryResourcesModule.getValue()
                  ? androidResourceModule.resources
                  : androidResourceModule.transitiveResources);
      updateModuleFacetInMemoryState(
          project,
          androidSdkPlatform,
          module,
          moduleDirectoryForAndroidTarget(workspaceRoot, target),
          manifestFileForAndroidTarget(
              artifactLocationDecoder,
              androidIdeInfo,
              moduleDirectoryForAndroidTarget(workspaceRoot, target)),
          androidIdeInfo.getResourceJavaPackage(),
          resources);
      rClassBuilder.addRClass(androidIdeInfo.getResourceJavaPackage(), module);
    }

    Set<TargetKey> androidResourceModules =
        syncData.importResult.androidResourceModules.stream()
            .map(androidResourceModule -> androidResourceModule.targetKey)
            .collect(toSet());
    List<TargetIdeInfo> runConfigurationTargets =
        getRunConfigurationTargets(
            project, projectViewSet, blazeProjectData, androidResourceModules);
    for (TargetIdeInfo target : runConfigurationTargets) {
      String moduleName = moduleNameForAndroidModule(target.getKey());
      Module module = moduleFinder.findModuleByName(moduleName);
      if (module == null) {
        logger.warn("No module found for run configuration target: " + target.getKey());
        continue;
      }
      AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
      assert androidIdeInfo != null;
      updateModuleFacetInMemoryState(
          project,
          androidSdkPlatform,
          module,
          moduleDirectoryForAndroidTarget(workspaceRoot, target),
          manifestFileForAndroidTarget(
              artifactLocationDecoder,
              androidIdeInfo,
              moduleDirectoryForAndroidTarget(workspaceRoot, target)),
          androidIdeInfo.getResourceJavaPackage(),
          ImmutableList.of());
    }
  }

  private static File moduleDirectoryForAndroidTarget(
      WorkspaceRoot workspaceRoot, TargetIdeInfo target) {
    return workspaceRoot.fileForPath(target.getKey().getLabel().blazePackage());
  }

  private static File manifestFileForAndroidTarget(
      ArtifactLocationDecoder artifactLocationDecoder,
      AndroidIdeInfo androidIdeInfo,
      File moduleDirectory) {
    ArtifactLocation manifestArtifactLocation = androidIdeInfo.getManifest();
    return manifestArtifactLocation != null
        ? artifactLocationDecoder.decode(manifestArtifactLocation)
        : new File(moduleDirectory, "AndroidManifest.xml");
  }

  /** Updates the shared workspace module with android info. */
  private static void updateWorkspaceModuleFacetInMemoryState(
      Project project,
      WorkspaceRoot workspaceRoot,
      Module workspaceModule,
      AndroidSdkPlatform androidSdkPlatform) {
    File moduleDirectory = workspaceRoot.directory();
    File manifest = new File(workspaceRoot.directory(), "AndroidManifest.xml");
    String resourceJavaPackage = ":workspace";
    updateModuleFacetInMemoryState(
        project,
        androidSdkPlatform,
        workspaceModule,
        moduleDirectory,
        manifest,
        resourceJavaPackage,
        ImmutableList.of());
  }

  /**
   * Updates the library resources module with android info only when user do not want to create aar
   * library for module.
   */
  private static void updateLibraryResourcesModuleFacetInMemoryState(
      Project project,
      WorkspaceRoot workspaceRoot,
      Module workspaceModule,
      AndroidSdkPlatform androidSdkPlatform,
      Collection<File> resources) {
    File moduleDirectory = workspaceRoot.directory();
    File manifest = new File(workspaceRoot.directory(), "AndroidManifest.xml");
    String resourceJavaPackage = ":android-resources";
    updateModuleFacetInMemoryState(
        project,
        androidSdkPlatform,
        workspaceModule,
        moduleDirectory,
        manifest,
        resourceJavaPackage,
        resources);
  }

  private static void updateModuleFacetInMemoryState(
      Project project,
      AndroidSdkPlatform androidSdkPlatform,
      Module module,
      File moduleDirectory,
      File manifest,
      String resourceJavaPackage,
      Collection<File> resources) {
    SourceSet sourceSet =
        new SourceSet(
            ImmutableMap.of(
                AndroidPathType.RES,
                PathStringUtil.toPathStrings(resources),
                AndroidPathType.MANIFEST,
                Collections.singletonList(PathStringUtil.toPathString(manifest))));
    SourceProvider sourceProvider =
        SourceProviderUtil.toSourceProvider(sourceSet, module.getName());

    BlazeAndroidModel androidModel =
        new BlazeAndroidModel(
            project,
            module,
            moduleDirectory,
            sourceProvider,
            manifest,
            resourceJavaPackage,
            androidSdkPlatform.androidMinSdkLevel);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      facet.getConfiguration().setModel(androidModel);
    }
  }
}

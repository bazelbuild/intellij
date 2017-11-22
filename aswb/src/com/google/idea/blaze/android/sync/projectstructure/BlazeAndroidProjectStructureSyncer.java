/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync.projectstructure;

import static java.util.stream.Collectors.toSet;

import com.android.builder.model.SourceProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.idea.blaze.android.projectview.GeneratedAndroidResourcesSection;
import com.google.idea.blaze.android.resources.LightResourceClassService;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.android.sync.model.idea.BlazeAndroidModel;
import com.google.idea.blaze.android.sync.model.idea.SourceProviderImpl;
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
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Updates the IDE's project structure. */
public class BlazeAndroidProjectStructureSyncer {
  private static final Logger logger = Logger.getInstance(BlazeAndroidProjectStructureSyncer.class);
  private static final BoolExperiment cyclicResourceDependency =
      new BoolExperiment("cyclic.resource.dependency", true);

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

    BlazeAndroidSyncData syncData = blazeProjectData.syncState.get(BlazeAndroidSyncData.class);
    if (syncData == null) {
      return;
    }
    AndroidSdkPlatform androidSdkPlatform = syncData.androidSdkPlatform;
    if (androidSdkPlatform == null) {
      return;
    }

    // Configure workspace module as an android module
    AndroidFacetModuleCustomizer.createAndroidFacet(workspaceModule);

    // Create android resource modules
    // Because we're setting up dependencies, the modules have to exist before we configure them
    Map<TargetKey, AndroidResourceModule> targetToAndroidResourceModule = Maps.newHashMap();
    for (AndroidResourceModule androidResourceModule :
        syncData.importResult.androidResourceModules) {
      targetToAndroidResourceModule.put(androidResourceModule.targetKey, androidResourceModule);
      String moduleName = moduleNameForAndroidModule(androidResourceModule.targetKey);
      Module module = moduleEditor.createModule(moduleName, StdModuleTypes.JAVA);
      AndroidFacetModuleCustomizer.createAndroidFacet(module);
    }

    // Configure android resource modules
    int totalOrderEntries = 0;
    Set<File> existingRoots = Sets.newHashSet();
    for (AndroidResourceModule androidResourceModule : targetToAndroidResourceModule.values()) {
      TargetIdeInfo target = blazeProjectData.targetMap.get(androidResourceModule.targetKey);
      AndroidIdeInfo androidIdeInfo = target.androidIdeInfo;
      assert androidIdeInfo != null;

      String moduleName = moduleNameForAndroidModule(target.key);
      Module module = moduleEditor.findModule(moduleName);
      assert module != null;
      ModifiableRootModel modifiableRootModel = moduleEditor.editModule(module);

      Collection<File> resources =
          blazeProjectData.artifactLocationDecoder.decodeAll(androidResourceModule.resources);
      if (cyclicResourceDependency.getValue()) {
        // Remove existing resource roots to silence the duplicate content root error.
        // We can only do this if we have cyclic resource dependencies, since otherwise we risk
        // breaking dependencies within this resource module.
        resources.removeAll(existingRoots);
        existingRoots.addAll(resources);
      }
      ResourceModuleContentRootCustomizer.setupContentRoots(modifiableRootModel, resources);

      if (cyclicResourceDependency.getValue()) {
        modifiableRootModel.addModuleOrderEntry(workspaceModule);
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
      // Add a dependency from the workspace to the resource module
      ModuleOrderEntry orderEntry = workspaceModifiableModel.addModuleOrderEntry(module);
      if (cyclicResourceDependency.getValue()) {
        orderEntry.setExported(true);
      }
    }

    List<TargetIdeInfo> runConfigurationTargets =
        getRunConfigurationTargets(
            project, projectViewSet, blazeProjectData, targetToAndroidResourceModule.keySet());
    for (TargetIdeInfo target : runConfigurationTargets) {
      TargetKey targetKey = target.key;
      String moduleName = moduleNameForAndroidModule(targetKey);
      Module module = moduleEditor.createModule(moduleName, StdModuleTypes.JAVA);
      AndroidFacetModuleCustomizer.createAndroidFacet(module);
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
      TargetIdeInfo target = blazeProjectData.targetMap.get(targetKey);
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
    TargetIdeInfo target = blazeProjectData.targetMap.get(targetKey);
    if (target == null) {
      return null;
    }
    if (target.androidIdeInfo == null) {
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
              AndroidFacetModuleCustomizer.createAndroidFacet(newModule);
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
            blazeProjectData.artifactLocationDecoder, target.androidIdeInfo, moduleDirectory),
        target.androidIdeInfo.resourceJavaPackage,
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
    LightResourceClassService.Builder rClassBuilder =
        new LightResourceClassService.Builder(project);
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
    LightResourceClassService.getInstance(project).installRClasses(rClassBuilder);
  }

  private static void updateInMemoryState(
      Project project,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Module workspaceModule,
      AndroidResourceModuleRegistry registry,
      LightResourceClassService.Builder rClassBuilder) {
    BlazeAndroidSyncData syncData = blazeProjectData.syncState.get(BlazeAndroidSyncData.class);
    if (syncData == null) {
      return;
    }
    AndroidSdkPlatform androidSdkPlatform = syncData.androidSdkPlatform;
    if (androidSdkPlatform == null) {
      return;
    }

    updateWorkspaceModuleFacetInMemoryState(
        project, workspaceRoot, workspaceModule, androidSdkPlatform);

    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.artifactLocationDecoder;
    ModuleFinder moduleFinder = ModuleFinder.getInstance(project);
    for (AndroidResourceModule androidResourceModule :
        syncData.importResult.androidResourceModules) {
      TargetIdeInfo target = blazeProjectData.targetMap.get(androidResourceModule.targetKey);
      String moduleName = moduleNameForAndroidModule(target.key);
      Module module = moduleFinder.findModuleByName(moduleName);
      if (module == null) {
        logger.warn("No module found for resource target: " + target.key);
        continue;
      }
      registry.put(module, androidResourceModule);

      AndroidIdeInfo androidIdeInfo = target.androidIdeInfo;
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
          androidIdeInfo.resourceJavaPackage,
          artifactLocationDecoder.decodeAll(androidResourceModule.transitiveResources));
      rClassBuilder.addRClass(androidIdeInfo.resourceJavaPackage, module);
    }

    Set<TargetKey> androidResourceModules =
        syncData
            .importResult
            .androidResourceModules
            .stream()
            .map(androidResourceModule -> androidResourceModule.targetKey)
            .collect(toSet());
    List<TargetIdeInfo> runConfigurationTargets =
        getRunConfigurationTargets(
            project, projectViewSet, blazeProjectData, androidResourceModules);
    for (TargetIdeInfo target : runConfigurationTargets) {
      String moduleName = moduleNameForAndroidModule(target.key);
      Module module = moduleFinder.findModuleByName(moduleName);
      if (module == null) {
        logger.warn("No module found for run configuration target: " + target.key);
        continue;
      }
      AndroidIdeInfo androidIdeInfo = target.androidIdeInfo;
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
          androidIdeInfo.resourceJavaPackage,
          ImmutableList.of());
    }
  }

  private static File moduleDirectoryForAndroidTarget(
      WorkspaceRoot workspaceRoot, TargetIdeInfo target) {
    return workspaceRoot.fileForPath(target.key.label.blazePackage());
  }

  private static File manifestFileForAndroidTarget(
      ArtifactLocationDecoder artifactLocationDecoder,
      AndroidIdeInfo androidIdeInfo,
      File moduleDirectory) {
    ArtifactLocation manifestArtifactLocation = androidIdeInfo.manifest;
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
    ImmutableList<File> transitiveResources = ImmutableList.of();
    updateModuleFacetInMemoryState(
        project,
        androidSdkPlatform,
        workspaceModule,
        moduleDirectory,
        manifest,
        resourceJavaPackage,
        transitiveResources);
  }

  private static void updateModuleFacetInMemoryState(
      Project project,
      AndroidSdkPlatform androidSdkPlatform,
      Module module,
      File moduleDirectory,
      File manifest,
      String resourceJavaPackage,
      Collection<File> transitiveResources) {
    SourceProvider sourceProvider =
        new SourceProviderImpl(module.getName(), manifest, transitiveResources);
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
      facet.setAndroidModel(androidModel);
    }
  }
}

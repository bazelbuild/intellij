/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.util.PathString;
import com.android.projectmodel.ExternalLibrary;
import com.android.projectmodel.Library;
import com.android.projectmodel.SelectiveResourceFolder;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.CapabilityNotSupported;
import com.android.tools.idea.projectsystem.CapabilityStatus;
import com.android.tools.idea.projectsystem.DependencyManagementException;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.projectsystem.SampleDataDirectoryProvider;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.npw.project.BlazeAndroidModuleTemplate;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.android.sync.model.BlazeResourceLibrary;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/** Blaze implementation of {@link AndroidModuleSystem}. */
public class BlazeModuleSystem implements AndroidModuleSystem, BlazeClassFileFinder {
  private Module module;
  private SampleDataDirectoryProvider sampleDataDirectoryProvider;
  private BlazeClassFileFinder classFileFinder;

  public BlazeModuleSystem(Module module) {
    this.module = module;
    classFileFinder = BlazeClassFileFinderFactory.createBlazeClassFileFinder(module);
    sampleDataDirectoryProvider = new BlazeSampleDataDirectoryProvider(module);
  }

  @Override
  public boolean shouldSkipResourceRegistration() {
    return classFileFinder.shouldSkipResourceRegistration();
  }

  @Override
  @Nullable
  public VirtualFile findClassFile(String fqcn) {
    return classFileFinder.findClassFile(fqcn);
  }

  @Override
  @Nullable
  public PathString getOrCreateSampleDataDirectory() throws IOException {
    return sampleDataDirectoryProvider.getOrCreateSampleDataDirectory();
  }

  @Override
  @Nullable
  public PathString getSampleDataDirectory() {
    return sampleDataDirectoryProvider.getSampleDataDirectory();
  }

  @Override
  public CapabilityStatus canGeneratePngFromVectorGraphics() {
    // We're currently unsure of the state of the Blaze support, so we report that it's unsupported.
    // TODO: Change this to "supported" when and if we can confirm that Blaze supports it
    return new CapabilityNotSupported(
        "<html><p>Blaze does not support generation of PNG images from vector assets. "
            + "Vector asset support requires a SDK version of at least 21.</p></html>",
        "Vector Assets Not Supported");
  }

  @Override
  public CapabilityStatus getInstantRunSupport() {
    return new CapabilityNotSupported();
  }

  @Override
  public List<NamedModuleTemplate> getModuleTemplates(
      @javax.annotation.Nullable VirtualFile targetDirectory) {
    return BlazeAndroidModuleTemplate.getTemplates(module, targetDirectory);
  }

  @Override
  public void registerDependency(GradleCoordinate coordinate) {
    Project project = module.getProject();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return;
    }
    AndroidResourceModuleRegistry registry = AndroidResourceModuleRegistry.getInstance(project);
    TargetIdeInfo targetIdeInfo =
        blazeProjectData.getTargetMap().get(registry.getTargetKey(module));
    if (targetIdeInfo == null || targetIdeInfo.getBuildFile() == null) {
      return;
    }

    // TODO: automagically edit deps instead of just opening the BUILD file?
    // Need to translate Gradle coordinates into blaze targets.
    // Will probably need to hardcode for each dependency.
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    PsiElement buildTargetPsi =
        BuildReferenceManager.getInstance(project).resolveLabel(targetIdeInfo.getKey().getLabel());
    if (buildTargetPsi != null) {
      // If we can find a PSI for the target,
      // then we can jump straight to the target in the build file.
      fileEditorManager.openTextEditor(
          new OpenFileDescriptor(
              project,
              buildTargetPsi.getContainingFile().getVirtualFile(),
              buildTargetPsi.getTextOffset()),
          true);
    } else {
      // If not, just the build file is good enough.
      File buildIoFile =
          blazeProjectData.getArtifactLocationDecoder().decode(targetIdeInfo.getBuildFile());
      VirtualFile buildVirtualFile = VfsUtils.resolveVirtualFile(buildIoFile);
      if (buildVirtualFile != null) {
        fileEditorManager.openFile(buildVirtualFile, true);
      }
    }
  }

  @Nullable
  @Override
  public GradleCoordinate getRegisteredDependency(GradleCoordinate coordinate)
      throws DependencyManagementException {

    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(module.getProject()).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }

    TargetKey resourceModuleKey =
        AndroidResourceModuleRegistry.getInstance(module.getProject()).getTargetKey(module);
    if (resourceModuleKey == null) {
      // TODO: decide what constitutes a registered dependency for the .workspace module
      return null;
    }

    TargetIdeInfo resourceModuleTarget = projectData.getTargetMap().get(resourceModuleKey);
    if (resourceModuleTarget == null) {
      return null;
    }

    Set<TargetKey> firstLevelDeps =
        resourceModuleTarget.getDependencies().stream()
            .map(Dependency::getTargetKey)
            .collect(Collectors.toSet());

    return locateArtifactsFor(coordinate).anyMatch(firstLevelDeps::contains) ? coordinate : null;
  }

  @Nullable
  @Override
  public GradleCoordinate getResolvedDependency(GradleCoordinate coordinate)
      throws DependencyManagementException {

    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(module.getProject()).getBlazeProjectData();

    if (projectData == null) {
      return null;
    }

    TargetKey resourceModuleKey =
        AndroidResourceModuleRegistry.getInstance(module.getProject()).getTargetKey(module);
    TransitiveDependencyMap transitiveDependencyMap =
        TransitiveDependencyMap.getInstance(module.getProject());

    boolean moduleHasDependency =
        locateArtifactsFor(coordinate)
            .anyMatch(
                artifactKey ->
                    resourceModuleKey == null
                        // If this isn't a resource module, then it must be the .workspace module,
                        // which
                        // transitively depends on everything in the project. So we can just check
                        // to see
                        // if the artifact is included in the project by checking the keys of the
                        // target map.
                        ? projectData.getTargetMap().contains(artifactKey)
                        // Otherwise, we actually need to search the transitive dependencies of the
                        // resource module.
                        : transitiveDependencyMap.hasTransitiveDependency(
                            resourceModuleKey, artifactKey));

    return moduleHasDependency ? coordinate : null;
  }

  private Stream<TargetKey> locateArtifactsFor(GradleCoordinate coordinate) {
    // External dependencies can be imported into the project via many routes (e.g. maven_jar,
    // local_repository, custom repo paths, etc). Within the project these dependencies are all
    // referenced by their TargetKey. Here we use a locator to convert coordinates to TargetKey
    // labels in order to find them.
    return MavenArtifactLocator.forBuildSystem(Blaze.getBuildSystem(module.getProject())).stream()
        .map(locator -> locator.labelFor(coordinate))
        .filter(Objects::nonNull)
        .map(TargetKey::forPlainTarget);
  }

  @Override
  public Collection<Library> getResolvedDependentLibraries() {
    Project project = module.getProject();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (blazeProjectData == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<Library> libraries = ImmutableList.builder();
    ArtifactLocationDecoder decoder = blazeProjectData.getArtifactLocationDecoder();
    AndroidResourceModuleRegistry registry = AndroidResourceModuleRegistry.getInstance(project);
    TargetIdeInfo target = blazeProjectData.getTargetMap().get(registry.getTargetKey(module));

    if (BlazeDataStorage.WORKSPACE_MODULE_NAME.equals(module.getName())) {
      for (BlazeLibrary library :
          BlazeLibraryCollector.getLibraries(
              ProjectViewManager.getInstance(project).getProjectViewSet(), blazeProjectData)) {
        if (library instanceof AarLibrary) {
          libraries.add(toExternalLibrary((AarLibrary) library, decoder, project));
        } else if (library instanceof BlazeResourceLibrary) {
          libraries.add(toExternalLibrary((BlazeResourceLibrary) library, decoder));
        } else if (library instanceof BlazeJarLibrary) {
          libraries.add(toExternalLibrary((BlazeJarLibrary) library, decoder));
        }
      }
      return libraries.build();
    }
    if (target == null) {
      // this can happen if the module points to the <android-resources>, <project-data-dir>
      // <project-data-dir> does not contain any resource
      // <android-resources> contains all external resources as module's local resources, so there's
      // no dependent libraries
      return ImmutableList.of();
    }

    BlazeAndroidSyncData androidSyncData =
        blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class);
    for (String libraryKey : registry.get(module).resourceLibraryKeys) {
      if (androidSyncData.importResult.resourceLibraries.containsKey(libraryKey)) {
        libraries.add(
            toExternalLibrary(
                androidSyncData.importResult.resourceLibraries.get(libraryKey), decoder));
      }
      if (androidSyncData.importResult.aarLibraries.containsKey(libraryKey)) {
        libraries.add(
            toExternalLibrary(
                androidSyncData.importResult.aarLibraries.get(libraryKey), decoder, project));
      }
    }
    return libraries.build();
  }

  private ExternalLibrary toExternalLibrary(
      BlazeResourceLibrary library, ArtifactLocationDecoder decoder) {
    PathString resFolder = new PathString(decoder.decode(library.root));
    return new ExternalLibrary(library.key.toString())
        .withManifestFile(
            library.manifest == null ? null : new PathString(decoder.decode(library.manifest)))
        .withResFolder(
            new SelectiveResourceFolder(
                resFolder,
                library.resources.stream()
                    .map(relativePath -> resFolder.resolve(relativePath))
                    .collect(Collectors.toList())));
  }

  private ExternalLibrary toExternalLibrary(
      AarLibrary library, ArtifactLocationDecoder decoder, Project project) {
    PathString aarFile = new PathString(decoder.decode(library.aarArtifact));
    PathString resFolder = library.getResFolder(project);
    return new ExternalLibrary(library.key.toString())
        .withLocation(aarFile)
        .withManifestFile(
            resFolder == null ? null : resFolder.getParentOrRoot().resolve("AndroidManifest.xml"))
        .withResFolder(resFolder == null ? null : new SelectiveResourceFolder(resFolder, null))
        .withSymbolFile(resFolder == null ? null : resFolder.getParentOrRoot().resolve("R.txt"));
  }

  private ExternalLibrary toExternalLibrary(
      BlazeJarLibrary library, ArtifactLocationDecoder decoder) {
    return new ExternalLibrary(library.key.toString())
        .withClassJars(
            ImmutableList.of(
                new PathString(decoder.decode(library.libraryArtifact.jarForIntellijLibrary()))));
  }

  @Nullable
  @Override
  public GradleCoordinate getLatestCompatibleDependency(
      String mavenGroupId, String mavenArtifactId) {
    return null;
  }
}

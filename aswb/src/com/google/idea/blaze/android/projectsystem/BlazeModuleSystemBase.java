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
import com.android.manifmerger.ManifestSystemProperty;
import com.android.projectmodel.ExternalLibrary;
import com.android.projectmodel.SelectiveResourceFolder;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.CapabilityNotSupported;
import com.android.tools.idea.projectsystem.CapabilityStatus;
import com.android.tools.idea.projectsystem.CapabilitySupported;
import com.android.tools.idea.projectsystem.DependencyType;
import com.android.tools.idea.projectsystem.ManifestOverrides;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.projectsystem.SampleDataDirectoryProvider;
import com.android.tools.idea.projectsystem.ScopeType;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.libraries.UnpackedAars;
import com.google.idea.blaze.android.npw.project.BlazeAndroidModuleTemplate;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.targetmaps.ReverseDependencyMap;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
import com.google.idea.blaze.java.libraries.JarCache;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kotlin.Triple;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Blaze implementation of {@link AndroidModuleSystem}. */
@SuppressWarnings("NullableProblems")
abstract class BlazeModuleSystemBase implements AndroidModuleSystem, BlazeClassFileFinder {
  protected static final Logger logger = Logger.getInstance(BlazeModuleSystem.class);
  protected Module module;
  protected final Project project;
  SampleDataDirectoryProvider sampleDataDirectoryProvider;
  BlazeClassFileFinder classFileFinder;
  final boolean isWorkspaceModule;

  /**
   * Experiment used to control whether the calculation of library dependencies for the workspace
   * module ought to be cached.
   *
   * <p>b/171082532: Collecting the Library dependencies for the workspace module using *
   * BlazeLibraryCollector is quite slow and negatively impacts editing performance in 4.1+.
   */
  protected final BoolExperiment cacheLibraryComputation =
      new BoolExperiment("aswb.cache.workspace.libraries", true);

  BlazeModuleSystemBase(Module module) {
    this.module = module;
    this.project = module.getProject();
    classFileFinder = BlazeClassFileFinderFactory.createBlazeClassFileFinder(module);
    sampleDataDirectoryProvider = new BlazeSampleDataDirectoryProvider(module);
    isWorkspaceModule = BlazeDataStorage.WORKSPACE_MODULE_NAME.equals(module.getName());
  }

  @Override
  public Module getModule() {
    return module;
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
    return new CapabilitySupported();
  }

  @Override
  public List<NamedModuleTemplate> getModuleTemplates(@Nullable VirtualFile targetDirectory) {
    return BlazeAndroidModuleTemplate.getTemplates(module, targetDirectory);
  }

  @Override
  public CapabilityStatus canRegisterDependency(DependencyType type) {
    return new CapabilityNotSupported();
  }

  @Override
  public void registerDependency(GradleCoordinate coordinate) {
    registerDependency(coordinate, DependencyType.IMPLEMENTATION);
  }

  @Override
  public void registerDependency(GradleCoordinate coordinate, DependencyType type) {
    if (type != DependencyType.IMPLEMENTATION) {
      throw new UnsupportedOperationException("Unsupported dependency type in Blaze: " + type);
    }
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
      ArtifactLocation buildFile = targetIdeInfo.getBuildFile();
      File buildIoFile =
          Preconditions.checkNotNull(
              OutputArtifactResolver.resolve(
                  project, blazeProjectData.getArtifactLocationDecoder(), buildFile),
              "Fail to find file %s",
              buildFile.getRelativePath());
      VirtualFile buildVirtualFile =
          VfsUtils.resolveVirtualFile(buildIoFile, /* refreshIfNeeded= */ true);
      if (buildVirtualFile != null) {
        fileEditorManager.openFile(buildVirtualFile, true);
      }
    }
  }

  @Nullable
  @Override
  public GradleCoordinate getRegisteredDependency(GradleCoordinate coordinate) {
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
  private TargetKey getResolvedTarget(GradleCoordinate coordinate) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(module.getProject()).getBlazeProjectData();

    if (projectData == null) {
      return null;
    }

    TargetKey resourceModuleKey =
        AndroidResourceModuleRegistry.getInstance(module.getProject()).getTargetKey(module);
    TransitiveDependencyMap transitiveDependencyMap =
        TransitiveDependencyMap.getInstance(module.getProject());

    return locateArtifactsFor(coordinate)
        .filter(
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
                        resourceModuleKey, artifactKey))
        .findFirst()
        .orElse(null);
  }

  @Nullable
  @Override
  public GradleCoordinate getResolvedDependency(GradleCoordinate coordinate) {
    TargetKey target = getResolvedTarget(coordinate);
    return target != null ? coordinate : null;
  }

  /**
   * Returns the absolute path of the dependency, if it exists.
   *
   * @param coordinate external coordinates for the dependency.
   * @return the absolute path of the dependency including workspace root and path.
   */
  @Nullable
  // @Override #api42
  public Path getDependencyPath(GradleCoordinate coordinate) {
    TargetKey target = getResolvedTarget(coordinate);
    if (target != null) {
      return WorkspaceRoot.fromProject(project)
          .fileForPath(target.getLabel().blazePackage())
          .toPath();
    }
    return null;
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

  /**
   * Currently, the ordering of the returned list of modules is meaningless for the Blaze
   * implementation of this API. This may break legacy callers of {@link
   * org.jetbrains.android.util.AndroidUtils#getAndroidResourceDependencies(Module)}, who may be
   * assuming that the facets are returned in overlay order.
   */
  @Override
  public List<Module> getResourceModuleDependencies() {
    AndroidResourceModuleRegistry resourceModuleRegistry =
        AndroidResourceModuleRegistry.getInstance(project);

    if (isWorkspaceModule) {
      // The workspace module depends on every resource module.
      return Arrays.stream(ModuleManager.getInstance(project).getModules())
          .filter(module -> resourceModuleRegistry.get(module) != null)
          .collect(Collectors.toList());
    }
    AndroidResourceModule resourceModule = resourceModuleRegistry.get(module);
    if (resourceModule == null) {
      return ImmutableList.of();
    }

    return resourceModule.transitiveResourceDependencies.stream()
        .map(resourceModuleRegistry::getModule)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  public List<Module> getDirectResourceModuleDependents() {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(module.getProject()).getBlazeProjectData();
    if (projectData == null) {
      return ImmutableList.of();
    }

    AndroidResourceModuleRegistry resourceModuleRegistry =
        AndroidResourceModuleRegistry.getInstance(module.getProject());
    TargetKey resourceModuleKey = resourceModuleRegistry.getTargetKey(module);
    if (resourceModuleKey == null) {
      return ImmutableList.of();
    }

    return ReverseDependencyMap.get(module.getProject()).get(resourceModuleKey).stream()
        .map(projectData.getTargetMap()::get)
        .filter(Objects::nonNull)
        .map(TargetIdeInfo::getKey)
        .map(resourceModuleRegistry::getModule)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Nullable
  static ExternalLibrary toExternalLibrary(
      Project project, AarLibrary library, ArtifactLocationDecoder decoder) {
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);
    File aarFile = unpackedAars.getAarDir(decoder, library);
    if (aarFile == null) {
      logger.warn(
          String.format(
              "Fail to locate AAR file %s. Re-sync the project may solve the problem",
              library.aarArtifact));
      return null;
    }
    File resFolder = unpackedAars.getResourceDirectory(decoder, library);
    PathString resFolderPathString = resFolder == null ? null : new PathString(resFolder);
    return ExternalLibraryCompat.create(library.key.toString())
        .withLocation(new PathString(aarFile))
        .withManifestFile(
            resFolderPathString == null
                ? null
                : resFolderPathString.getParentOrRoot().resolve("AndroidManifest.xml"))
        .withResFolder(
            resFolderPathString == null
                ? null
                : new SelectiveResourceFolder(resFolderPathString, null))
        .withSymbolFile(
            resFolderPathString == null
                ? null
                : resFolderPathString.getParentOrRoot().resolve("R.txt"))
        .withPackageName(library.resourcePackage);
  }

  @Nullable
  static ExternalLibrary toExternalLibrary(
      Project project, BlazeJarLibrary library, ArtifactLocationDecoder decoder) {
    File cachedJar = JarCache.getInstance(project).getCachedJar(decoder, library);
    if (cachedJar == null) {
      logger.warn(
          String.format(
              "Failed to locate jar file %s. Re-sync project may solve the problem", library));
      return null;
    }
    return ExternalLibraryCompat.create(library.toString())
        .withClassJars(ImmutableList.of(new PathString(cachedJar)));
  }

  @Override
  public Triple<List<GradleCoordinate>, List<GradleCoordinate>, String>
      analyzeDependencyCompatibility(List<GradleCoordinate> dependenciesToAdd) {
    return new Triple<>(ImmutableList.of(), dependenciesToAdd, "");
  }

  @Override
  @Nullable
  public String getPackageName() {
    return PackageNameUtils.getPackageName(module);
  }

  @Override
  public ManifestOverrides getManifestOverrides() {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return new ManifestOverrides();
    }
    TargetKey targetKey = AndroidResourceModuleRegistry.getInstance(project).getTargetKey(module);
    if (targetKey == null) {
      return new ManifestOverrides();
    }
    TargetIdeInfo target = projectData.getTargetMap().get(targetKey);

    if (target == null || target.getAndroidIdeInfo() == null) {
      return new ManifestOverrides();
    }
    Map<String, String> manifestValues = target.getAndroidIdeInfo().getManifestValues();
    ImmutableMap.Builder<ManifestSystemProperty, String> directOverrides = ImmutableMap.builder();
    ImmutableMap.Builder<String, String> placeholders = ImmutableMap.builder();
    manifestValues.forEach(
        (key, value) -> processManifestValue(key, value, directOverrides, placeholders));
    return new ManifestOverrides(directOverrides.build(), placeholders.build());
  }

  /**
   * Puts the key-value pair from a target's manifest_values map into either {@code directOverrides}
   * if the key corresponds to a manifest attribute that Blaze allows you to override directly, or
   * {@code placeholders} otherwise.
   *
   * @see <a
   *     href="https://docs.bazel.build/versions/master/be/android.html#android_binary.manifest_values">manifest_values</a>
   */
  private static void processManifestValue(
      String key,
      String value,
      ImmutableMap.Builder<ManifestSystemProperty, String> directOverrides,
      ImmutableMap.Builder<String, String> placeholders) {
    switch (key) {
      case "applicationId":
        directOverrides.put(ManifestSystemProperty.PACKAGE, value);
        break;
      case "versionCode":
        directOverrides.put(ManifestSystemProperty.VERSION_CODE, value);
        break;
      case "versionName":
        directOverrides.put(ManifestSystemProperty.VERSION_NAME, value);
        break;
      case "minSdkVersion":
        directOverrides.put(ManifestSystemProperty.MIN_SDK_VERSION, value);
        break;
      case "targetSdkVersion":
        directOverrides.put(ManifestSystemProperty.TARGET_SDK_VERSION, value);
        break;
      case "maxSdkVersion":
        directOverrides.put(ManifestSystemProperty.MAX_SDK_VERSION, value);
        break;
      case "packageName":
        // From the doc: "packageName will be ignored and will be set from either applicationId if
        // specified or the package in manifest"
        break;
      default:
        placeholders.put(key, value);
    }
  }

  @Override
  public GlobalSearchScope getResolveScope(ScopeType scopeType) {
    // Bazel projects have either a workspace module, or a resource module. In both cases, we just
    // want to ignore the currently specified module level dependencies and use the global set of
    // dependencies. This is because when we artificially split up the Java code (into workspace
    // module) and resources (into a separate module each), we introduce a circular dependency,
    // which essentially means that all modules end up depending on all other modules. If we
    // expressed this circular dependency, IntelliJ blows up due to the large heavily connected
    // dependency graph. Instead, we just redirect the scopes in the few places that we need.
    return ProjectScope.getAllScope(module.getProject());
  }

  @TestOnly
  public static BlazeModuleSystem create(Module module) {
    Preconditions.checkState(ApplicationManager.getApplication().isUnitTestMode());
    return new BlazeModuleSystem(module);
  }

  public static BlazeModuleSystem getInstance(Module module) {
    return ModuleServiceManager.getService(module, BlazeModuleSystem.class);
  }
}

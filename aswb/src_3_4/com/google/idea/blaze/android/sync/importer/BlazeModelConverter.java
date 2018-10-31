/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.importer;

import static com.android.projectmodel.VariantUtil.ARTIFACT_NAME_MAIN;

import com.android.ide.common.gradle.model.ModelCache;
import com.android.ide.common.util.PathMap;
import com.android.ide.common.util.PathString;
import com.android.ide.common.util.PathStringUtil;
import com.android.projectmodel.AndroidModel;
import com.android.projectmodel.AndroidPathType;
import com.android.projectmodel.AndroidSubmodule;
import com.android.projectmodel.ArtifactDependency;
import com.android.projectmodel.Config;
import com.android.projectmodel.ConfigTable;
import com.android.projectmodel.ConfigTableUtil;
import com.android.projectmodel.ConfigUtil;
import com.android.projectmodel.ExternalLibrary;
import com.android.projectmodel.Library;
import com.android.projectmodel.ManifestAttributes;
import com.android.projectmodel.ManifestAttributesUtil;
import com.android.projectmodel.ProjectLibrary;
import com.android.projectmodel.ProjectType;
import com.android.projectmodel.SelectiveResourceFolder;
import com.android.projectmodel.SourceSet;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.projectsystem.TransitiveClosureClassFileFinder;
import com.google.idea.blaze.android.sync.importer.aggregators.TransitiveResourceMap;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/** Converts the data structures produced by blaze sync into a project model data types. */
public final class BlazeModelConverter {
  private final BlazeJavaSyncData javaSyncData;
  private final ModelCache cache = new ModelCache();
  private final BlazeImportInput importInput;
  private final BlazeInfo blazeInfo;
  private final PathMap<BlazeContentEntry> sourceDirectoryForPath;
  private final ManifestAttributes defaultManifestValues;
  private final WhitelistFilter whitelistTester;
  private final Predicate<ArtifactLocation> shouldCreateFakeAarFilter;
  private final List<PathString> generatedPaths;
  private final Map<TargetKey, TargetIdeInfo> targetsToImportAsSource;

  /**
   * Convenience constructor for instantiating this class within {@link
   * BlazeAndroidSyncPlugin#updateInMemoryState}
   */
  public BlazeModelConverter(
      Project project,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData projectData) {
    this(
        projectData.getSyncState().get(BlazeAndroidSyncData.class).androidSdkPlatform,
        projectData.getSyncState().get(BlazeJavaSyncData.class),
        BlazeImportInput.forProject(project, workspaceRoot, projectViewSet, projectData),
        projectData.getBlazeInfo());
  }

  public BlazeModelConverter(
      AndroidSdkPlatform androidSdkPlatform,
      BlazeJavaSyncData javaSyncData,
      BlazeImportInput importInput,
      BlazeInfo blazeInfo) {
    this.javaSyncData = javaSyncData;
    this.importInput = importInput;
    this.blazeInfo = blazeInfo;

    sourceDirectoryForPath =
        BlazeImportUtil.getContentEntryForPath(javaSyncData.getImportResult().contentEntries);

    defaultManifestValues =
        ManifestAttributesUtil.getEmptyManifestAttributes()
            .withVersion(platformToAndroidVersion(androidSdkPlatform));
    whitelistTester =
        new WhitelistFilter(
            BlazeImportUtil.getWhitelistedGenResourcePaths(importInput.projectViewSet));
    shouldCreateFakeAarFilter = BlazeImportUtil.getShouldCreateFakeAarFilter(importInput);
    generatedPaths = getGeneratedPaths(blazeInfo);
    targetsToImportAsSource =
        BlazeImportUtil.getSourceTargetsStream(importInput)
            .collect(Collectors.toMap((info) -> info.getKey(), Function.identity()));
  }

  public AndroidModel createWorkspaceModuleModel() {
    return new AndroidModel(
        targetsToImportAsSource.values().stream()
            .map((TargetIdeInfo target) -> createSubmoduleFor(target.getKey()))
            .collect(Collectors.toList()));
  }

  /**
   * Returns the project type for the given rule kind, or null if it doesn't correspond to an
   * android project type.
   */
  @Nullable
  public static ProjectType ruleKindToProjectType(Kind kind) {
    return ruleTypeToProjectType(kind.ruleType);
  }

  /** Converts a {@link AndroidSdkPlatform} to an {@link AndroidVersion} */
  public static AndroidVersion platformToAndroidVersion(AndroidSdkPlatform platform) {
    return new AndroidVersion(platform.androidMinSdkLevel, platform.androidSdk);
  }

  /**
   * Returns the project type for the given rule type, or null if it doesn't correspond to an
   * android project type.
   */
  @Nullable
  public static ProjectType ruleTypeToProjectType(RuleType type) {
    switch (type) {
      case LIBRARY:
        return ProjectType.LIBRARY;
      case BINARY:
        return ProjectType.APP;
      case TEST:
        return ProjectType.TEST;
      default:
        return null;
    }
  }

  @Nullable
  private PathString manifestFor(TargetIdeInfo target) {
    AndroidIdeInfo ideInfo = target.getAndroidIdeInfo();
    if (ideInfo != null) {
      ArtifactLocation manifest = ideInfo.getManifest();

      if (manifest != null) {
        return decode(manifest);
      }
    }
    return null;
  }

  public PathString decode(ArtifactLocation location) {
    // Decode the location, convert it to a PathString, and de-dupe it.
    return cache.computeIfAbsent(
        location,
        (ArtifactLocation loc) -> new PathString(importInput.artifactLocationDecoder.decode(loc)));
  }

  public List<PathString> decode(Collection<ArtifactLocation> locations) {
    return locations.stream().map(this::decode).collect(Collectors.toList());
  }

  @Nullable
  private PathString getCompileJarLocation(LibraryArtifact artifact) {
    // Prefer the ijar for compilation, but fall back to the class jar if no ijar is available
    ArtifactLocation location = artifact.getInterfaceJar();
    if (location == null) {
      location = artifact.getClassJar();
    }
    if (location == null) {
      return null;
    }
    return decode(location);
  }

  private List<PathString> compileJars(Stream<LibraryArtifact> libraries) {
    return libraries
        .map(this::getCompileJarLocation)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * Creates a fake library for a specific resource location. The library is "fake" in the sense
   * that it doesn't correspond to any target in blaze. However, treating individual folders as
   * libraries allows us to create dependencies to them and avoid including the same resource
   * locations in multiple libraries.
   */
  private ExternalLibrary fakeLibraryFor(
      @Nullable PathString manifest, @Nullable String packageName, PathString resourceLocation) {
    ExternalLibrary toDedupe =
        new ExternalLibrary(resourceLocation.toString())
            .withPackageName(packageName)
            .withManifestFile(manifest)
            .withResFolder(new SelectiveResourceFolder(resourceLocation, null));
    // Check the cache to ensure we don't waste memory by returning two equal library instances.
    return cache.computeIfAbsent(toDedupe, (it) -> it);
  }

  /**
   * Returns the target as a list of {@link ExternalLibrary}.
   *
   * @param resourcesAsFakeLibraries if true, each resource folder will be returned as a separate
   *     library. If false, all resources will be attached as resources of the same library.
   *     Returning false will result in libraries closer to the shape they were declared in.
   *     Returning true prevents the same resource folder from appearing twice in different targets.
   */
  private List<ExternalLibrary> getTargetAsExternalLibraries(TargetIdeInfo info) {
    TargetKey key = info.getKey();
    String libraryAddress = key.toString();

    ImmutableList.Builder<ExternalLibrary> resultBuilder = ImmutableList.builder();
    ExternalLibrary lib = new ExternalLibrary(libraryAddress);

    JavaIdeInfo javaIdeInfo = info.getJavaIdeInfo();
    if (javaIdeInfo != null) {
      // We filter out the resource jar since it contains the R class, which the IDE will generate.
      lib =
          lib.withClassJars(compileJars(TransitiveClosureClassFileFinder.getNonResourceJars(info)));
    }

    AndroidIdeInfo androidIdeInfo = info.getAndroidIdeInfo();
    if (androidIdeInfo != null) {
      @Nullable PathString manifest = manifestFor(info);

      if (androidIdeInfo.getResources() != null && !androidIdeInfo.getResources().isEmpty()) {
        lib = lib.withManifestFile(manifest);
        lib = lib.withPackageName(androidIdeInfo.getResourceJavaPackage());
        for (ArtifactLocation next : androidIdeInfo.getResources()) {
          resultBuilder.add(
              fakeLibraryFor(manifest, androidIdeInfo.getResourceJavaPackage(), decode(next)));
        }
      }
    }

    if (!lib.isEmpty()) {
      resultBuilder.add(lib);
    }

    return resultBuilder.build();
  }

  private ArtifactDependency dependencyFor(Library lib) {
    return cache.computeIfAbsent(new ArtifactDependency(lib), Function.identity());
  }

  @Nullable
  private ArtifactDependency convertTargetToArtifactDependency(TargetKey key) {
    return cache.computeIfAbsent(
        new CacheKey(EntryType.ARTIFACT_DEPENDENCY, key),
        (input) -> {
          ImmutableList.Builder<ArtifactDependency> deps = ImmutableList.builder();

          // If this is a target that we've also imported as a source project, create a
          // ProjectLibrary for it.
          if (targetsToImportAsSource.containsKey(key)) {
            String libraryAddress = key.toString();
            // Blaze projects only ever have one variant and it always has the same name, so the
            // variant
            // name can be hardcoded here.
            return new ArtifactDependency(
                new ProjectLibrary(libraryAddress, libraryAddress, ARTIFACT_NAME_MAIN));
          } else {
            TargetIdeInfo info = importInput.targetMap.get(key);
            if (info == null) {
              // If it is missing from the target map, ignore it as a dependency
              return null;
            }
            List<ExternalLibrary> libs = getTargetAsExternalLibraries(info);

            if (libs.isEmpty()) {
              return null;
            }

            Library mainLibrary = libs.get(0);
            for (int idx = 1; idx < libs.size(); idx++) {
              deps.add(new ArtifactDependency(libs.get(idx)));
            }

            deps.addAll(dependenciesFor(key));

            return new ArtifactDependency(mainLibrary, deps.build(), null, null);
          }
        });
  }

  /** Computes the dependencies for the given target. */
  private List<ArtifactDependency> dependenciesFor(TargetKey target) {
    return cache.computeIfAbsent(
        new CacheKey(EntryType.DEPENDENCY_LIST, target),
        (input) -> {
          TargetIdeInfo info = importInput.targetMap.get(target);
          if (info == null) {
            return Collections.emptyList();
          }

          return TransitiveResourceMap.getResourceDependencies(info).stream()
              .map(this::convertTargetToArtifactDependency)
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
        });
  }

  private AndroidSubmodule createSubmoduleFor(TargetKey key) {
    return cache.computeIfAbsent(
        new CacheKey(EntryType.PROJECT, key),
        (input) -> {
          TargetIdeInfo target = importInput.targetMap.get(key);
          if (target == null) {
            return new AndroidSubmodule(target.getKey().toString(), ProjectType.APP);
          }
          String packageName = BlazeImportUtil.javaResourcePackageFor(target);

          ImmutableList.Builder<ArtifactDependency> fakeLibraries = ImmutableList.builder();
          ImmutableList.Builder<PathString> localResources = ImmutableList.builder();

          BlazeImportUtil.resourcesFor(target)
              .forEach(
                  (it) -> {
                    if (shouldCreateFakeAarFilter.test(it)) {
                      fakeLibraries.add(
                          dependencyFor(fakeLibraryFor(null, packageName, decode(it))));
                    } else if (BlazeAndroidWorkspaceImporter.isSourceOrWhitelistedGenPath(
                        it, whitelistTester)) {
                      localResources.add(decode(it));
                    }
                  });

          SourceSet.Builder builder =
              new SourceSet.Builder()
                  .addIfNotNull(AndroidPathType.MANIFEST, manifestFor(target))
                  .add(AndroidPathType.JAVA, getSourcePathsFor(target.getSources()))
                  .add(AndroidPathType.RES, localResources.build());

          // TODO: It should be possible to extract the manifest_values attribute from
          // android_binary rules by updating the aspect.
          List<ArtifactDependency> dependencies = new ArrayList<>();
          dependencies.addAll(fakeLibraries.build());
          dependencies.addAll(dependenciesFor(target.getKey()));

          Config config =
              ConfigUtil.getEmptyConfig()
                  .withManifestValues(defaultManifestValues)
                  .withCompileDeps(dependencies)
                  .withSources(builder.build());

          ConfigTable configTable = ConfigTableUtil.configTableWith(config);

          // TODO: Right now, we use the root blaze-bin, blaze-gen, etc. for all android submodules.
          // This is sufficient right now but we could
          // do better by locating the most specific subfolder(s) specific to this target.
          return new AndroidSubmodule(
                  target.getKey().toString(), ruleKindToProjectType(target.getKind()))
              .withVariantsGeneratedBy(configTable)
              .withGeneratedPaths(generatedPaths)
              .withPackageName(packageName);
        });
  }

  /** Returns the list of content roots that contain the given sources. */
  private List<PathString> getSourcePathsFor(Collection<ArtifactLocation> toQuery) {
    // If this method turns out to be a bottleneck, we could also consider attaching all content
    // roots to all projects.
    // That would be much faster since there are significantly fewer source roots than source files.
    // However, it would also
    // produce a less specific result in the event that different targets use different source
    // roots.
    return toQuery.stream()
        .map(this::getContentRootFor)
        .filter((it) -> it != null)
        .distinct()
        .collect(Collectors.toList());
  }

  @Nullable
  private PathString getContentRootFor(ArtifactLocation sourceFile) {
    PathString queryString = decode(sourceFile);
    BlazeContentEntry contentEntry = sourceDirectoryForPath.findMostSpecific(queryString);
    if (contentEntry != null) {
      return new PathString(contentEntry.contentRoot);
    }

    return null;
  }

  private static List<PathString> getGeneratedPaths(BlazeInfo blazeInfo) {
    return PathStringUtil.toPathStrings(
        ImmutableList.of(
            blazeInfo.getBlazeBinDirectory(),
            blazeInfo.getGenfilesDirectory(),
            blazeInfo.getOutputBase()));
  }

  private enum EntryType {
    PROJECT,
    ARTIFACT_DEPENDENCY,
    DEPENDENCY_LIST
  }

  private static class CacheKey<T> {
    private EntryType entryType;
    private T key;

    public CacheKey(EntryType entryType, T key) {
      this.entryType = entryType;
      this.key = key;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CacheKey<?> key1 = (CacheKey<?>) o;
      return entryType == key1.entryType && Objects.equals(key, key1.key);
    }

    @Override
    public int hashCode() {
      return Objects.hash(entryType, key);
    }
  }
}

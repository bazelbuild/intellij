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
package com.google.idea.blaze.android.sync.importer;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.idea.blaze.android.sync.importer.aggregators.TransitiveResourceMap;
import com.google.idea.blaze.android.sync.importer.problems.GeneratedResourceWarnings;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.android.sync.model.BlazeResourceLibrary;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidResFolder;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Output;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PerformanceWarning;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Builds a BlazeWorkspace. */
public final class BlazeAndroidWorkspaceImporter {

  private final Project project;
  private final Consumer<Output> context;
  private final BlazeImportInput input;
  // filter used to get all ArtifactLocation that under project view resource directories
  private final Predicate<ArtifactLocation> shouldCreateFakeAar;

  public BlazeAndroidWorkspaceImporter(
      Project project, BlazeContext context, BlazeImportInput input) {

    this(project, BlazeImportUtil.asConsumer(context), input);
  }

  public BlazeAndroidWorkspaceImporter(
      Project project, Consumer<Output> context, BlazeImportInput input) {
    this.context = context;
    this.input = input;
    this.project = project;
    this.shouldCreateFakeAar = BlazeImportUtil.getShouldCreateFakeAarFilter(input);
  }

  public BlazeAndroidImportResult importWorkspace() {
    List<TargetIdeInfo> sourceTargets = BlazeImportUtil.getSourceTargets(input);
    TransitiveResourceMap transitiveResourceMap = new TransitiveResourceMap(input.targetMap);

    LibraryFactory libraries = new LibraryFactory();
    ImmutableSet<String> whitelistedGenResourcePaths =
        BlazeImportUtil.getWhitelistedGenResourcePaths(input.projectViewSet);
    WhitelistFilter tester = new WhitelistFilter(whitelistedGenResourcePaths);

    ImmutableList.Builder<AndroidResourceModule> resourceModules = new ImmutableList.Builder<>();
    for (TargetIdeInfo target : sourceTargets) {
      resourceModules.addAll(addSourceTarget(transitiveResourceMap, target, libraries, tester));
    }

    GeneratedResourceWarnings.submit(
        context::accept,
        project,
        input.projectViewSet,
        input.artifactLocationDecoder,
        tester.testedAgainstWhitelist,
        whitelistedGenResourcePaths);

    ImmutableList<AndroidResourceModule> androidResourceModules =
        buildAndroidResourceModules(resourceModules.build());

    if (!input.createFakeAarLibrariesExperiment) {
      addImportAarLibraries(input.createSourceFilter().getLibraryTargets(), libraries);
      addResourceModuleResourceLibrary(androidResourceModules, libraries);
    }

    return new BlazeAndroidImportResult(
        androidResourceModules,
        libraries.getBlazeResourceLibs(),
        libraries.getAarLibs(),
        BlazeImportUtil.getJavacJar(input.targetMap.targets()));
  }

  /** Returns the list of {@link AndroidResourceModule} to be created. */
  private List<AndroidResourceModule> addSourceTarget(
      TransitiveResourceMap transitiveResourceMap,
      TargetIdeInfo target,
      LibraryFactory libraryFactory,
      Predicate<ArtifactLocation> whitelistTester) {
    ImmutableList.Builder<AndroidResourceModule> result = new ImmutableList.Builder<>();
    AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
    assert androidIdeInfo != null;
    if (shouldGenerateResources(androidIdeInfo)
        && shouldGenerateResourceModule(androidIdeInfo, whitelistTester)) {
      AndroidResourceModule.Builder builder = new AndroidResourceModule.Builder(target.getKey());

      // Only resources within project view directory is local resources of a module.

      // We should include the resource as part of the module if we're not going to create a fake
      // AAR for it and it's either a source
      // target or included in the whitelist.
      for (AndroidResFolder androidResFolder : androidIdeInfo.getResFolders()) {
        ArtifactLocation artifactLocation = androidResFolder.getRoot();
        if (shouldCreateFakeAar.test(artifactLocation)) {
          // we are creating aar libraries, and this resource isn't inside the project view
          // so we can skip adding it to the module
          continue;
        }
        if (isSourceOrWhitelistedGenPath(artifactLocation, whitelistTester)) {
          builder.addResource(artifactLocation);
        }
      }

      TransitiveResourceMap.TransitiveResourceInfo transitiveResourceInfo =
          transitiveResourceMap.get(target.getKey());
      for (AndroidResFolder androidResFolder : transitiveResourceInfo.transitiveResources) {
        ArtifactLocation artifactLocation = androidResFolder.getRoot();
        if (shouldCreateFakeAar.test(artifactLocation)) {
          // All out of project view directory resources will be treated as resources of some aar
          // library.
          // We will create a BlazeResourceLibrary for each resource artifactLocation if it has not
          // yet been created.
          // And this library key will be added to module's library key list.
          // Since {@link TransitiveResourceMap#createForTarget} has already added local resource
          // into transitive resources,
          // transitiveResourceInfo.transitiveResources includes androidIdeInfo.resources.
          // We only need add aarLibraryKey when loop over transitiveResources.
          ArtifactLocation manifest = transitiveResourceMap.getManifestFile(artifactLocation);
          String libraryKey =
              libraryFactory.createBlazeResourceLibrary(
                  androidResFolder, manifest == null ? androidIdeInfo.getManifest() : manifest);
          if (isSourceOrWhitelistedGenPath(artifactLocation, whitelistTester)) {
            builder.addResourceLibraryKey(libraryKey);
          }
        } else {
          // Only resources within project view directory is transitive resources of a module.
          if (isSourceOrWhitelistedGenPath(artifactLocation, whitelistTester)) {
            builder.addTransitiveResource(artifactLocation);
          }
        }
      }
      for (TargetKey resourceDependency : transitiveResourceInfo.transitiveResourceTargets) {
        if (!resourceDependency.equals(target.getKey())) {
          builder.addTransitiveResourceDependency(resourceDependency);
          TargetIdeInfo dependencyTarget = input.targetMap.get(resourceDependency);
          if (input.createFakeAarLibrariesExperiment) {
            String libraryKey = libraryFactory.createAarLibrary(dependencyTarget);
            if (libraryKey != null) {
              ArtifactLocation artifactLocation = dependencyTarget.getAndroidAarIdeInfo().getAar();
              if (isSourceOrWhitelistedGenPath(artifactLocation, whitelistTester)) {
                builder.addResourceLibraryKey(libraryKey);
              }
            }
          }
        }
      }
      result.add(builder.build());
    }
    return result.build();
  }

  public static boolean shouldGenerateResources(AndroidIdeInfo androidIdeInfo) {
    // Generate an android resource module if this rule defines resources
    // We don't want to generate one if this depends on a legacy resource rule through :resources
    // In this case, the resource information is redundantly forwarded to this class for
    // backwards compatibility, but the android_resource rule itself is already generating
    // the android resource module
    return androidIdeInfo.generateResourceClass() && androidIdeInfo.getLegacyResources() == null;
  }

  public static boolean shouldGenerateResourceModule(
      AndroidIdeInfo androidIdeInfo, Predicate<ArtifactLocation> whitelistTester) {
    return androidIdeInfo.getResFolders().stream()
        .map(resource -> resource.getRoot())
        .anyMatch(location -> isSourceOrWhitelistedGenPath(location, whitelistTester));
  }

  public static boolean isSourceOrWhitelistedGenPath(
      ArtifactLocation artifactLocation, Predicate<ArtifactLocation> tester) {
    return artifactLocation.isSource() || tester.test(artifactLocation);
  }

  private void addResourceModuleResourceLibrary(
      Collection<AndroidResourceModule> androidResourceModules, LibraryFactory libraryFactory) {
    Set<ArtifactLocation> result = Sets.newHashSet();
    for (AndroidResourceModule androidResourceModule : androidResourceModules) {
      result.addAll(androidResourceModule.transitiveResources);
    }
    for (AndroidResourceModule androidResourceModule : androidResourceModules) {
      result.removeAll(androidResourceModule.resources);
    }
    for (ArtifactLocation artifactLocation : result) {
      libraryFactory.createBlazeResourceLibrary(artifactLocation, null);
    }
  }

  private void addImportAarLibraries(
      Iterable<TargetIdeInfo> libraryTargets, LibraryFactory libraryFactory) {
    for (TargetIdeInfo target : libraryTargets) {
      libraryFactory.createAarLibrary(target);
    }
  }

  private ImmutableList<AndroidResourceModule> buildAndroidResourceModules(
      ImmutableList<AndroidResourceModule> inputModules) {
    // Filter empty resource modules
    List<AndroidResourceModule> androidResourceModules =
        inputModules.stream()
            .filter(
                androidResourceModule ->
                    !(androidResourceModule.resources.isEmpty()
                        && androidResourceModule.resourceLibraryKeys.isEmpty()))
            .collect(Collectors.toList());

    // Detect, filter, and warn about multiple R classes
    Multimap<String, AndroidResourceModule> javaPackageToResourceModule =
        ArrayListMultimap.create();
    for (AndroidResourceModule androidResourceModule : androidResourceModules) {
      TargetIdeInfo target = input.targetMap.get(androidResourceModule.targetKey);
      AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
      assert androidIdeInfo != null;
      javaPackageToResourceModule.put(
          androidIdeInfo.getResourceJavaPackage(), androidResourceModule);
    }

    List<AndroidResourceModule> result = Lists.newArrayList();
    for (String resourceJavaPackage : javaPackageToResourceModule.keySet()) {
      Collection<AndroidResourceModule> androidResourceModulesWithJavaPackage =
          javaPackageToResourceModule.get(resourceJavaPackage);

      if (androidResourceModulesWithJavaPackage.size() == 1) {
        result.addAll(androidResourceModulesWithJavaPackage);
      } else {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder
            .append("Multiple R classes generated with the same java package ")
            .append(resourceJavaPackage)
            .append(".R: ");
        messageBuilder.append('\n');
        for (AndroidResourceModule androidResourceModule : androidResourceModulesWithJavaPackage) {
          messageBuilder.append("  ").append(androidResourceModule.targetKey).append('\n');
        }
        String message = messageBuilder.toString();
        context.accept(new PerformanceWarning(message));
        context.accept(IssueOutput.warn(message).build());

        result.add(selectBestAndroidResourceModule(androidResourceModulesWithJavaPackage));
      }
    }

    Collections.sort(result, (lhs, rhs) -> lhs.targetKey.compareTo(rhs.targetKey));
    return ImmutableList.copyOf(result);
  }

  private AndroidResourceModule selectBestAndroidResourceModule(
      Collection<AndroidResourceModule> androidResourceModulesWithJavaPackage) {
    return androidResourceModulesWithJavaPackage.stream()
        .max(
            (lhs, rhs) ->
                ComparisonChain.start()
                    .compare(lhs.resources.size(), rhs.resources.size()) // Most resources wins
                    .compare(
                        lhs.transitiveResources.size(),
                        rhs.transitiveResources.size()) // Most transitive resources wins
                    .compare(
                        lhs.resourceLibraryKeys.size(),
                        rhs.resourceLibraryKeys.size()) // Most transitive resources wins
                    .compare(
                        rhs.targetKey.toString().length(),
                        lhs.targetKey
                            .toString()
                            .length()) // Shortest label wins - note lhs, rhs are flipped
                    .result())
        .get();
  }

  private static class LibraryFactory {
    private Map<String, AarLibrary> aarLibraries = new HashMap<>();
    private Map<String, BlazeResourceLibrary.Builder> resourceLibraries = new HashMap<>();

    public ImmutableMap<String, AarLibrary> getAarLibs() {
      return ImmutableMap.copyOf(aarLibraries);
    }

    public ImmutableMap<String, BlazeResourceLibrary> getBlazeResourceLibs() {
      ImmutableMap.Builder<String, BlazeResourceLibrary> builder = ImmutableMap.builder();
      for (Map.Entry<String, BlazeResourceLibrary.Builder> entry : resourceLibraries.entrySet()) {
        builder.put(entry.getKey(), entry.getValue().build());
      }
      return builder.build();
    }

    /**
     * Creates a new BlazeResourceLibrary, or locates an existing one if one already existed for
     * this location. Returns the library key for the library.
     */
    @NotNull
    private String createBlazeResourceLibrary(
        @NotNull ArtifactLocation root,
        @NotNull Set<String> resources,
        @Nullable ArtifactLocation manifestLocation) {
      String libraryKey = BlazeResourceLibrary.libraryNameFromArtifactLocation(root);
      resourceLibraries
          .computeIfAbsent(
              libraryKey,
              key -> new BlazeResourceLibrary.Builder().setRoot(root).setManifest(manifestLocation))
          .addResources(resources);
      return libraryKey;
    }

    @NotNull
    public String createBlazeResourceLibrary(
        @NotNull ArtifactLocation root, @Nullable ArtifactLocation manifestLocation) {
      return createBlazeResourceLibrary(root, ImmutableSet.of(), manifestLocation);
    }

    @NotNull
    public String createBlazeResourceLibrary(
        @NotNull AndroidResFolder androidResFolder, @Nullable ArtifactLocation manifestLocation) {
      return createBlazeResourceLibrary(
          androidResFolder.getRoot(), androidResFolder.getResources(), manifestLocation);
    }

    /**
     * Creates a new Aar repository for this target, if possible, or locates an existing one if one
     * already existed for this location. Returns the key for the library or null if no aar exists
     * for this target.
     */
    @Nullable
    public String createAarLibrary(@NotNull TargetIdeInfo target) {
      // NOTE: we are not doing jdeps optimization, even though we have the jdeps data for the AAR's
      // jar. The aar might still have resources that are used (e.g., @string/foo in .xml), and we
      // don't have the equivalent of jdeps data.
      if (target.getAndroidAarIdeInfo() == null
          || target.getJavaIdeInfo() == null
          || target.getJavaIdeInfo().getJars().isEmpty()) {
        return null;
      }

      String libraryKey =
          LibraryKey.libraryNameFromArtifactLocation(target.getAndroidAarIdeInfo().getAar());
      if (!aarLibraries.containsKey(libraryKey)) {
        // aar_import should only have one jar (a merged jar from the AAR's jars).
        LibraryArtifact firstJar = target.getJavaIdeInfo().getJars().iterator().next();
        aarLibraries.put(
            libraryKey, new AarLibrary(firstJar, target.getAndroidAarIdeInfo().getAar()));
      }
      return libraryKey;
    }
  }
}

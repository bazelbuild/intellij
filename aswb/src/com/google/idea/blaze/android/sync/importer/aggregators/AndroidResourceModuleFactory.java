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
package com.google.idea.blaze.android.sync.importer.aggregators;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.idea.blaze.android.sync.importer.BlazeImportInput;
import com.google.idea.blaze.android.sync.importer.BlazeImportUtil;
import com.google.idea.blaze.android.sync.importer.LibraryFactory;
import com.google.idea.blaze.android.sync.importer.ModuleTester;
import com.google.idea.blaze.android.sync.importer.WhitelistFilter;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidResFolder;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.scope.Output;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PerformanceWarning;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Util class used to generate AndroidResourceModule. */
public class AndroidResourceModuleFactory {
  private final WhitelistFilter whitelistTester;
  // filter used to get all ArtifactLocation that under project view resource directories
  private final Predicate<ArtifactLocation> shouldCreateFakeAar;
  private final TargetMap targetMap;
  private final Consumer<Output> context;

  public AndroidResourceModuleFactory(
      Consumer<Output> context, WhitelistFilter whitelistTester, BlazeImportInput input) {
    this.whitelistTester = whitelistTester;
    this.shouldCreateFakeAar = BlazeImportUtil.getShouldCreateFakeAarFilter(input);
    this.targetMap = input.targetMap;
    this.context = context;
  }

  /**
   * Creates and populates an AndroidResourceModule.Builder for the given target by recursively
   * aggregating the AndroidResourceModule.Builders of its transitive dependencies, or reuses an
   * existing builder if it's cached in resourceModuleBuilderCache.
   */
  public AndroidResourceModule.Builder getOrCreateResourceModuleBuilder(
      TargetIdeInfo target,
      LibraryFactory libraryFactory,
      Map<TargetKey, AndroidResourceModule.Builder> resourceModuleBuilderCache) {
    TargetKey targetKey = target.getKey();
    if (resourceModuleBuilderCache.containsKey(targetKey)) {
      return resourceModuleBuilderCache.get(targetKey);
    }
    AndroidResourceModule.Builder targetResourceModule =
        createResourceModuleBuilder(target, libraryFactory);
    resourceModuleBuilderCache.put(targetKey, targetResourceModule);
    for (TargetKey dep : DependencyUtil.getResourceDependencies(target)) {
      TargetIdeInfo depIdeInfo = targetMap.get(dep);
      reduce(
          targetKey,
          targetResourceModule,
          dep,
          depIdeInfo,
          libraryFactory,
          resourceModuleBuilderCache);
    }
    return targetResourceModule;
  }

  protected void reduce(
      TargetKey targetKey,
      AndroidResourceModule.Builder targetResourceModule,
      TargetKey depKey,
      TargetIdeInfo depIdeInfo,
      LibraryFactory libraryFactory,
      Map<TargetKey, AndroidResourceModule.Builder> resourceModuleBuilderCache) {
    if (depIdeInfo != null) {
      AndroidResourceModule.Builder depTargetResourceModule =
          getOrCreateResourceModuleBuilder(depIdeInfo, libraryFactory, resourceModuleBuilderCache);
      targetResourceModule.addTransitiveResources(depTargetResourceModule.getTransitiveResources());
      targetResourceModule.addResourceLibraryKeys(depTargetResourceModule.getResourceLibraryKeys());
      targetResourceModule.addTransitiveResourceDependencies(
          depTargetResourceModule.getTransitiveResourceDependencies().stream()
              .filter(key -> !targetKey.equals(key))
              .collect(Collectors.toList()));
      if (ModuleTester.shouldCreateModule(depIdeInfo.getAndroidIdeInfo(), whitelistTester)
          && !depKey.equals(targetKey)) {
        targetResourceModule.addTransitiveResourceDependency(depKey);
      }
    }
  }

  /**
   * Helper function to create an AndroidResourceModule.Builder with initial resource information.
   * The builder is incomplete since it doesn't contain information about dependencies. {@link
   * getOrCreateResourceModuleBuilder} will aggregate AndroidResourceModule.Builder over its
   * transitive dependencies.
   */
  protected AndroidResourceModule.Builder createResourceModuleBuilder(
      TargetIdeInfo target, LibraryFactory libraryFactory) {
    AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
    TargetKey targetKey = target.getKey();
    AndroidResourceModule.Builder androidResourceModule =
        new AndroidResourceModule.Builder(targetKey);
    if (androidIdeInfo == null) {
      String libraryKey = libraryFactory.createAarLibrary(target);
      if (libraryKey != null) {
        ArtifactLocation artifactLocation = target.getAndroidAarIdeInfo().getAar();
        if (ModuleTester.isSourceOrWhitelistedGenPath(artifactLocation, whitelistTester)) {
          androidResourceModule.addResourceLibraryKey(libraryKey);
        }
      }
      return androidResourceModule;
    }

    for (AndroidResFolder androidResFolder : androidIdeInfo.getResFolders()) {
      ArtifactLocation artifactLocation = androidResFolder.getRoot();
      if (ModuleTester.isSourceOrWhitelistedGenPath(artifactLocation, whitelistTester)) {
        if (shouldCreateFakeAar.test(artifactLocation)) {
          // we are creating aar libraries, and this resource isn't inside the project view
          // so we can skip adding it to the module
          String libraryKey =
              libraryFactory.createBlazeResourceLibrary(
                  androidResFolder,
                  androidIdeInfo.getManifest(),
                  target.getBuildFile().getRelativePath());
          androidResourceModule.addResourceLibraryKey(libraryKey);
        } else {
          if (ModuleTester.shouldCreateModule(androidIdeInfo, whitelistTester)) {
            androidResourceModule.addResource(artifactLocation);
          }
          androidResourceModule.addTransitiveResource(artifactLocation);
        }
      }
    }
    return androidResourceModule;
  }

  public ImmutableList<AndroidResourceModule> buildAndroidResourceModules(
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
      TargetIdeInfo target = targetMap.get(androidResourceModule.targetKey);
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
}

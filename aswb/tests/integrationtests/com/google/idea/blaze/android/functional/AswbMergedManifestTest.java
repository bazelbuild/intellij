/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.functional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;
import static junit.framework.Assert.fail;

import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.lint.checks.PermissionHolder;
import com.android.utils.concurrency.AsyncSupplier;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.fixtures.ManifestFixture;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProviderUtil;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that merged manifest calculations are accurate for Blaze Android projects. */
@RunWith(JUnit4.class)
public class AswbMergedManifestTest extends BlazeAndroidIntegrationTestCase {
  private ManifestFixture.Factory manifestFactory;

  @Before
  public void setup() {
    setProjectView(
        "directories:",
        "  java/com/example",
        "targets:",
        "  //java/com/example/...:all",
        "android_sdk_platform: android-27");
    MockSdkUtil.registerSdk(workspace, "27");
    manifestFactory = new ManifestFixture.Factory(getProject(), workspace);
  }

  private Module getModule(String moduleName) {
    Module module = ModuleManager.getInstance(getProject()).findModuleByName(moduleName);
    assertThat(module).isNotNull();
    return module;
  }

  private Set<Module> getModules(String... moduleNames) {
    return Stream.of(moduleNames).map(this::getModule).collect(Collectors.toSet());
  }

  private AndroidFacet getFacet(String moduleName) {
    AndroidFacet facet = AndroidFacet.getInstance(getModule(moduleName));
    assertThat(facet).isNotNull();
    return facet;
  }

  private static void runAndWaitForMergedManifestUpdate(Module module, Runnable toRun)
      throws Exception {
    runAndWaitForMergedManifestUpdates(ImmutableSet.of(module), toRun);
  }

  private static void runAndWaitForMergedManifestUpdates(Set<Module> modules, Runnable toRun)
      throws Exception {
    HashSet<Module> notUpdated = new HashSet<>(modules);
    CountDownLatch manifestsUpdated = new CountDownLatch(modules.size());
    modules.forEach(
        module ->
            MergedManifestManager.setUpdateCallback(
                module,
                () -> {
                  MergedManifestManager.setUpdateCallback(module, null);
                  notUpdated.remove(module);
                  manifestsUpdated.countDown();
                }));
    toRun.run();
    if (manifestsUpdated.await(5, TimeUnit.SECONDS)) {
      return;
    }
    String notUpdatedText =
        notUpdated.stream().map(Module::getName).collect(Collectors.joining(", "));
    fail(
        "Merged manifests for the following modules were not updated as expected: "
            + notUpdatedText);
  }

  @Test
  public void manifestBelongsToResourceModule() {
    ManifestFixture manifest = manifestFactory.fromPackage("com.example.target");
    setTargetMap(android_binary("//java/com/example/target:target").manifest(manifest).res("res"));
    runFullBlazeSync();
    AndroidFacet targetFacet = getFacet("java.com.example.target.target");

    // Verify the mapping from resource module to manifest.
    assertThat(IdeaSourceProviderUtil.getManifestFiles(targetFacet))
        .containsExactly(manifest.file.getVirtualFile());
    // Verify the mapping from manifest back to resource module.
    assertThat(AndroidFacet.getInstance(manifest.file)).isEqualTo(targetFacet);
  }

  @Test
  public void excludesManifestsFromUnrelatedModules() {
    setTargetMap(
        android_binary("//java/com/example/target:target")
            .manifest(
                manifestFactory
                    .fromPackage("com.example.target")
                    .addUsesPermission("android.permission.BLUETOOTH"))
            .res("res")
            .dep("//java/com/example/direct:direct"),
        android_library("//java/com/example/direct:direct")
            .manifest(
                manifestFactory
                    .fromPackage("com.example.direct")
                    .addUsesPermission("android.permission.SEND_SMS"))
            .res("res")
            .dep("//java/com/example/transitive:transitive"),
        android_library("//java/com/example/transitive:transitive")
            .manifest(
                manifestFactory
                    .fromPackage("com.example.transitive")
                    .addUsesPermission("android.permission.INTERNET"))
            .res("res"),
        android_library("//java/com/example/irrelevant:irrelevant")
            .manifest(
                manifestFactory
                    .fromPackage("com.example.irrelevant")
                    .addUsesPermission("android.permission.WRITE_EXTERNAL_STORAGE"))
            .res("res"));
    runFullBlazeSync();

    Module targetModule = getModule("java.com.example.target.target");
    PermissionHolder permissions =
        MergedManifestManager.getSnapshot(targetModule).getPermissionHolder();

    // We should have all the permissions used by the binary and its transitive dependencies...
    assertThat(permissions.hasPermission("android.permission.BLUETOOTH")).isTrue();
    assertThat(permissions.hasPermission("android.permission.SEND_SMS")).isTrue();
    assertThat(permissions.hasPermission("android.permission.INTERNET")).isTrue();
    // ... but nothing from libraries that the binary doesn't depend on
    assertThat(permissions.hasPermission("android.permission.WRITE_EXTERNAL_STORAGE")).isFalse();
  }

  @Ignore("b/135927686")
  @Test
  public void updatesWhenManifestChanges() throws Exception {
    ManifestFixture manifest =
        manifestFactory
            .fromPackage("com.example.target")
            .addUsesPermission("android.permission.SEND_SMS");
    setTargetMap(android_binary("//java/com/example/target:target").manifest(manifest).res("res"));
    runFullBlazeSync();

    Module targetModule = getModule("java.com.example.target.target");
    AsyncSupplier<MergedManifestSnapshot> mergedManifest =
        MergedManifestManager.getMergedManifestSupplier(targetModule);
    PermissionHolder permissions = mergedManifest.get().get().getPermissionHolder();
    assertThat(permissions.hasPermission("android.permission.SEND_SMS")).isTrue();

    runAndWaitForMergedManifestUpdate(
        targetModule, () -> manifest.removeUsesPermission("android.permission.SEND_SMS"));

    permissions = mergedManifest.getNow().getPermissionHolder();
    assertThat(permissions.hasPermission("android.permission.SEND_SMS")).isFalse();
  }

  @Test
  public void updatesWhenDependencyManifestChanges() throws Exception {
    ManifestFixture transitiveDependencyManifest =
        manifestFactory.fromPackage("com.example.transitive");
    setTargetMap(
        android_binary("//java/com/example/topLevelOne:topLevelOne")
            .manifest(manifestFactory.fromPackage("com.example.topLevelOne"))
            .res("res")
            .dep("//java/com/example/transitive:transitive"),
        android_binary("//java/com/example/topLevelTwo:topLevelTwo")
            .manifest(manifestFactory.fromPackage("com.example.topLevelTwo"))
            .res("res")
            .dep("//java/com/example/direct:direct"),
        android_binary("//java/com/example/direct:direct")
            .manifest(manifestFactory.fromPackage("com.example.direct"))
            .res("res")
            .dep("//java/com/example/transitive:transitive"),
        android_library("//java/com/example/transitive:transitive")
            .manifest(transitiveDependencyManifest)
            .res("res"));
    runFullBlazeSync();

    Set<Module> modules =
        getModules(
            "java.com.example.topLevelOne.topLevelOne",
            "java.com.example.topLevelTwo.topLevelTwo",
            "java.com.example.direct.direct",
            "java.com.example.transitive.transitive");

    runAndWaitForMergedManifestUpdates(
        modules,
        () -> transitiveDependencyManifest.addUsesPermission("android.permission.SEND_SMS"));

    // The merged manifest of //java/com/example/transitive:transitive and everything that depends
    // on it
    // should have been automatically updated to include the newly-added permission.
    Set<Module> modulesMissingPermission =
        modules.stream()
            .filter(
                module -> {
                  PermissionHolder permissions =
                      MergedManifestManager.getMergedManifestSupplier(module)
                          .getNow()
                          .getPermissionHolder();
                  return !permissions.hasPermission("android.permission.SEND_SMS");
                })
            .collect(Collectors.toSet());
    String missingPermissionsText =
        "Merged manifests for the following modules are missing the added permission: "
            + modulesMissingPermission.stream()
                .map(Module::getName)
                .collect(Collectors.joining(", "));
    assertWithMessage(missingPermissionsText).that(modulesMissingPermission).isEmpty();
  }

  @Test
  public void updatesWithNavigationChanges() throws Exception {
    setTargetMap(
        android_binary("//java/com/example/target:target")
            .manifest(manifestFactory.fromPackage("com.example.target"))
            .res("res"));
    runFullBlazeSync();

    Module module = getModule("java.com.example.target.target");
    workspace.createDirectory(new WorkspacePath("java/com/example/target/res/navigation"));

    // This will fail if the merged manifest wasn't updated.
    runAndWaitForMergedManifestUpdate(
        module,
        () ->
            workspace.createFile(
                new WorkspacePath("java/com/example/target/res/navigation/nav_graph.xml"),
                "<navigation></navigation>"));
  }

  @Test
  public void getPackageName() throws Exception {
    setTargetMap(
        android_binary("//java/com/example/target:target")
            .manifest(manifestFactory.fromPackage("com.example.target"))
            .res("res"));
    runFullBlazeSync();
    Module module = getModule("java.com.example.target.target");
    AndroidModuleSystem moduleSystem = ProjectSystemUtil.getModuleSystem(module);

    runAndWaitForMergedManifestUpdate(
        module,
        () ->
            // The merged manifest hasn't been calculated yet, so this should parse the primary
            // manifest and kick off the initial merged manifest computation.
            assertThat(moduleSystem.getPackageName()).isEqualTo("com.example.target"));

    // Now that the merged manifest is available, we should use that instead of the primary
    // manifest.
    // TODO(b/128928135): Once manifest overrides are supported for Blaze projects, we should verify
    // that we're
    //  picking up the effective package name from the merged manifest here.
    assertThat(moduleSystem.getPackageName()).isEqualTo("com.example.target");
  }

  @Test
  public void directOverrides() throws Exception {
    setTargetMap(
        android_binary("//java/com/example/target:target")
            .manifest(
                manifestFactory
                    .fromPackage("com.example.target")
                    .setVersionCode(0)
                    .setMinSdkVersion(27)
                    .setTargetSdkVersion(28))
            .manifest_value("applicationId", "com.example.merged")
            .manifest_value("versionCode", "1")
            .manifest_value("minSdkVersion", "28")
            .manifest_value("targetSdkVersion", "29")
            .manifest_value("packageName", "com.example.ignored")
            .res("res"));
    runFullBlazeSync();
    Module module = getModule("java.com.example.target.target");

    MergedManifestSnapshot manifest =
        MergedManifestManager.getMergedManifest(module).get(5, TimeUnit.SECONDS);
    assertThat(manifest.getApplicationId()).isEqualTo("com.example.merged");
    assertThat(manifest.getVersionCode()).isEqualTo(1);
    assertThat(manifest.getMinSdkVersion().getApiLevel()).isEqualTo(28);
    assertThat(manifest.getTargetSdkVersion().getApiLevel()).isEqualTo(29);
    assertThat(manifest.getPackage()).isEqualTo("com.example.target");
  }

  @Test
  public void placeholderSubstitution() throws Exception {
    setTargetMap(
        android_binary("//java/com/example/target:target")
            .manifest(
                manifestFactory
                    .fromPackage("com.example.target")
                    .addUsesPermission("${permissionPlaceholder}"))
            .manifest_value("permissionPlaceholder", "android.permission.SEND_SMS")
            .res("res"));
    runFullBlazeSync();
    Module module = getModule("java.com.example.target.target");

    MergedManifestSnapshot manifest =
        MergedManifestManager.getMergedManifest(module).get(5, TimeUnit.SECONDS);
    assertThat(manifest.getPermissionHolder().hasPermission("android.permission.SEND_SMS"))
        .isTrue();
  }
}

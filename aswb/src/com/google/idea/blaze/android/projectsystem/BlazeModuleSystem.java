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

import com.android.manifmerger.ManifestSystemProperty;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ManifestOverrides;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Blaze implementation of {@link AndroidModuleSystem}. */
@SuppressWarnings("NullableProblems")
public class BlazeModuleSystem extends BlazeModuleSystemBase {

  @TestOnly
  public static BlazeModuleSystem create(Module module) {
    Preconditions.checkState(ApplicationManager.getApplication().isUnitTestMode());
    return new BlazeModuleSystem(module);
  }

  public static BlazeModuleSystem getInstance(Module module) {
    return ModuleServiceManager.getService(module, BlazeModuleSystem.class);
  }

  private BlazeModuleSystem(Module module) {
    super(module);
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
  @Nullable
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
}

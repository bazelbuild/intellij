/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sdk;

import static org.junit.Assert.assertNotNull;

import com.android.sdklib.IAndroidTarget;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.runtime.RunfilesPaths;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkType;

/**
 * SDK provider that exposes real Android SDK to the tests. The Android SDK is symlinked to the
 * test's sandbox
 */
public class AndroidSdkProviderForTests implements BlazeSdkProvider {

  // This SDK should be used in the test's projectview
  public static final String SUPPORTED_SDK = "test-sdk";

  // SDK_PATH is the path to output of
  // //aswb:repacked_android_sdk_stable
  private static final String SDK_PATH = "aswb/androidSdk";

  private final ImmutableMap<String, Sdk> sdks;

  public AndroidSdkProviderForTests() {
    sdks = ImmutableMap.of(SUPPORTED_SDK, setupSdk());
  }

  @Override
  public List<Sdk> getAllAndroidSdks() {
    return ImmutableList.copyOf(sdks.values());
  }

  @Nullable
  @Override
  public Sdk findSdk(String targetHash) {
    return sdks.get(targetHash);
  }

  @Nullable
  @Override
  public String getSdkTargetHash(Sdk sdk) {
    return sdks.entrySet().stream()
        .filter(kv -> kv.getValue().equals(sdk))
        .findAny()
        .map(Map.Entry::getKey)
        .orElse(null);
  }

  private static Sdk setupSdk() {
    Path sdkHomePath = RunfilesPaths.resolve(SDK_PATH);

    Sdk sdk = ProjectJdkTable.getInstance().createSdk(SUPPORTED_SDK, AndroidSdkType.getInstance());
    ApplicationManager.getApplication()
        .runWriteAction(() -> ProjectJdkTable.getInstance().addJdk(sdk));

    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(PathUtil.toSystemIndependentName(sdkHomePath.toString()));

    String androidJarRelativePath = String.format("platforms/%s/android.jar", SUPPORTED_SDK);
    Path androidJarPath = RunfilesPaths.resolve(Paths.get(SDK_PATH, androidJarRelativePath));
    VirtualFile androidJar = JarFileSystem.getInstance().findFileByPath(androidJarPath + "!/");
    assertNotNull(androidJar);
    sdkModificator.addRoot(androidJar, OrderRootType.CLASSES);

    // TODO(b/157491804): Attach resources
    // Currently raises `java.lang.AssertionError: File accessed outside allowed roots`

    // String resRelativePath = String.format("platforms/%s/data/res, SUPPORTED_SDK);
    // Path resPath = RunfilesPaths.resolve(Paths.get(SDK_PATH, resRelativePath));
    // VirtualFile resDir = LocalFileSystem.getInstance().findFileByPath(resPath.toString());
    // assertNotNull(resDir);
    // sdkModificator.addRoot(resDir, OrderRootType.CLASSES);

    AndroidSdkAdditionalData data = new AndroidSdkAdditionalData(sdk);
    AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdkHomePath.toString());
    assertNotNull(sdkData);
    IAndroidTarget target =
        Arrays.stream(sdkData.getTargets())
            .filter(t -> t.getLocation().contains(SUPPORTED_SDK))
            .findAny()
            .orElse(null);
    assertNotNull(target);
    data.setBuildTarget(target);
    sdkModificator.setSdkAdditionalData(data);

    sdkModificator.commitChanges();
    return sdk;
  }
}

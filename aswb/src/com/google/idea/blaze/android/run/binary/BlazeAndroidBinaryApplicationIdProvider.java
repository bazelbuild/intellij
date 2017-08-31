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
package com.google.idea.blaze.android.run.binary;

import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import javax.annotation.Nullable;
import org.jetbrains.android.dom.manifest.Manifest;

/** Application id provider for android_binary. */
public class BlazeAndroidBinaryApplicationIdProvider implements ApplicationIdProvider {
  private final BlazeApkBuildStep buildStep;

  public BlazeAndroidBinaryApplicationIdProvider(BlazeApkBuildStep buildStep) {
    this.buildStep = buildStep;
  }

  @Override
  public String getPackageName() throws ApkProvisionException {
    BlazeAndroidDeployInfo deployInfo = buildStep.getDeployInfo();
    Manifest manifest = deployInfo.getMergedManifest();
    if (manifest == null) {
      throw new ApkProvisionException(
          "Could not find merged manifest: " + deployInfo.getMergedManifestFile());
    }
    String applicationId =
        ApplicationManager.getApplication()
            .runReadAction((Computable<String>) () -> manifest.getPackage().getValue());
    if (applicationId == null) {
      throw new ApkProvisionException(
          "No application id in merged manifest: " + deployInfo.getMergedManifestFile());
    }
    return applicationId;
  }

  @Nullable
  @Override
  public String getTestPackageName() throws ApkProvisionException {
    return null;
  }
}

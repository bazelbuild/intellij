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
package com.google.idea.blaze.android.run.test;

import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Application id provider for android_binary.
 */
public class BlazeAndroidTestApplicationIdProvider implements ApplicationIdProvider {
  private final Project project;
  private final ListenableFuture<BlazeAndroidDeployInfo> deployInfoFuture;

  public BlazeAndroidTestApplicationIdProvider(Project project,
                                               ListenableFuture<BlazeAndroidDeployInfo> deployInfoFuture) {
    this.project = project;
    this.deployInfoFuture = deployInfoFuture;
  }

  @NotNull
  @Override
  public String getPackageName() throws ApkProvisionException {
    BlazeAndroidDeployInfo deployInfo = Futures.get(deployInfoFuture, ApkProvisionException.class);
    Manifest manifest = Iterables.getFirst(deployInfo.getAdditionalMergedManifests(), null);
    if (manifest == null) {
      throw new ApkProvisionException("Could not find manifest for application under test");
    }
    String applicationId = ApplicationManager.getApplication().runReadAction(
      (Computable<String>)() -> manifest.getPackage().getValue()
    );
    if (applicationId == null) {
      throw new ApkProvisionException("No application id in manifest under test");
    }
    return applicationId;
  }

  @Nullable
  @Override
  public String getTestPackageName() throws ApkProvisionException {
    BlazeAndroidDeployInfo deployInfo = Futures.get(deployInfoFuture, ApkProvisionException.class);
    Manifest manifest = deployInfo.getMergedManifest();
    if (manifest == null) {
      throw new ApkProvisionException("Could not find merged manifest: " + deployInfo.getMergedManifestFile());
    }
    String applicationId = ApplicationManager.getApplication().runReadAction(
      (Computable<String>)() -> manifest.getPackage().getValue()
    );
    if (applicationId == null) {
      throw new ApkProvisionException("No application id in merged manifest: " + deployInfo.getMergedManifestFile());
    }
    return applicationId;
  }
}

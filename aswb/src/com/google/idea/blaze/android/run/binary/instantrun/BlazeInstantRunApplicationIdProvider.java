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
package com.google.idea.blaze.android.run.binary.instantrun;

import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.google.idea.blaze.android.manifest.ManifestParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Application id provider for blaze instant run.
 */
public class BlazeInstantRunApplicationIdProvider implements ApplicationIdProvider {
  private final Project project;
  private final BlazeApkBuildStepInstantRun.BuildResult buildResult;

  public BlazeInstantRunApplicationIdProvider(Project project,
                                              BlazeApkBuildStepInstantRun.BuildResult buildResult) {
    this.project = project;
    this.buildResult = buildResult;
  }

  @NotNull
  @Override
  public String getPackageName() throws ApkProvisionException {
    File manifestFile = new File(buildResult.executionRoot, buildResult.apkManifestProto.getAndroidManifest().getExecRootPath());
    Manifest manifest = ManifestParser.getInstance(project).getManifest(manifestFile);
    if (manifest == null) {
      throw new ApkProvisionException("Could not find merged manifest: " + manifestFile);
    }
    String applicationId = ApplicationManager.getApplication().runReadAction(
      (Computable<String>)() -> manifest.getPackage().getValue()
    );
    if (applicationId == null) {
      throw new ApkProvisionException("No application id in merged manifest: " + manifestFile);
    }
    return applicationId;
  }

  @Nullable
  @Override
  public String getTestPackageName() throws ApkProvisionException {
    return null;
  }
}

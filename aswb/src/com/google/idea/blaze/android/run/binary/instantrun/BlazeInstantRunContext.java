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

import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.idea.fd.BuildSelection;
import com.android.tools.idea.fd.FileChangeListener;
import com.android.tools.idea.fd.InstantRunContext;
import com.google.common.hash.HashCode;
import com.google.repackaged.devtools.build.lib.rules.android.apkmanifest.ApkManifestOuterClass;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Blaze implementation of instant run context.
 */
public class BlazeInstantRunContext implements InstantRunContext {
  private static final Logger LOG = Logger.getInstance(BlazeInstantRunContext.class);
  private final Project project;
  private final ApkManifestOuterClass.ApkManifest apkManifest;
  private final String applicationId;
  private final File instantRunBuildInfoFile;
  private BuildSelection buildSelection;

  BlazeInstantRunContext(Project project,
                         ApkManifestOuterClass.ApkManifest apkManifest,
                         String applicationId,
                         File instantRunBuildInfoFile) {
    this.project = project;
    this.apkManifest = apkManifest;
    this.applicationId = applicationId;
    this.instantRunBuildInfoFile = instantRunBuildInfoFile;
  }

  @NotNull
  @Override
  public String getApplicationId() {
    return applicationId;
  }

  @NotNull
  @Override
  public HashCode getManifestResourcesHash() {
    // TODO b/28373160
    return HashCode.fromInt(0);
  }

  @Override
  public boolean usesMultipleProcesses() {
    // TODO(tomlu) -- does this make sense in blaze? We can of course just parse the manifest.
    return false;
  }

  @Nullable
  @Override
  public FileChangeListener.Changes getFileChangesAndReset() {
    return null;
  }

  @Nullable
  @Override
  public InstantRunBuildInfo getInstantRunBuildInfo() {
    if (instantRunBuildInfoFile.exists()) {
      try {
        String xml = new String(Files.readAllBytes(Paths.get(instantRunBuildInfoFile.getPath())), StandardCharsets.UTF_8);
        return InstantRunBuildInfo.get(xml);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return null;
  }

  @Override
  public void setBuildSelection(@NotNull BuildSelection buildSelection) {
    this.buildSelection = buildSelection;
  }

  @Override
  public BuildSelection getBuildSelection() {
    return buildSelection;
  }
}

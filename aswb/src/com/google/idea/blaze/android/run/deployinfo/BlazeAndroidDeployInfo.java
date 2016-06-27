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
package com.google.idea.blaze.android.run.deployinfo;

import com.google.common.collect.Lists;
import com.google.idea.blaze.android.manifest.ManifestParser;
import com.google.repackaged.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.dom.manifest.Manifest;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Info about the android_binary/android_test to deploy.
 */
public class BlazeAndroidDeployInfo {
  private final Project project;
  private final File executionRoot;
  private final AndroidDeployInfoOuterClass.AndroidDeployInfo deployInfo;

  public BlazeAndroidDeployInfo(Project project,
                                File executionRoot,
                                AndroidDeployInfoOuterClass.AndroidDeployInfo deployInfo) {
    this.project = project;
    this.executionRoot = executionRoot;
    this.deployInfo = deployInfo;
  }

  @Nullable
  public File getMergedManifestFile() {
    return new File(executionRoot, deployInfo.getMergedManifest().getExecRootPath());
  }

  @Nullable
  public Manifest getMergedManifest() {
    File manifestFile = getMergedManifestFile();
    return ManifestParser.getInstance(project).getManifest(manifestFile);
  }

  public List<File> getAdditionalMergedManifestFiles() {
    return deployInfo.getAdditionalMergedManifestsList().stream()
      .map(artifact -> new File(executionRoot, artifact.getExecRootPath()))
      .collect(Collectors.toList());
  }

  public List<Manifest> getAdditionalMergedManifests() {
    return getAdditionalMergedManifestFiles().stream()
      .map(file -> ManifestParser.getInstance(project).getManifest(file))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  public List<File> getManifestFiles() {
    List<File> result = Lists.newArrayList();
    result.add(getMergedManifestFile());
    result.addAll(getAdditionalMergedManifestFiles());
    return result;
  }

  /**
   * Returns the full list of apks to deploy, if any.
   */
  public List<File> getApksToDeploy() {
    return deployInfo.getApksToDeployList().stream()
      .map(artifact -> new File(executionRoot, artifact.getExecRootPath()))
      .collect(Collectors.toList());
  }
}

/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.libraries;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.libraries.LintCollector;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.intellij.openapi.project.Project;
import java.io.File;

/** {@inheritDoc} Collecting lint rule jars from {@code BlazeJavaSyncData} */
public class JavaLintCollector implements LintCollector {

  public static ImmutableList<BlazeArtifact> collectLintJarsArtifacts(
      BlazeProjectData blazeProjectData) {
    BlazeJavaSyncData syncData = blazeProjectData.getSyncState().get(BlazeJavaSyncData.class);
    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.getArtifactLocationDecoder();

    if (syncData == null) {
      return ImmutableList.of();
    }
    return syncData.getImportResult().pluginProcessorJars.stream()
        .map(artifactLocationDecoder::resolveOutput)
        .collect(toImmutableList());
  }

  @Override
  public ImmutableList<File> collectLintJars(Project project, BlazeProjectData blazeProjectData) {
    JarCache jarCache = JarCache.getInstance(project);
    return collectLintJarsArtifacts(blazeProjectData).stream()
        .map(jarCache::getCachedJar)
        .collect(toImmutableList());
  }
}

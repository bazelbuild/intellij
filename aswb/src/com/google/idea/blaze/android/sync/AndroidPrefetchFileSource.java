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
package com.google.idea.blaze.android.sync;

import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidResFolder;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.OutputsProvider;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

/** Adds the resource directories outside our source roots to prefetch. */
public class AndroidPrefetchFileSource implements PrefetchFileSource, OutputsProvider {

  @Override
  public void addFilesToPrefetch(
      Project project,
      ProjectViewSet projectViewSet,
      ImportRoots importRoots,
      BlazeProjectData blazeProjectData,
      Set<File> files) {
    BlazeAndroidSyncData syncData = blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class);
    if (syncData == null) {
      return;
    }
    if (syncData.importResult.resourceLibraries == null) {
      return;
    }
    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.getArtifactLocationDecoder();
    files.addAll(
        OutputArtifactResolver.resolveAll(
            project,
            artifactLocationDecoder,
            syncData.importResult.resourceLibraries.values().stream()
                .map(resourceLibrary -> resourceLibrary.root)
                .collect(toList())));
  }

  @Override
  public Set<String> prefetchFileExtensions() {
    return ImmutableSet.of("xml");
  }

  @Override
  public boolean isActive(WorkspaceLanguageSettings languageSettings) {
    return languageSettings.isLanguageActive(LanguageClass.ANDROID);
  }

  @Override
  public Collection<ArtifactLocation> selectAllRelevantOutputs(TargetIdeInfo target) {
    if (target.getJavaToolchainIdeInfo() != null) {
      return target.getJavaToolchainIdeInfo().getJavacJars();
    }
    if (target.getAndroidSdkIdeInfo() != null) {
      return ImmutableList.of(target.getAndroidSdkIdeInfo().getAndroidJar());
    }
    if (target.getAndroidAarIdeInfo() != null) {
      return ImmutableList.of(target.getAndroidAarIdeInfo().getAar());
    }

    if (target.getAndroidIdeInfo() == null) {
      return ImmutableList.of();
    }
    AndroidIdeInfo androidInfo = target.getAndroidIdeInfo();

    ImmutableList.Builder<ArtifactLocation> list = ImmutableList.builder();
    androidInfo.getResFolders().forEach(f -> addArtifact(list, f.getRoot()));
    addLibrary(list, androidInfo.getResourceJar());
    addLibrary(list, androidInfo.getIdlJar());
    addArtifact(list, androidInfo.getManifest());
    return list.build();
  }

  private static void addLibrary(
      ImmutableList.Builder<ArtifactLocation> list, @Nullable LibraryArtifact library) {
    if (library != null) {
      addArtifact(list, library.getInterfaceJar());
      addArtifact(list, library.getClassJar());
      library.getSourceJars().forEach(j -> addArtifact(list, j));
    }
  }

  private static void addArtifact(
      ImmutableList.Builder<ArtifactLocation> list, @Nullable ArtifactLocation artifact) {
    if (artifact != null) {
      list.add(artifact);
    }
  }

  @Override
  public Collection<ArtifactLocation> selectOutputsToCache(TargetIdeInfo target) {
    // other outputs are handled separately to RemoteOutputsCache
    if (target.getJavaToolchainIdeInfo() != null) {
      return target.getJavaToolchainIdeInfo().getJavacJars();
    }
    if (target.getAndroidIdeInfo() != null) {
      return getAndroidSources(target.getAndroidIdeInfo());
    }
    return ImmutableList.of();
  }

  private static Collection<ArtifactLocation> getAndroidSources(AndroidIdeInfo androidInfo) {
    Set<ArtifactLocation> fileSet = new HashSet<>();

    ArtifactLocation manifest = androidInfo.getManifest();
      if (manifest != null) {
        fileSet.add(manifest);
      }
    fileSet.addAll(androidInfo.getResources());
    for (AndroidResFolder androidResFolder : androidInfo.getResFolders()) {
        fileSet.add(androidResFolder.getRoot());
    }
    return fileSet;
  }
}

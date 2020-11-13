/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.projectstructure;

import static com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer.getApplicationIdFromManifestOrDefault;
import static com.intellij.openapi.vfs.VfsUtilCore.pathToUrl;

import com.android.builder.model.SourceProvider;
import com.android.ide.common.gradle.model.SourceProviderUtil;
import com.android.ide.common.util.PathString;
import com.android.ide.common.util.PathStringUtil;
import com.android.projectmodel.AndroidPathType;
import com.android.projectmodel.SourceSet;
import com.android.tools.idea.model.AndroidModel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.idea.BlazeAndroidModel;
import com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer.ManifestParsingStatCollector;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetProperties;
import org.jetbrains.annotations.Nullable;

class BlazeAndroidProjectStructureSyncerCompat {

  private BlazeAndroidProjectStructureSyncerCompat() {}

  static void updateAndroidFacetWithSourceAndModel(
      AndroidFacet facet, SourceProvider sourceProvider, BlazeAndroidModel androidModel) {
    facet.getProperties().RES_FOLDERS_RELATIVE_PATH =
        sourceProvider.getResDirectories().stream()
            .map(it -> pathToUrl(it.getAbsolutePath()))
            .collect(
                Collectors.joining(
                    AndroidFacetProperties.PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION));
    facet.getProperties().TEST_RES_FOLDERS_RELATIVE_PATH = "";
    AndroidModel.set(facet, androidModel);
  }

  static void updateModuleFacetInMemoryState(
      Project project,
      @Nullable BlazeContext context,
      AndroidSdkPlatform androidSdkPlatform,
      Module module,
      File moduleDirectory,
      @Nullable File manifestFile,
      String resourceJavaPackage,
      Collection<File> resources,
      boolean configAndroidJava8Libs,
      @Nullable ManifestParsingStatCollector manifestParsingStatCollector) {
    List<PathString> manifests =
        manifestFile == null
            ? ImmutableList.of()
            : ImmutableList.of(PathStringUtil.toPathString(manifestFile));
    SourceSet sourceSet =
        new SourceSet(
            ImmutableMap.of(
                AndroidPathType.RES,
                PathStringUtil.toPathStrings(resources),
                AndroidPathType.MANIFEST,
                manifests));
    SourceProvider sourceProvider =
        SourceProviderUtil.toSourceProvider(sourceSet, module.getName());

    String applicationId =
        getApplicationIdFromManifestOrDefault(
            project, context, manifestFile, resourceJavaPackage, manifestParsingStatCollector);

    BlazeAndroidModel androidModel =
        new BlazeAndroidModel(
            project,
            moduleDirectory,
            sourceProvider,
            applicationId,
            androidSdkPlatform.androidMinSdkLevel,
            configAndroidJava8Libs);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      BlazeAndroidProjectStructureSyncerCompat.updateAndroidFacetWithSourceAndModel(
          facet, sourceProvider, androidModel);
    }
  }
}

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

import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder;
import com.android.tools.idea.projectsystem.ScopeType;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.idea.BlazeAndroidModel;
import com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer.ManifestParsingStatCollector;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.Collection;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetProperties;

class BlazeAndroidProjectStructureSyncerCompat {
  private BlazeAndroidProjectStructureSyncerCompat() {}

  static void updateAndroidFacetWithSourceAndModel(
      AndroidFacet facet, NamedIdeaSourceProvider sourceProvider, BlazeAndroidModel androidModel) {
    facet.getProperties().RES_FOLDERS_RELATIVE_PATH =
        String.join(
            AndroidFacetProperties.PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION,
            sourceProvider.getResDirectoryUrls());
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
    String name = module.getName();
    File manifest = manifestFile != null ? manifestFile : new File("MissingManifest.xml");
    NamedIdeaSourceProvider sourceProvider =
        NamedIdeaSourceProviderBuilder.create(name, VfsUtilCore.fileToUrl(manifest))
            .withScopeType(ScopeType.MAIN)
            .withResDirectoryUrls(
                ContainerUtil.map(resources, it -> VfsUtilCore.fileToUrl(it.getAbsoluteFile())))
            .build();
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

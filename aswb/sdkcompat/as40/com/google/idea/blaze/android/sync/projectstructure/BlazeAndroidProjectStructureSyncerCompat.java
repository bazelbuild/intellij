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

import static com.intellij.openapi.vfs.VfsUtilCore.pathToUrl;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.model.AndroidModel;
import com.google.idea.blaze.android.sync.model.idea.BlazeAndroidModel;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

class BlazeAndroidProjectStructureSyncerCompat {

  private BlazeAndroidProjectStructureSyncerCompat() {}

  static void updateAndroidFacetWithSourceAndModel(
      AndroidFacet facet, SourceProvider sourceProvider, BlazeAndroidModel androidModel) {
    facet.getProperties().RES_FOLDERS_RELATIVE_PATH =
        sourceProvider.getResDirectories().stream()
            .map(it -> pathToUrl(it.getAbsolutePath()))
            .collect(
                Collectors.joining(
                    JpsAndroidModuleProperties.PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION));
    facet.getProperties().TEST_RES_FOLDERS_RELATIVE_PATH = "";
    AndroidModel.set(facet, androidModel);
  }
}

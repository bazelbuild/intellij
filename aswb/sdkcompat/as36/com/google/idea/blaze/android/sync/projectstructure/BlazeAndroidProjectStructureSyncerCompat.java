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

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.model.AndroidModel;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.android.facet.AndroidFacet;

/** Compat class for {@link BlazeAndroidProjectStructureSyncer} */
@VisibleForTesting
public class BlazeAndroidProjectStructureSyncerCompat {

  private BlazeAndroidProjectStructureSyncerCompat() {}

  @VisibleForTesting
  public static void updateAndroidFacetWithSourceAndModel(
      AndroidFacet facet, SourceProvider sourceProvider, AndroidModel androidModel) {
    facet.getConfiguration().setModel(androidModel);
  }
}

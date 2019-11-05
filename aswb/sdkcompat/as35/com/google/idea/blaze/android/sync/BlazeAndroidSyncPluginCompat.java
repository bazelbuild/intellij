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
package com.google.idea.blaze.android.sync;

import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

class BlazeAndroidSyncPluginCompat {

  private BlazeAndroidSyncPluginCompat() {}

  static boolean facetHasAndroidModel(@Nullable AndroidFacet facet) {
    return facet != null
        && facet.requiresAndroidModel()
        && facet.getConfiguration().getModel() == null;
  }
}

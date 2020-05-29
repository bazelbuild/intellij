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
package com.google.idea.blaze.android.run;

import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.Nullable;

/** Compat utilities for obtaining an {@link AndroidPlatform}. #api3.6 */
public class AndroidPlatformCompat {
  private AndroidPlatformCompat() {}

  @Nullable
  public static AndroidPlatform getAndroidPlatform(AndroidFacet facet) {
    return AndroidPlatform.getInstance(facet.getModule());
  }
}

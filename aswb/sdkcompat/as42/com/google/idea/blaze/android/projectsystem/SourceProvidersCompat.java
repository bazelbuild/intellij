/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.projectsystem;

import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.sync.model.idea.BlazeAndroidModel;
import org.jetbrains.android.facet.SourceProvidersImpl;

/** Factory methods for creating SourceProviders, #api42. */
public class SourceProvidersCompat {
  public static SourceProviders forModel(BlazeAndroidModel model) {
    NamedIdeaSourceProvider mainSourceProvider = model.getDefaultSourceProvider();
    return new SourceProvidersImpl(
        mainSourceProvider,
        ImmutableList.of(mainSourceProvider),
        ImmutableList.of(mainSourceProvider),
        ImmutableList.of(mainSourceProvider),
        ImmutableList.of(mainSourceProvider),
        ImmutableList.of(mainSourceProvider));
  }
}

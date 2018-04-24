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
package com.google.idea.blaze.android.plugin;

import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.plugin.BlazePluginId;
import com.google.idea.blaze.base.settings.BuildSystem;

/** ASwB plugin configuration information. */
public class AswbPlugin implements BlazePluginId {

  @Override
  public String getPluginId() {
    // Please keep these up-to-date with plugin xmls
    BuildSystem type = BuildSystemProvider.defaultBuildSystem().buildSystem();
    if (type == BuildSystem.Blaze) {
      return "com.google.idea.blaze.aswb";
    }
    return "com.google.idea.bazel.aswb";
  }
}

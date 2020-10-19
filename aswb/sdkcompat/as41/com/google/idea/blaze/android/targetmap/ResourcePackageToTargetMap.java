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

package com.google.idea.blaze.android.targetmap;

import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.android.sync.importer.BlazeImportUtil;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.SyncCache;
import com.intellij.openapi.project.Project;

/**
 * Maps java packages of resource declaring target to the targets that declare them. One package can
 * be exported by multiple targets.
 */
public class ResourcePackageToTargetMap {

  private ResourcePackageToTargetMap() {}

  public static ImmutableMultimap<String, TargetKey> get(Project project) {
    // Note: After every sync, first call to `get` will in turn call `createPackagesToTargetMap`.
    // All subsequent calls should simply be a map lookup.
    ImmutableMultimap<String, TargetKey> map =
        SyncCache.getInstance(project)
            .get(
                ResourcePackageToTargetMap.class,
                ResourcePackageToTargetMap::createPackagesToTargetsMap);
    return map == null ? ImmutableMultimap.of() : map;
  }

  static ImmutableMultimap<String, TargetKey> createPackagesToTargetsMap(
      Project project, BlazeProjectData blazeProjectData) {
    TargetMap targetMap = blazeProjectData.getTargetMap();
    ImmutableMultimap.Builder<String, TargetKey> builder = ImmutableMultimap.builder();

    for (TargetIdeInfo target : targetMap.targets()) {
      AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
      if (androidIdeInfo == null || !androidIdeInfo.generateResourceClass()) {
        continue;
      }

      String resourcePackage =
          BlazeImportUtil.javaResourcePackageFor(target, /* inferPackage = */ true);
      if (resourcePackage != null) {
        builder.put(resourcePackage, target.getKey());
      }
    }

    return builder.build();
  }
}

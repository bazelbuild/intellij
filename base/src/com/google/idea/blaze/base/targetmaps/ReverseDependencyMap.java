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
package com.google.idea.blaze.base.targetmaps;

import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;

/** Handy class to create an reverse dep map of all targets */
public class ReverseDependencyMap {
  public static ImmutableMultimap<TargetKey, TargetKey> createRdepsMap(TargetMap targetMap) {
    ImmutableMultimap.Builder<TargetKey, TargetKey> builder = ImmutableMultimap.builder();
    for (TargetIdeInfo target : targetMap.targets()) {
      TargetKey key = target.key;
      for (Dependency dep : target.dependencies) {
        TargetKey depKey = dep.targetKey;
        if (targetMap.contains(depKey)) {
          builder.put(depKey, key);
        }
      }
    }
    return builder.build();
  }
}

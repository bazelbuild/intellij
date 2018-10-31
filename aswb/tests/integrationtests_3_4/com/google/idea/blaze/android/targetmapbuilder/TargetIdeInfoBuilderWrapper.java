/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.targetmapbuilder;

import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;

/** Top-level interface for targetmapbuilder utility classes. */
public interface TargetIdeInfoBuilderWrapper {
  /**
   * Each implementing class provides convenience methods for configuring a particular type of
   * {@link TargetIdeInfo.Builder}. This method returns the final result.
   *
   * @return the {@link TargetIdeInfo.Builder} configured by this utility
   */
  TargetIdeInfo.Builder getIdeInfoBuilder();

  /**
   * This does the same thing as {@link
   * TargetIdeInfoBuilderWrapper#targetMapBuilder(TargetIdeInfoBuilderWrapper...)}, except it
   * actually builds the target map as a convenience for callers who don't need to perform any
   * additional modifications on the builder.
   */
  static TargetMap targetMap(TargetIdeInfoBuilderWrapper... wrappers) {
    return targetMapBuilder(wrappers).build();
  }

  /**
   * Given an array of TargetIdeInfoBuilderWrappers, this method combines the {@link
   * TargetIdeInfo.Builder} instances they have configured into a single {@link TargetMapBuilder},
   * which callers can continue to update until they're ready to build the final {@link TargetMap}.
   */
  static TargetMapBuilder targetMapBuilder(TargetIdeInfoBuilderWrapper... wrappers) {
    TargetMapBuilder builder = TargetMapBuilder.builder();
    for (TargetIdeInfoBuilderWrapper wrapper : wrappers) {
      builder.addTarget(wrapper.getIdeInfoBuilder());
    }
    return builder;
  }
}

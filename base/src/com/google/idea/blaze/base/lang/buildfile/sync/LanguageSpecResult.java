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
package com.google.idea.blaze.base.lang.buildfile.sync;

import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import java.io.Serializable;

/** The BUILD language specifications, serialized along with the sync data. */
public class LanguageSpecResult implements Serializable {

  private static final long ONE_DAY_IN_MILLISECONDS = 1000 * 60 * 60 * 24;

  private final BuildLanguageSpec spec;
  private final long timestampMillis;

  public LanguageSpecResult(BuildLanguageSpec spec, long timestampMillis) {
    this.spec = spec;
    this.timestampMillis = timestampMillis;
  }

  public BuildLanguageSpec getSpec() {
    return spec;
  }

  public long getTimestampMillis() {
    return timestampMillis;
  }

  public boolean shouldRecalculateSpec() {
    return System.currentTimeMillis() - getTimestampMillis() > ONE_DAY_IN_MILLISECONDS;
  }
}

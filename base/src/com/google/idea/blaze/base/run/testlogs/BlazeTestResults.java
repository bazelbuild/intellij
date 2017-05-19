/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.testlogs;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Label;
import java.io.File;

/** Results from a 'blaze test' invocation. */
public class BlazeTestResults {

  /** Output test XML files, grouped by target label. */
  public final ImmutableMultimap<Label, File> testXmlFiles;
  /** Targets which failed to build */
  public final ImmutableSet<Label> failedTargets;

  public BlazeTestResults(
      ImmutableMultimap<Label, File> testXmlFiles, ImmutableSet<Label> failedTargets) {
    this.testXmlFiles = testXmlFiles;
    this.failedTargets = failedTargets;
  }
}

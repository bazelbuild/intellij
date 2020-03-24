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
package com.google.idea.blaze.android.sync.importer;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.idea.blaze.android.sync.importer.problems.GeneratedResourceRetentionFilter;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Predicate that returns true for artifacts whose relative paths are present in the whitelist. Also
 * records all the {@link ArtifactLocation} objects that have been used as part of the test, which
 * can be used to determine whitelist entries that are no longer needed.
 *
 * <p>Note that any artifacts which pass the {@link GeneratedResourceRetentionFilter} are simply
 * retained without checking them against the whitelist.
 */
public class WhitelistFilter implements Predicate<ArtifactLocation> {
  final Set<ArtifactLocation> testedAgainstWhitelist = Sets.newHashSet();
  private final ImmutableSet<String> whitelistedGenResourcePaths;
  private final Predicate<ArtifactLocation> retentionFilter;

  public WhitelistFilter(
      Set<String> whitelistedGenResourcePaths, Predicate<ArtifactLocation> retentionFilter) {
    this.whitelistedGenResourcePaths = ImmutableSet.copyOf(whitelistedGenResourcePaths);
    this.retentionFilter = retentionFilter;
  }

  @Override
  public boolean test(ArtifactLocation location) {
    if (retentionFilter.test(location)) {
      return true;
    }
    testedAgainstWhitelist.add(location);
    return whitelistedGenResourcePaths.contains(location.getRelativePath());
  }
}

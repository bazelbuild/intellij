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

package com.google.idea.blaze.java.sync.importer;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.common.experiments.FeatureRolloutExperiment;
import com.google.idea.common.experiments.IntExperiment;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Filters out any {@link BlazeJarLibrary} whose corresponding IntelliJ library would reference only
 * an effectively empty JAR (i.e. it has nothing other than a manifest and directories).
 *
 * <p>Since this filter is used, in part, to determine which remote output JARs should be copied to
 * a local cache, checking the contents of those JARs can involve expensive network operations. We
 * try to minimize this cost by checking the JAR's size first and applying heuristics to avoid doing
 * extra work in the more obvious cases.
 */
public class EmptyLibraryFilter implements Predicate<BlazeLibrary> {
  private static final String FN_MANIFEST = "MANIFEST.MF";

  @VisibleForTesting
  public static final FeatureRolloutExperiment filterExperiment =
      new FeatureRolloutExperiment("blaze.empty.jar.filter");
  /**
   * Any JAR that is this size (in bytes) or smaller is assumed to be empty.
   *
   * <p>We came up with this number by checking the file size and contents for every JAR in the
   * .blaze/libraries directory for several projects (AGSA, AGMM, Express, Photos, Phonesky,
   * Memegen, and ASwB). This is the size of the largest empty JAR we saw that is still smaller than
   * all of the non-empty JARs we saw.
   */
  private static final IntExperiment presumedEmptyThresholdBytes =
      new IntExperiment("blaze.empty.jar.threshold", 359);
  /**
   * Any JAR that is this size (in bytes) or larger is assumed to be non-empty.
   *
   * <p>We came up with this number by checking the file size and contents for every JAR in the
   * .blaze/libraries directory for several projects (AGSA, AGMM, Express, Photos, Phonesky,
   * Memegen, and ASwB). This is the size of the smallest non-empty JAR we saw that is still larger
   * than all of the empty JARs we saw.
   */
  private static final IntExperiment presumedNonEmptyThresholdBytes =
      new IntExperiment("blaze.nonempty.jar.threshold", 470);

  private static final Logger logger = Logger.getInstance(EmptyLibraryFilter.class);

  private final ArtifactLocationDecoder locationDecoder;

  EmptyLibraryFilter(ArtifactLocationDecoder locationDecoder) {
    this.locationDecoder = locationDecoder;
  }

  public static boolean isEnabled() {
    return filterExperiment.isEnabled();
  }

  @Override
  public boolean test(BlazeLibrary blazeLibrary) {
    if (!isEnabled() || !(blazeLibrary instanceof BlazeJarLibrary)) {
      return true;
    }
    ArtifactLocation location =
        ((BlazeJarLibrary) blazeLibrary).libraryArtifact.jarForIntellijLibrary();
    BlazeArtifact artifact = locationDecoder.resolveOutput(location);
    try {
      return !isEmpty(artifact);
    } catch (IOException e) {
      logger.warn(e);
      return true;
    }
  }

  /**
   * Returns true if the given JAR is effectively empty (i.e. it has nothing other than a manifest
   * and directories).
   */
  static boolean isEmpty(BlazeArtifact artifact) throws IOException {
    long length = artifact.getLength();
    if (length <= presumedEmptyThresholdBytes.getValue()) {
      // Note: this implicitly includes files that can't be found (length -1 or 0).
      return true;
    }
    if (length >= presumedNonEmptyThresholdBytes.getValue()) {
      return false;
    }
    try (InputStream inputStream = artifact.getInputStream();
        JarInputStream jarInputStream = new JarInputStream(inputStream)) {
      return isEmpty(jarInputStream);
    }
  }

  private static boolean isEmpty(JarInputStream jar) throws IOException {
    for (JarEntry entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
      if (!entry.isDirectory() && !entry.getName().endsWith(FN_MANIFEST)) {
        return false;
      }
    }
    return true;
  }
}

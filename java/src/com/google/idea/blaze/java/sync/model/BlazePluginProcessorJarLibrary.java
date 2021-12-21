/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync.model;

import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.libraries.JarCache;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import java.io.File;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** An immutable reference to a plugin processor jar required by a rule. */
@Immutable
public final class BlazePluginProcessorJarLibrary extends BlazeJarLibrary {
  private static final Logger logger = Logger.getInstance(BlazePluginProcessorJarLibrary.class);

  public BlazePluginProcessorJarLibrary(
      LibraryArtifact libraryArtifact, @Nullable TargetKey targetKey) {
    super(libraryArtifact, targetKey);
  }

  public static BlazePluginProcessorJarLibrary fromProto(ProjectData.BlazeLibrary proto) {
    return new BlazePluginProcessorJarLibrary(
        LibraryArtifact.fromProto(proto.getBlazeJarLibrary().getLibraryArtifact()),
        proto.getBlazeJarLibrary().hasTargetKey()
            ? TargetKey.fromProto(proto.getBlazeJarLibrary().getTargetKey())
            : null);
  }

  @Override
  public void modifyLibraryModel(
      Project project,
      ArtifactLocationDecoder artifactLocationDecoder,
      Library.ModifiableModel libraryModel) {
    logger.warn("Try to add lint rule jar to library model but it's not allowed.");
  }

  @Override
  @Nullable
  public File getLintRuleJar(Project project, ArtifactLocationDecoder decoder) {
    JarCache jarCache = JarCache.getInstance(project);
    return jarCache.getCachedJar(decoder, this);
  }
}

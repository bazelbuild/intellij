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
package com.google.idea.blaze.android.sync.model;

import com.google.idea.blaze.android.libraries.UnpackedAars;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library.ModifiableModel;
import java.io.File;
import javax.annotation.concurrent.Immutable;

/**
 * A library corresponding to an AAR file. Has jars and resource directories.
 *
 * <p>We may want {@link com.google.idea.blaze.java.libraries.BlazeSourceJarNavigationPolicy} to
 * work with these library jars too. However, aar_import will need to have an attribute to point at
 * any source jars corresponding to jars within the aar.
 */
@Immutable
public final class AarLibrary extends BlazeLibrary {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = Logger.getInstance(AarLibrary.class);

  public final LibraryArtifact libraryArtifact;
  public final ArtifactLocation aarArtifact;

  public AarLibrary(LibraryArtifact libraryArtifact, ArtifactLocation aarArtifact) {
    // Use the aar's name for the library key. The jar name is the same for all AARs, so could more
    // easily get a hash collision.
    super(LibraryKey.fromArtifactLocation(aarArtifact));
    this.libraryArtifact = libraryArtifact;
    this.aarArtifact = aarArtifact;
  }

  /**
   * Create an IntelliJ library that matches Android Studio's expectation for an AAR. See {@link
   * org.jetbrains.android.facet.ResourceFolderManager#addAarsFromModuleLibraries}.
   */
  @Override
  public void modifyLibraryModel(
      Project project,
      ArtifactLocationDecoder artifactLocationDecoder,
      ModifiableModel libraryModel) {
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);
    File resourceDirectory = unpackedAars.getResourceDirectory(artifactLocationDecoder, this);
    File jar = unpackedAars.getClassJar(artifactLocationDecoder, this);
    if (resourceDirectory == null) {
      logger.warn("Failed to update AAR library model for: " + aarArtifact);
      return;
    }
    libraryModel.addRoot(pathToUrl(jar), OrderRootType.CLASSES);
    libraryModel.addRoot(pathToUrl(resourceDirectory), OrderRootType.CLASSES);
  }
}

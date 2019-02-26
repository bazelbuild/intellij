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

import com.android.SdkConstants;
import com.android.ide.common.util.PathString;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.android.libraries.UnpackedAars;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.Library.ModifiableModel;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import javax.annotation.concurrent.Immutable;
import org.jetbrains.annotations.Nullable;

/**
 * A library corresponding to an AAR file. Has jars and resource directories.
 *
 * <p>We may want {@link com.google.idea.blaze.java.libraries.BlazeSourceJarNavigationPolicy} to
 * work with these library jars too. However, aar_import will need to have an attribute to point at
 * any source jars corresponding to jars within the aar.
 */
@Immutable
public final class AarLibrary extends BlazeLibrary {
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

  static AarLibrary fromProto(ProjectData.BlazeLibrary proto) {
    return new AarLibrary(
        LibraryArtifact.fromProto(proto.getAarLibrary().getLibraryArtifact()),
        ArtifactLocation.fromProto(proto.getAarLibrary().getAarArtifact()));
  }

  @Override
  public ProjectData.BlazeLibrary toProto() {
    return super.toProto()
        .toBuilder()
        .setAarLibrary(
            ProjectData.AarLibrary.newBuilder()
                .setLibraryArtifact(libraryArtifact.toProto())
                .setAarArtifact(aarArtifact.toProto())
                .build())
        .build();
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

  /** Get path to res folder according to CLASSES root of modifiable model */
  @Nullable
  public PathString getResFolder(Project project) {
    Library aarLibrary =
        ProjectLibraryTable.getInstance(project)
            .getLibraryByName(this.key.getIntelliJLibraryName());
    if (aarLibrary != null) {
      VirtualFile[] files = aarLibrary.getFiles(OrderRootType.CLASSES);
      for (VirtualFile file : files) {
        if (file.isDirectory() && SdkConstants.FD_RES.equals(file.getName())) {
          return new PathString(file.getPath());
        }
      }
    }
    return null;
  }
}

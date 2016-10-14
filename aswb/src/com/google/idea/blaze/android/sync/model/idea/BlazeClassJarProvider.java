/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync.model.idea;

import com.android.SdkConstants;
import com.android.tools.idea.model.ClassJarProvider;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.OrderedSet;
import java.io.File;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Collects class jars from the user's build. */
public class BlazeClassJarProvider extends ClassJarProvider {

  private final Project project;

  public BlazeClassJarProvider(final Project project) {
    this.project = project;
  }

  @Override
  @Nullable
  public VirtualFile findModuleClassFile(String className, Module module) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    LocalFileSystem localVfs = LocalFileSystem.getInstance();
    String classNamePath = className.replace('.', File.separatorChar) + SdkConstants.DOT_CLASS;
    BlazeJavaSyncData syncData = blazeProjectData.syncState.get(BlazeJavaSyncData.class);
    if (syncData == null) {
      return null;
    }
    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.artifactLocationDecoder;
    for (File classJar : artifactLocationDecoder.decodeAll(syncData.importResult.buildOutputJars)) {
      VirtualFile classJarVF = localVfs.findFileByIoFile(classJar);
      if (classJarVF == null) {
        continue;
      }
      VirtualFile classFile = findClassInJar(classJarVF, classNamePath);
      if (classFile != null) {
        return classFile;
      }
    }
    return null;
  }

  @Nullable
  private static VirtualFile findClassInJar(final VirtualFile classJar, String classNamePath) {
    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(classJar);
    if (jarRoot == null) {
      return null;
    }
    return jarRoot.findFileByRelativePath(classNamePath);
  }

  @Override
  public List<VirtualFile> getModuleExternalLibraries(Module module) {
    OrderedSet<VirtualFile> results = new OrderedSet<VirtualFile>();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return results;
    }
    BlazeJavaSyncData syncData = blazeProjectData.syncState.get(BlazeJavaSyncData.class);
    if (syncData == null) {
      return null;
    }
    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.artifactLocationDecoder;
    LocalFileSystem localVfs = LocalFileSystem.getInstance();
    for (BlazeJarLibrary blazeLibrary : syncData.importResult.libraries.values()) {
      LibraryArtifact libraryArtifact = blazeLibrary.libraryArtifact;
      ArtifactLocation classJar = libraryArtifact.classJar;
      if (classJar == null) {
        continue;
      }
      VirtualFile libVF = localVfs.findFileByIoFile(artifactLocationDecoder.decode(classJar));
      if (libVF == null) {
        continue;
      }
      results.add(libVF);
    }

    return results;
  }
}

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
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.blaze.java.sync.model.BlazeLibrary;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class BlazeClassJarProvider extends ClassJarProvider {

  private @NotNull final Project project;

  public BlazeClassJarProvider(@NotNull final Project project){
    this.project = project;
  }

  @Override
  @Nullable
  public VirtualFile findModuleClassFile(@NotNull String className, @NotNull Module module) {
    BlazeProjectData blazeProjectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    LocalFileSystem localVfs = LocalFileSystem.getInstance();
    String classNamePath = className.replace('.', File.separatorChar) + SdkConstants.DOT_CLASS;
    BlazeJavaSyncData syncData = blazeProjectData.syncState.get(BlazeJavaSyncData.class);
    if (syncData == null) {
      return null;
    }
    for (File runtimeJar : syncData.importResult.buildOutputJars) {
      VirtualFile runtimeJarVF = localVfs.findFileByIoFile(runtimeJar);
      if (runtimeJarVF == null) {
        continue;
      }
      VirtualFile classFile = findClassInJar(runtimeJarVF, classNamePath);
      if (classFile != null) {
        return classFile;
      }
    }
    return null;
  }

  @Nullable
  private static VirtualFile findClassInJar(@NotNull final VirtualFile runtimeJar,
                                            @NotNull String classNamePath) {
    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(runtimeJar);
    if (jarRoot == null) {
      return null;
    }
    return jarRoot.findFileByRelativePath(classNamePath);
  }

  @Override
  @NotNull
  public List<VirtualFile> getModuleExternalLibraries(@NotNull Module module) {
    OrderedSet<VirtualFile> results = new OrderedSet<VirtualFile>();
    BlazeProjectData blazeProjectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return results;
    }
    BlazeJavaSyncData syncData = blazeProjectData.syncState.get(BlazeJavaSyncData.class);
    if (syncData == null) {
      return null;
    }
    LocalFileSystem localVfs = LocalFileSystem.getInstance();
    for (BlazeLibrary blazeLibrary : syncData.importResult.libraries.values()) {
      LibraryArtifact libraryArtifact = blazeLibrary.getLibraryArtifact();
      if (libraryArtifact == null) {
        continue;
      }
      ArtifactLocation runtimeJar = libraryArtifact.runtimeJar;
      if (runtimeJar == null) {
        continue;
      }
      VirtualFile libVF = localVfs.findFileByIoFile(runtimeJar.getFile());
      if (libVF == null) {
        continue;
      }
      results.add(libVF);
    }

    return results;
  }
}

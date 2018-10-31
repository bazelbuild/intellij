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
package com.google.idea.blaze.base.model;

import com.google.common.base.Objects;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.io.URLUtil;
import java.io.File;
import javax.annotation.concurrent.Immutable;

/** A model object for something that will map to an IntelliJ library. */
@Immutable
public abstract class BlazeLibrary implements ProtoWrapper<ProjectData.BlazeLibrary> {
  public final LibraryKey key;

  protected BlazeLibrary(LibraryKey key) {
    this.key = key;
  }

  @Override
  public ProjectData.BlazeLibrary toProto() {
    return ProjectData.BlazeLibrary.newBuilder().setLibraryKey(key.toProto()).build();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(key);
  }

  @Override
  public String toString() {
    return key.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BlazeLibrary)) {
      return false;
    }

    BlazeLibrary that = (BlazeLibrary) other;
    return Objects.equal(key, that.key);
  }

  public abstract void modifyLibraryModel(
      Project project,
      ArtifactLocationDecoder artifactLocationDecoder,
      Library.ModifiableModel libraryModel);

  protected static String pathToUrl(File path) {
    String name = path.getName();
    boolean isJarFile =
        FileUtilRt.extensionEquals(name, "jar") || FileUtilRt.extensionEquals(name, "zip");
    // .jar files require an URL with "jar" protocol.
    String protocol =
        isJarFile
            ? StandardFileSystems.JAR_PROTOCOL
            : VirtualFileSystemProvider.getInstance().getSystem().getProtocol();
    String filePath = FileUtil.toSystemIndependentName(path.getPath());
    String url = VirtualFileManager.constructUrl(protocol, filePath);
    if (isJarFile) {
      url += URLUtil.JAR_SEPARATOR;
    }
    return url;
  }
}

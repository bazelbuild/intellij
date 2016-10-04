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
package com.google.idea.blaze.java.sync.model;

import com.google.common.base.Objects;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.java.libraries.JarCache;
import com.google.idea.blaze.java.libraries.SourceJarManager;
import com.google.idea.blaze.java.settings.BlazeJavaUserSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import java.io.File;
import javax.annotation.concurrent.Immutable;

/** An immutable reference to a .jar required by a rule. */
@Immutable
public final class BlazeJarLibrary extends BlazeLibrary {
  private static final long serialVersionUID = 1L;

  public final LibraryArtifact libraryArtifact;

  public final Label originatingRule;

  public BlazeJarLibrary(LibraryArtifact libraryArtifact, Label originatingRule) {
    super(LibraryKey.fromJarFile(libraryArtifact.jarForIntellijLibrary().getFile()));
    this.libraryArtifact = libraryArtifact;
    this.originatingRule = originatingRule;
  }

  @Override
  public void modifyLibraryModel(Project project, Library.ModifiableModel libraryModel) {
    JarCache jarCache = JarCache.getInstance(project);
    File jar = jarCache.getCachedJar(this);
    libraryModel.addRoot(pathToUrl(jar), OrderRootType.CLASSES);

    boolean attachSourcesByDefault =
        BlazeJavaUserSettings.getInstance().getAttachSourcesByDefault();
    SourceJarManager sourceJarManager = SourceJarManager.getInstance(project);
    boolean attachSourceJar = attachSourcesByDefault || sourceJarManager.hasSourceJarAttached(key);
    if (attachSourceJar && libraryArtifact.sourceJar != null) {
      File sourceJar = jarCache.getCachedSourceJar(this);
      if (sourceJar != null) {
        libraryModel.addRoot(pathToUrl(sourceJar), OrderRootType.SOURCES);
      }
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), libraryArtifact, originatingRule);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BlazeJarLibrary)) {
      return false;
    }

    BlazeJarLibrary that = (BlazeJarLibrary) other;

    return super.equals(other)
        && Objects.equal(libraryArtifact, that.libraryArtifact)
        && Objects.equal(originatingRule, that.originatingRule);
  }
}

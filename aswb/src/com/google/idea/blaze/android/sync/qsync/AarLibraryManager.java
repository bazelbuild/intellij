/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.qsync;

import com.android.ide.common.util.PathString;
import com.android.projectmodel.ExternalAndroidLibrary;
import com.android.projectmodel.ExternalLibraryImpl;
import com.android.projectmodel.SelectiveResourceFolder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.idea.blaze.android.manifest.ManifestParser;
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest;
import com.google.idea.blaze.base.qsync.AarDependencyRegistry;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import javax.annotation.Nullable;

/** Maintains caches of {@link ExternalAndroidLibrary} for external aars */
@Service
public final class AarLibraryManager {
  private final Map<String, ExternalAndroidLibrary> libraries;
  private final Project project;

  public AarLibraryManager(Project project) {
    libraries = Maps.newHashMap();
    this.project = project;
  }

  public ImmutableList<ExternalAndroidLibrary> getLibraries() {
    ImmutableList<String> aarDirs = project.getService(AarDependencyRegistry.class).getAarDirs();
    aarDirs.stream()
        .filter(dir -> !libraries.containsKey(dir))
        .forEach(
            dir -> {
              ExternalAndroidLibrary externalAndroidLibrary = toExternalLibrary(dir);
              if (externalAndroidLibrary != null) {
                libraries.put(dir, externalAndroidLibrary);
              }
            });
    return ImmutableList.copyOf(libraries.values());
  }

  @Nullable
  private ExternalAndroidLibrary toExternalLibrary(String aarDirName) {
    Path libs = Paths.get(project.getBasePath()).resolve(".blaze/libraries");
    Path aarDir = libs.resolve(aarDirName);

    File aarFile = new File(aarDir.toString());

    File manifest = new File(aarDir.toString(), "AndroidManifest.xml");
    String javaPackage;
    try (InputStream is = new FileInputStream(manifest)) {
      ParsedManifest parsedManifest = ManifestParser.parseManifestFromInputStream(is);
      javaPackage = parsedManifest.packageName;
    } catch (IOException ioe) {
      return null;
    }

    PathString resFolderPathString = new PathString(new File(aarFile, "res"));
    return new ExternalLibraryImpl(aarDirName)
        .withLocation(new PathString(aarFile))
        .withManifestFile(new PathString(manifest))
        .withResFolder(new SelectiveResourceFolder(resFolderPathString, null))
        .withSymbolFile(resFolderPathString.getParentOrRoot().resolve("R.txt"))
        .withPackageName(javaPackage);
  }
}

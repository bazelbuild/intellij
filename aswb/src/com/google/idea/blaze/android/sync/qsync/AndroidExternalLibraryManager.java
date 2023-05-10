/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.android.manifest.ManifestParser;
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Retrieves {@link ExternalAndroidLibrary} for external aars */
public final class AndroidExternalLibraryManager {
  private static final Logger logger = Logger.getInstance(AndroidExternalLibraryManager.class);

  private final Supplier<Collection<Path>> pathSupplier;
  private final Map<Path, ExternalAndroidLibrary> librariesByPath;

  public AndroidExternalLibraryManager(Supplier<Collection<Path>> pathSupplier) {
    this.pathSupplier = pathSupplier;
    this.librariesByPath = new HashMap<>();
  }

  public synchronized Collection<ExternalAndroidLibrary> getExternalLibraries() {
    Collection<Path> currentPaths = pathSupplier.get();
    librariesByPath.keySet().retainAll(currentPaths);

    for (Path path : currentPaths) {
      // TODO: This assumes that a given dependency will never change over the lifetime of a
      // project, which may not always be true.
      if (librariesByPath.containsKey(path)) {
        continue;
      } else {
        ExternalAndroidLibrary externalAndroidLibrary = toExternalLibrary(path);
        if (externalAndroidLibrary != null) {
          librariesByPath.put(path, externalAndroidLibrary);
        }
      }
    }
    return librariesByPath.values();
  }

  @Nullable
  private static ExternalAndroidLibrary toExternalLibrary(Path libraryPath) {
    File aarFile = libraryPath.toFile();
    File manifest = new File(aarFile, "AndroidManifest.xml");
    if (!manifest.exists()) {
      logger.warn(String.format("No manifest for library %s", aarFile.getName()));
      return null;
    }

    String javaPackage;
    try (InputStream is = new FileInputStream(manifest)) {
      ParsedManifest parsedManifest = ManifestParser.parseManifestFromInputStream(is);
      javaPackage = parsedManifest.packageName;
    } catch (IOException ioe) {
      logger.warn(
          String.format("Could not parse package from manifest in library %s", aarFile.getName()));
      return null;
    }

    PathString resFolderPathString = new PathString(new File(aarFile, "res"));
    return new ExternalLibraryImpl(aarFile.getName())
        .withLocation(new PathString(aarFile))
        .withManifestFile(new PathString(manifest))
        .withResFolder(new SelectiveResourceFolder(resFolderPathString, null))
        .withSymbolFile(resFolderPathString.getParentOrRoot().resolve("R.txt"))
        .withPackageName(javaPackage);
  }
}

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
package com.google.idea.blaze.java.sync.projectstructure;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.URLUtil;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;

/** Edits source folders in IntelliJ content entries */
public class SourceFolderEditor {
  private static final Logger LOG = Logger.getInstance(SourceFolderEditor.class);

  public static void modifyContentEntries(
      BlazeJavaImportResult importResult, Collection<ContentEntry> contentEntries) {

    Map<File, BlazeContentEntry> contentEntryMap = Maps.newHashMap();
    for (BlazeContentEntry contentEntry : importResult.contentEntries) {
      contentEntryMap.put(contentEntry.contentRoot, contentEntry);
    }

    for (ContentEntry contentEntry : contentEntries) {
      VirtualFile virtualFile = contentEntry.getFile();
      if (virtualFile == null) {
        continue;
      }

      File contentRoot = new File(virtualFile.getPath());
      BlazeContentEntry javaContentEntry = contentEntryMap.get(contentRoot);
      if (javaContentEntry != null) {
        for (BlazeSourceDirectory sourceDirectory : javaContentEntry.sources) {
          addSourceFolderToContentEntry(contentEntry, sourceDirectory);
        }
      }
    }
  }

  private static void addSourceFolderToContentEntry(
      ContentEntry contentEntry, BlazeSourceDirectory sourceDirectory) {
    File sourceDir = sourceDirectory.getDirectory();

    // Create the source folder
    SourceFolder sourceFolder;
    if (sourceDirectory.getIsResource()) {
      JavaResourceRootType resourceRootType =
          sourceDirectory.getIsTest()
              ? JavaResourceRootType.TEST_RESOURCE
              : JavaResourceRootType.RESOURCE;
      sourceFolder = contentEntry.addSourceFolder(pathToUrl(sourceDir.getPath()), resourceRootType);
    } else {
      sourceFolder =
          contentEntry.addSourceFolder(pathToUrl(sourceDir.getPath()), sourceDirectory.getIsTest());
    }
    JpsModuleSourceRoot sourceRoot = sourceFolder.getJpsElement();
    JpsElement properties = sourceRoot.getProperties();
    if (properties instanceof JavaSourceRootProperties) {
      JavaSourceRootProperties rootProperties = (JavaSourceRootProperties) properties;
      if (sourceDirectory.getIsGenerated()) {
        rootProperties.setForGeneratedSources(true);
      }
      String packagePrefix = sourceDirectory.getPackagePrefix();
      if (!Strings.isNullOrEmpty(packagePrefix)) {
        rootProperties.setPackagePrefix(packagePrefix);
      }
    }
  }

  @NotNull
  private static String pathToUrl(@NotNull String filePath) {
    filePath = FileUtil.toSystemIndependentName(filePath);
    if (filePath.endsWith(".srcjar") || filePath.endsWith(".jar")) {
      return URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR + filePath + URLUtil.JAR_SEPARATOR;
    } else if (filePath.contains("src.jar!")) {
      return URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR + filePath;
    } else {
      return VfsUtilCore.pathToUrl(filePath);
    }
  }
}

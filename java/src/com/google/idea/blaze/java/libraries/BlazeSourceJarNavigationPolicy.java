/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.libraries;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.BlazeSyncModificationTracker;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.compiled.ClsCustomNavigationPolicyEx;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import java.io.File;
import javax.annotation.Nullable;

/**
 * A navigation policy that allows navigating to source (or reading javadocs) even when that source
 * isn't officially attached to the library in the project.
 *
 * <p>Attaching sources has been shown to slow indexing time because IntelliJ indexes all the source
 * files attached to project jars. This isn't a huge problem for non-Blaze projects, since the jars
 * change infrequently. With blaze, however, the jars are reshuffled after every blaze build and so
 * the indexing time increases dramatically if you attach too many of them.
 *
 * <p>This class attempts to work around that problem by providing a way to navigate to the source
 * without having that source actually indexed.
 */
final class BlazeSourceJarNavigationPolicy extends ClsCustomNavigationPolicyEx {

  @Nullable
  @Override
  public PsiFile getFileNavigationElement(ClsFileImpl file) {
    return CachedValuesManager.getCachedValue(
        file,
        () -> {
          Result<PsiFile> result = getPsiFile(file);
          if (result == null) {
            result = notFound(file);
          }
          return result;
        });
  }

  @Nullable
  private Result<PsiFile> getPsiFile(ClsFileImpl file) {
    Project project = file.getProject();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }

    VirtualFile root = getSourceJarRoot(project, blazeProjectData, file);
    if (root == null) {
      return null;
    }

    return getSourceFileResult(file, root);
  }

  @Nullable
  private VirtualFile getSourceJarRoot(
      Project project, BlazeProjectData blazeProjectData, PsiJavaFile clsFile) {

    Library library = findLibrary(project, clsFile);
    if (library == null || library.getFiles(OrderRootType.SOURCES).length != 0) {
      // If the library already has sources attached, no need to hunt for them.
      return null;
    }

    BlazeJarLibrary blazeLibrary =
        LibraryActionHelper.findLibraryFromIntellijLibrary(project, blazeProjectData, library);
    if (blazeLibrary == null) {
      return null;
    }

    // TODO: If there are multiple source jars, search for one containing this PsiJavaFile.
    for (ArtifactLocation jar : blazeLibrary.libraryArtifact.getSourceJars()) {
      VirtualFile root =
          getSourceJarRoot(project, blazeProjectData.getArtifactLocationDecoder(), jar);
      if (root != null) {
        return root;
      }
    }
    return null;
  }

  @Nullable
  private static VirtualFile getSourceJarRoot(
      Project project, ArtifactLocationDecoder decoder, ArtifactLocation sourceJar) {
    File sourceJarFile = JarCache.getInstance(project).getCachedSourceJar(decoder, sourceJar);
    if (sourceJar == null) {
      return null;
    }
    VirtualFile vfsFile = VfsUtils.resolveVirtualFile(sourceJarFile);
    if (vfsFile == null) {
      return null;
    }
    return JarFileSystem.getInstance().getJarRootForLocalFile(vfsFile);
  }

  @Nullable
  private Library findLibrary(Project project, PsiJavaFile clsFile) {
    OrderEntry libraryEntry = LibraryUtil.findLibraryEntry(clsFile.getVirtualFile(), project);
    if (!(libraryEntry instanceof LibraryOrderEntry)) {
      return null;
    }
    return ((LibraryOrderEntry) libraryEntry).getLibrary();
  }

  @Nullable
  private Result<PsiFile> getSourceFileResult(ClsFileImpl clsFile, VirtualFile root) {
    // This code is adapted from JavaPsiImplementationHelperImpl#getClsFileNavigationElement
    PsiClass[] classes = clsFile.getClasses();
    if (classes.length == 0) {
      return null;
    }

    String sourceFileName = ((ClsClassImpl) classes[0]).getSourceFileName();
    String packageName = clsFile.getPackageName();
    String relativePath =
        packageName.isEmpty()
            ? sourceFileName
            : packageName.replace('.', '/') + '/' + sourceFileName;

    VirtualFile source = root.findFileByRelativePath(relativePath);
    if (source != null && source.isValid()) {
      // Since we have an actual source jar tracked down, use that source jar as the modification
      // tracker. This means the result will continue to be cached unless that source jar changes.
      // If we didn't find a source jar, we use a modification tracker that invalidates on every
      // Blaze sync, which is less efficient.
      PsiFile psiSource = clsFile.getManager().findFile(source);
      if (psiSource instanceof PsiClassOwner) {
        return Result.create(psiSource, source);
      }
      return Result.create(null, source);
    }

    return null;
  }

  private Result<PsiFile> notFound(ClsFileImpl file) {
    // A "not-found" result is null, but depends on the project sync tracker, so it will expire
    // after the next blaze sync. This means we'll run this check again after every sync for files
    // that don't have source jars, but it's not a huge deal because checking for the source jar
    // only takes a few microseconds.
    return Result.create(null, BlazeSyncModificationTracker.getInstance(file.getProject()));
  }
}

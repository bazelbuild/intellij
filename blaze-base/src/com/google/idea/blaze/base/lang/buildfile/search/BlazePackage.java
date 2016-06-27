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
package com.google.idea.blaze.base.lang.buildfile.search;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.history.core.Paths;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PackagePrefixFileSystemItem;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Defines the files accessible by a given blaze package.
 */
public class BlazePackage {

  @Nullable
  public static BlazePackage getContainingPackage(PsiFileSystemItem file) {
    if (file instanceof PsiFile) {
      file = ((PsiFile) file).getOriginalFile();
    }
    if (file instanceof BuildFile && file.getName().equals("BUILD")) {
      return new BlazePackage((BuildFile) file);
    }
    return getContainingPackage(getPsiDirectory(file));
  }

  @Nullable
  private static PsiDirectory getPsiDirectory(PsiFileSystemItem file) {
    if (file instanceof PsiDirectory) {
      return (PsiDirectory) file;
    }
    if (file instanceof PsiFile) {
      return ((PsiFile) file).getContainingDirectory();
    }
    if (file instanceof PackagePrefixFileSystemItem) {
      return ((PackagePrefixFileSystemItem) file).getDirectory();
    }
    return null;
  }

  @Nullable
  public static BlazePackage getContainingPackage(@Nullable PsiDirectory dir) {
    while (dir != null) {
      PsiFile buildFile = dir.findFile("BUILD");
      if (buildFile != null) {
        return buildFile instanceof BuildFile ? new BlazePackage((BuildFile) buildFile) : null;
      }
      dir = dir.getParentDirectory();
    }
    return null;
  }

  public final BuildFile buildFile;

  private BlazePackage(BuildFile buildFile) {
    this.buildFile = buildFile;
  }

  @Nullable
  public PsiDirectory getContainingDirectory() {
    return buildFile.getParent();
  }

  /**
   * The search scope corresponding to this package (i.e. not crossing package boundaries).
   * @param onlyBlazeFiles if true, the scope is limited to BUILD and Skylark files.
   */
  public GlobalSearchScope getSearchScope(boolean onlyBlazeFiles) {
    return new BlazePackageSearchScope(this, onlyBlazeFiles);
  }

  /**
   * Returns the file path relative to this blaze package, or null if it does lie inside this package
   */
  @Nullable
  public String getPackageRelativePath(String filePath) {
    String packageFilePath = PathUtil.getParentPath(buildFile.getFilePath());
    return Paths.relativeIfUnder(filePath, packageFilePath);
  }

  /**
   * The path from the blaze package directory to the child file, or null if
   * the package directory is not an ancestor of the provided file.
   */
  @Nullable
  public String getRelativePathToChild(@Nullable VirtualFile child) {
    if (child == null) {
      return null;
    }
    String packagePath = PathUtil.getParentPath(buildFile.getFilePath());
    return Paths.relativeIfUnder(child.getPath(), packagePath);
  }

  /**
   * Walks the directory tree, processing all files accessible by this package (i.e. not processing child packages).
   */
  public void processPackageFiles(Processor<PsiFile> processor) {
    PsiDirectory dir = getContainingDirectory();
    if (dir == null) {
      return;
    }
    processPackageFiles(processor, dir);
  }

  private static void processPackageFiles(Processor<PsiFile> processor, PsiDirectory directory) {
    processDirectory(processor, directory);
    for (PsiDirectory child : directory.getSubdirectories()) {
      if (!isBlazePackage(child)) {
        processPackageFiles(processor, directory);
      }
    }
  }

  private static boolean isBlazePackage(PsiDirectory directory) {
    return directory.findFile("BUILD") != null;
  }

  private static void processDirectory(Processor<PsiFile> processor, PsiDirectory directory) {
    for (PsiFile file : directory.getFiles()) {
      processor.process(file);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof BlazePackage)) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    BlazePackage that = (BlazePackage) obj;
    return Objects.equals(buildFile.getFilePath(), that.buildFile.getFilePath());
  }

  @Override
  public int hashCode() {
    return Objects.hash(buildFile.getFilePath());
  }

  @Override
  public String toString() {
    return String.format("%s package: %s", Blaze.buildSystemName(buildFile.getProject()), buildFile.getPackageWorkspacePath());
  }
}
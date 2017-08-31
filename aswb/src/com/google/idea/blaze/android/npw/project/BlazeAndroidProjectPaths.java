/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.npw.project;

import com.android.SdkConstants;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import java.io.File;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Project paths for a Blaze Android project.
 *
 * <p>We mostly just take whatever directory the user specified and put the new component there.
 * Unlike Gradle, Blaze has no strict requirements regarding the structure of an Android project,
 * but there are some common conventions:
 *
 * <pre>
 * google3/
 * |-java/com/google/foo/bar/... (module root)
 * | |-BUILD
 * | |-AndroidManifest.xml (manifest directory)
 * | |-Baz.java (source directory of com.google.foo.bar.Baz)
 * | |-Baz.aidl (aidl directory, option 1)
 * | |-aidl/
 * | | `-com/google/foo/bar/Baz.aidl (aidl directory, option 2)
 * | `-res/... (res directory, one of the few things required by the build system)
 * `-javatest/com/google/foo/bar/...
 *   |-BUILD
 *   `-BazTest.java (test directory of com.google.foo.bar.BazTest)
 * </pre>
 *
 * However, this is also possible (package name unrelated to directory structure):
 *
 * <pre>
 * google3/experimental/users/foo/my/own/project/
 * |-Baz.java (com.google.foo.bar.Baz)
 * `-BazTest.java (com.google.foo.bar.BazTest)
 * </pre>
 *
 * So is this (versioned paths that aren't reflected by the package name):
 *
 * <pre>
 * google3/third_party/com/google/foo/bar/
 * |-v1/Baz.java (com.google.foo.bar.Baz)
 * `-v2/Baz.java (com.google.foo.bar.Baz)
 * </pre>
 */
public class BlazeAndroidProjectPaths implements AndroidProjectPaths {
  @Nullable private File moduleRoot;
  @Nullable private File srcDirectory;
  @Nullable private File resDirectory;

  @Nullable
  @Override
  public File getModuleRoot() {
    return moduleRoot;
  }

  @Nullable
  @Override
  public File getSrcDirectory(@Nullable String packageName) {
    return srcDirectory;
  }

  @Nullable
  @Override
  public File getTestDirectory(@Nullable String packageName) {
    return srcDirectory;
  }

  @Nullable
  @Override
  public File getResDirectory() {
    return resDirectory;
  }

  @Nullable
  @Override
  public File getAidlDirectory(@Nullable String packageName) {
    return srcDirectory;
  }

  @Nullable
  @Override
  public File getManifestDirectory() {
    return srcDirectory;
  }

  /**
   * The new component wizard uses {@link AndroidSourceSet#getName()} for the default package name
   * of the new component. If we can figure it out from the target directory here, then we can pass
   * it to the new component wizard.
   */
  private static String getPackageName(Project project, VirtualFile targetDirectory) {
    PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(targetDirectory);
    if (psiDirectory == null) {
      return null;
    }
    PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
    if (psiPackage == null) {
      return null;
    }
    return psiPackage.getQualifiedName();
  }

  public static List<AndroidSourceSet> getSourceSets(
      AndroidFacet androidFacet, @Nullable VirtualFile targetDirectory) {
    Module module = androidFacet.getModule();
    BlazeAndroidProjectPaths paths = new BlazeAndroidProjectPaths();
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length > 0) {
      paths.moduleRoot = VfsUtilCore.virtualToIoFile(roots[0]);
    }

    // We have a res dir if this happens to be a resource module.
    SourceProvider sourceProvider = androidFacet.getMainSourceProvider();
    paths.resDirectory = Iterables.getFirst(sourceProvider.getResDirectories(), null);

    // If this happens to be a resource package,
    // the module name (resource package) would be more descriptive than the facet name (Android).
    // Otherwise, .workspace is still better than (Android).
    String name = androidFacet.getModule().getName();
    if (targetDirectory != null) {
      String packageName = getPackageName(module.getProject(), targetDirectory);
      if (packageName != null) {
        name = packageName;
      }
      paths.srcDirectory = VfsUtilCore.virtualToIoFile(targetDirectory);
    } else {
      // People usually put the manifest file with their sources.
      paths.srcDirectory = sourceProvider.getManifestFile().getParentFile();
    }
    if (paths.resDirectory == null) {
      paths.resDirectory = new File(paths.srcDirectory, SdkConstants.FD_RES);
    }
    return Collections.singletonList(new AndroidSourceSet(name, paths));
  }
}

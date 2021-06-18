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
package com.google.idea.blaze.android.sync.model.idea;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.ClassJarProvider;
import com.android.tools.lint.detector.api.Desugaring;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.build.BlazeBuildService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import java.io.File;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

/**
 * Contains Android-Blaze related state necessary for configuring an IDEA project based on a
 * user-selected build variant.
 */
abstract class BlazeAndroidModelBase implements AndroidModel {
  private final Project project;
  private final ListenableFuture<String> applicationId;
  private final int minSdkVersion;
  private final boolean desugarJava8Libs;

  protected BlazeAndroidModelBase(
      Project project,
      File rootDirPath,
      ListenableFuture<String> applicationId,
      int minSdkVersion,
      boolean desugarJava8Libs) {
    this.project = project;
    this.applicationId = applicationId;
    this.minSdkVersion = minSdkVersion;
    this.desugarJava8Libs = desugarJava8Libs;
  }

  @Override
  public String getApplicationId() {
    try {
      return applicationId.get(1, SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (TimeoutException | ExecutionException e) {
      Logger.getInstance(BlazeAndroidModelBase.class).warn("Application Id not initialized yet", e);
    }
    return uninitializedApplicationId();
  }

  protected abstract String uninitializedApplicationId();

  @Override
  public Set<String> getAllApplicationIds() {
    return ImmutableSet.of(getApplicationId());
  }

  @Override
  public boolean overridesManifestPackage() {
    return false;
  }

  @Override
  public Boolean isDebuggable() {
    return true;
  }

  @Override
  @Nullable
  public AndroidVersion getMinSdkVersion() {
    return new AndroidVersion(minSdkVersion, null);
  }

  @Nullable
  @Override
  public AndroidVersion getRuntimeMinSdkVersion() {
    return getMinSdkVersion();
  }

  @Nullable
  @Override
  public AndroidVersion getTargetSdkVersion() {
    return null;
  }

  @Override
  public boolean isGenerated(VirtualFile file) {
    return false;
  }

  @Override
  public ClassJarProvider getClassJarProvider() {
    return new BlazeClassJarProvider(project);
  }

  @Override
  public boolean isClassFileOutOfDate(Module module, String fqcn, VirtualFile classFile) {
    return testIsClassFileOutOfDate(project, fqcn, classFile);
  }

  public static boolean testIsClassFileOutOfDate(
      Project project, String fqcn, VirtualFile classFile) {
    VirtualFile sourceFile =
        ApplicationManager.getApplication()
            .runReadAction(
                (Computable<VirtualFile>)
                    () -> {
                      PsiClass psiClass =
                          JavaPsiFacade.getInstance(project)
                              .findClass(fqcn, GlobalSearchScope.projectScope(project));
                      if (psiClass == null) {
                        return null;
                      }
                      PsiFile psiFile = psiClass.getContainingFile();
                      if (psiFile == null) {
                        return null;
                      }
                      return psiFile.getVirtualFile();
                    });
    if (sourceFile == null) {
      return false;
    }

    // Edited but not yet saved?
    if (FileDocumentManager.getInstance().isFileModified(sourceFile)) {
      return true;
    }

    long sourceTimeStamp = sourceFile.getTimeStamp();
    long buildTimeStamp = classFile.getTimeStamp();

    if (classFile.getFileSystem() instanceof JarFileSystem) {
      JarFileSystem jarFileSystem = (JarFileSystem) classFile.getFileSystem();
      VirtualFile jarFile = jarFileSystem.getVirtualFileForJar(classFile);
      if (jarFile != null) {
        if (jarFile.getFileSystem() instanceof LocalFileSystem) {
          // The virtual file timestamp could be stale since we don't watch this file.
          buildTimeStamp = VfsUtilCore.virtualToIoFile(jarFile).lastModified();
        } else {
          buildTimeStamp = jarFile.getTimeStamp();
        }
      }
    }

    if (sourceTimeStamp > buildTimeStamp) {
      // It's possible that the source file's timestamp has been updated, but the content remains
      // same. In this case, blaze will not try to rebuild the jar, we have to also check whether
      // the user recently clicked the build button. So they can at least manually get rid of the
      // error.
      Long projectBuildTimeStamp = BlazeBuildService.getLastBuildTimeStamp(project);
      return projectBuildTimeStamp == null || sourceTimeStamp > projectBuildTimeStamp;
    }

    return false;
  }

  @Override
  public Set<Desugaring> getDesugaring() {
    return desugarJava8Libs ? Desugaring.FULL : Desugaring.DEFAULT;
  }
}

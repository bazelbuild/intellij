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

import com.android.builder.model.SourceProvider;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.ClassJarProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.idea.blaze.android.manifest.ManifestParser;
import com.google.idea.blaze.base.actions.BlazeBuildService;
import com.intellij.openapi.application.ApplicationManager;
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
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.android.dom.manifest.Manifest;

/**
 * Contains Android-Blaze related state necessary for configuring an IDEA project based on a
 * user-selected build variant.
 */
public class BlazeAndroidModel implements AndroidModel {
  private Project project;
  private final File rootDirPath;
  private final SourceProvider sourceProvider;
  private final List<SourceProvider> sourceProviders; // Singleton list of sourceProvider
  private final File moduleManifest;
  private final String resourceJavaPackage;
  private final int minSdkVersion;

  /** Creates a new {@link BlazeAndroidModel}. */
  public BlazeAndroidModel(
      Project project,
      Module module,
      File rootDirPath,
      SourceProvider sourceProvider,
      File moduleManifest,
      String resourceJavaPackage,
      int minSdkVersion) {
    this.project = project;
    this.rootDirPath = rootDirPath;
    this.sourceProvider = sourceProvider;
    this.sourceProviders = ImmutableList.of(sourceProvider);
    this.moduleManifest = moduleManifest;
    this.resourceJavaPackage = resourceJavaPackage;
    this.minSdkVersion = minSdkVersion;
  }

  @Override
  public SourceProvider getDefaultSourceProvider() {
    return sourceProvider;
  }

  @Override
  public List<SourceProvider> getActiveSourceProviders() {
    return sourceProviders;
  }

  @Override
  public List<SourceProvider> getTestSourceProviders() {
    return sourceProviders;
  }

  @Override
  public List<SourceProvider> getAllSourceProviders() {
    return sourceProviders;
  }

  @Override
  public String getApplicationId() {
    // Run in a read action since otherwise, it might throw a read access exception.
    return ApplicationManager.getApplication()
        .runReadAction(
            (Computable<String>)
                () -> {
                  Manifest manifest =
                      ManifestParser.getInstance(project).getManifest(moduleManifest);
                  if (manifest == null) {
                    return resourceJavaPackage;
                  }
                  String packageName = manifest.getPackage().getValue();
                  return packageName == null ? resourceJavaPackage : packageName;
                });
  }

  @Override
  public Set<String> getAllApplicationIds() {
    Set<String> applicationIds = Sets.newHashSet();
    applicationIds.add(getApplicationId());
    return applicationIds;
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

  @Nullable
  @Override
  public Integer getVersionCode() {
    return null;
  }

  @Override
  public File getRootDirPath() {
    return rootDirPath;
  }

  @Override
  public boolean isGenerated(VirtualFile file) {
    return false;
  }

  @Override
  public VirtualFile getRootDir() {
    File rootDirPath = getRootDirPath();
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(rootDirPath);
    assert virtualFile != null;
    return virtualFile;
  }

  @Override
  public boolean getDataBindingEnabled() {
    return false;
  }

  @Override
  public ClassJarProvider getClassJarProvider() {
    return new BlazeClassJarProvider(project);
  }

  @Override
  public boolean isClassFileOutOfDate(Module module, String fqcn, VirtualFile classFile) {
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
}

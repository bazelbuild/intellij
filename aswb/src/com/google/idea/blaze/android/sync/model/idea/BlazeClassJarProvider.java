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

import com.android.SdkConstants;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
import com.google.idea.sdkcompat.android.res.AppResourceRepositoryAdapter;
import com.google.idea.sdkcompat.android.sync.model.idea.ClassJarProviderCompat;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Collects class jars from the user's build. */
public class BlazeClassJarProvider extends ClassJarProviderCompat {

  private final Project project;
  private final AtomicBoolean pendingJarsRefresh;

  public BlazeClassJarProvider(final Project project) {
    this.project = project;
    this.pendingJarsRefresh = new AtomicBoolean(false);
  }

  @Override
  @Nullable
  public VirtualFile findModuleClassFile(String className, Module module) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }

    TargetMap targetMap = blazeProjectData.targetMap;
    ArtifactLocationDecoder decoder = blazeProjectData.artifactLocationDecoder;
    AndroidResourceModuleRegistry registry = AndroidResourceModuleRegistry.getInstance(project);
    TargetIdeInfo target = blazeProjectData.targetMap.get(registry.getTargetKey(module));

    if (target == null || target.javaIdeInfo == null) {
      return null;
    }

    // As a potential optimization, we could choose an arbitrary android_binary target
    // that depends on the library to provide a single complete resource jar,
    // instead of having to rely on dynamic class generation.
    // TODO: benchmark to see if optimization is worthwhile.

    String classNamePath = className.replace('.', File.separatorChar) + SdkConstants.DOT_CLASS;

    List<LibraryArtifact> jarsToSearch = Lists.newArrayList(target.javaIdeInfo.jars);
    jarsToSearch.addAll(
        TransitiveDependencyMap.getInstance(project)
            .getTransitiveDependencies(target.key)
            .stream()
            .map(targetMap::get)
            .filter(Objects::nonNull)
            .flatMap(BlazeClassJarProvider::getNonResourceJars)
            .collect(Collectors.toList()));

    List<File> missingClassJars = Lists.newArrayList();
    for (LibraryArtifact jar : jarsToSearch) {
      if (jar.classJar == null || jar.classJar.isSource()) {
        continue;
      }
      File classJarFile = decoder.decode(jar.classJar);
      VirtualFile classJarVF =
          VirtualFileSystemProvider.getInstance().getSystem().findFileByIoFile(classJarFile);
      if (classJarVF == null) {
        if (classJarFile.exists()) {
          missingClassJars.add(classJarFile);
        }
        continue;
      }
      VirtualFile classFile = findClassInJar(classJarVF, classNamePath);
      if (classFile != null) {
        return classFile;
      }
    }

    maybeRefreshJars(missingClassJars, pendingJarsRefresh);
    return null;
  }

  private static Stream<LibraryArtifact> getNonResourceJars(TargetIdeInfo target) {
    if (target.javaIdeInfo == null) {
      return null;
    }
    Stream<LibraryArtifact> jars = target.javaIdeInfo.jars.stream();
    if (target.androidIdeInfo != null) {
      jars = jars.filter(jar -> !jar.equals(target.androidIdeInfo.resourceJar));
    }
    return jars;
  }

  @Nullable
  private static VirtualFile findClassInJar(final VirtualFile classJar, String classNamePath) {
    VirtualFile jarRoot = getJarRootForLocalFile(classJar);
    if (jarRoot == null) {
      return null;
    }
    return jarRoot.findFileByRelativePath(classNamePath);
  }

  @Override
  public List<File> getModuleExternalLibrariesCompat(Module module) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (blazeProjectData == null) {
      return ImmutableList.of();
    }

    TargetMap targetMap = blazeProjectData.targetMap;
    ArtifactLocationDecoder decoder = blazeProjectData.artifactLocationDecoder;

    AndroidResourceModuleRegistry registry = AndroidResourceModuleRegistry.getInstance(project);
    TargetIdeInfo target = targetMap.get(registry.getTargetKey(module));

    if (target == null) {
      return ImmutableList.of();
    }

    AppResourceRepository repository = AppResourceRepositoryAdapter.getOrCreateInstance(module);
    ImmutableList.Builder<File> results = ImmutableList.builder();
    for (TargetKey dependencyTargetKey :
        TransitiveDependencyMap.getInstance(project).getTransitiveDependencies(target.key)) {
      TargetIdeInfo dependencyTarget = targetMap.get(dependencyTargetKey);
      if (dependencyTarget == null) {
        continue;
      }

      // Add all import jars as external libraries.
      JavaIdeInfo javaIdeInfo = dependencyTarget.javaIdeInfo;
      if (javaIdeInfo != null) {
        for (LibraryArtifact jar : javaIdeInfo.jars) {
          if (jar.classJar != null && jar.classJar.isSource()) {
            results.add(decoder.decode(jar.classJar));
          }
        }
      }

      // Tell ResourceClassRegistry which repository contains our resources and the java packages of
      // the resources that we're interested in.
      // When the class loader tries to load a custom view, and the view references resource
      // classes, layoutlib will ask the class loader for these resource classes.
      // If these resource classes are in a separate jar from the target (i.e., in a dependency),
      // then offering their jars will lead to a conflict in the resource IDs.
      // So instead, the resource class generator will produce dummy resource classes with
      // non-conflicting IDs to satisfy the class loader.
      // The resource repository remembers the dynamic IDs that it handed out and when the layoutlib
      // calls to ask about the name and content of a given resource ID, the repository can just
      // answer what it has already stored.
      AndroidIdeInfo androidIdeInfo = dependencyTarget.androidIdeInfo;
      if (androidIdeInfo != null && repository != null) {
        ResourceClassRegistry.get(module.getProject())
            .addLibrary(repository, androidIdeInfo.resourceJavaPackage);
      }
    }

    return results.build();
  }

  private static void maybeRefreshJars(Collection<File> missingJars, AtomicBoolean pendingRefresh) {
    // We probably need to refresh the virtual file system to find these files, but we can't refresh
    // here because we're in a read action. We also can't use the async refreshIoFiles since it
    // still tries to refresh the IO files synchronously. A global async refresh can't find new
    // files in the ObjFS since we're not watching it.
    // We need to do our own asynchronous refresh, and guard it with a flag to prevent the event
    // queue from overflowing.
    if (!missingJars.isEmpty() && !pendingRefresh.getAndSet(true)) {
      ApplicationManager.getApplication()
          .invokeLater(
              () -> {
                LocalFileSystem.getInstance().refreshIoFiles(missingJars);
                pendingRefresh.set(false);
              },
              ModalityState.NON_MODAL);
    }
  }

  private static VirtualFile getJarRootForLocalFile(VirtualFile file) {
    return ApplicationManager.getApplication().isUnitTestMode()
        ? TempFileSystem.getInstance().findFileByPath(file.getPath() + JarFileSystem.JAR_SEPARATOR)
        : JarFileSystem.getInstance().getJarRootForLocalFile(file);
  }
}

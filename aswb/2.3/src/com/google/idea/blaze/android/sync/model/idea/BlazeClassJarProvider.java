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
import com.android.tools.idea.model.ClassJarProvider;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.ResourceClassRegistry;
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.util.containers.OrderedSet;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.Nullable;

/** Collects class jars from the user's build. */
public class BlazeClassJarProvider extends ClassJarProvider {

  private final Project project;
  private AtomicBoolean pendingModuleJarsRefresh;
  private AtomicBoolean pendingDependencyJarsRefresh;

  public BlazeClassJarProvider(final Project project) {
    this.project = project;
    this.pendingModuleJarsRefresh = new AtomicBoolean(false);
    this.pendingDependencyJarsRefresh = new AtomicBoolean(false);
  }

  @Override
  @Nullable
  public VirtualFile findModuleClassFile(String className, Module module) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }

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

    List<File> missingClassJars = Lists.newArrayList();
    for (LibraryArtifact jar : target.javaIdeInfo.jars) {
      if (jar.classJar == null) {
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

    maybeRefreshJars(missingClassJars, pendingModuleJarsRefresh);
    return null;
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
  public List<VirtualFile> getModuleExternalLibraries(Module module) {
    OrderedSet<VirtualFile> results = new OrderedSet<>();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (blazeProjectData == null) {
      return results;
    }

    TargetMap targetMap = blazeProjectData.targetMap;
    ArtifactLocationDecoder decoder = blazeProjectData.artifactLocationDecoder;

    AndroidResourceModuleRegistry registry = AndroidResourceModuleRegistry.getInstance(project);
    TargetIdeInfo target = targetMap.get(registry.getTargetKey(module));

    if (target == null) {
      return results;
    }

    AppResourceRepository repository = AppResourceRepository.getAppResources(module, true);

    List<File> missingClassJars = Lists.newArrayList();
    for (TargetKey dependencyTargetKey :
        TransitiveDependencyMap.getInstance(project).getTransitiveDependencies(target.key)) {
      TargetIdeInfo dependencyTarget = targetMap.get(dependencyTargetKey);
      if (dependencyTarget == null) {
        continue;
      }
      JavaIdeInfo javaIdeInfo = dependencyTarget.javaIdeInfo;
      AndroidIdeInfo androidIdeInfo = dependencyTarget.androidIdeInfo;

      // Add all non-resource jars to be searched.
      // Multiple resource jars will have ID conflicts unless generated dynamically.
      if (javaIdeInfo != null) {
        for (LibraryArtifact jar : javaIdeInfo.jars) {
          if (androidIdeInfo != null && jar.equals(androidIdeInfo.resourceJar)) {
            // No resource jars.
            continue;
          }
          // Some of these could be empty class jars from resource only android_library targets.
          // A potential optimization could be to filter out jars like these,
          // so we don't waste time fetching and searching them.
          // TODO: benchmark to see if optimization is worthwhile.
          if (jar.classJar != null) {
            File classJarFile = decoder.decode(jar.classJar);
            VirtualFile classJar =
                VirtualFileSystemProvider.getInstance().getSystem().findFileByIoFile(classJarFile);
            if (classJar != null) {
              results.add(classJar);
            } else if (classJarFile.exists()) {
              missingClassJars.add(classJarFile);
            }
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
      if (androidIdeInfo != null && repository != null) {
        ResourceClassRegistry.get(module.getProject())
            .addLibrary(repository, androidIdeInfo.resourceJavaPackage);
      }
    }

    maybeRefreshJars(missingClassJars, pendingDependencyJarsRefresh);
    return results;
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

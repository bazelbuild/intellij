/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.projectsystem;

import com.android.SdkConstants;
import com.android.tools.idea.project.ModuleBasedClassFileFinder;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
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

/**
 * Blaze-specific implementation of the {@link com.android.tools.idea.projectsystem.ClassFileFinder}
 * interface. TransitiveClosureClassFileFinder searches for class files by looking at the output
 * jars belonging to each Blaze target in the transitive closure of the target corresponding to each
 * resource module.
 *
 * <p>TODO: Prevent duplicate checking of Blaze targets. The findClassFileInModule method gets
 * called for every module in the project since they all transitively depend on each other, and the
 * corresponding Blaze targets have many overlapping transitive dependencies.
 */
public class TransitiveClosureClassFileFinder extends ModuleBasedClassFileFinder
    implements BlazeClassFileFinder {
  private final AtomicBoolean pendingJarsRefresh;

  public TransitiveClosureClassFileFinder(Module module) {
    super(module);
    pendingJarsRefresh = new AtomicBoolean(false);
  }

  @Override
  public boolean shouldSkipResourceRegistration() {
    return false;
  }

  @Override
  @Nullable
  protected VirtualFile findClassFileInModule(Module module, String className) {
    VirtualFile classFile = super.findClassFileInModule(module, className);
    if (classFile != null) {
      return classFile;
    }

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(module.getProject()).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }

    TargetMap targetMap = blazeProjectData.getTargetMap();
    ArtifactLocationDecoder decoder = blazeProjectData.getArtifactLocationDecoder();
    AndroidResourceModuleRegistry registry =
        AndroidResourceModuleRegistry.getInstance(module.getProject());
    TargetIdeInfo target = blazeProjectData.getTargetMap().get(registry.getTargetKey(module));

    if (target == null || target.getJavaIdeInfo() == null) {
      return null;
    }

    // As a potential optimization, we could choose an arbitrary android_binary target
    // that depends on the library to provide a single complete resource jar,
    // instead of having to rely on dynamic class generation.
    // TODO: benchmark to see if optimization is worthwhile.

    String classNamePath = className.replace('.', File.separatorChar) + SdkConstants.DOT_CLASS;

    List<LibraryArtifact> jarsToSearch = Lists.newArrayList(target.getJavaIdeInfo().getJars());
    jarsToSearch.addAll(
        TransitiveDependencyMap.getInstance(module.getProject())
            .getTransitiveDependencies(target.getKey()).stream()
            .map(targetMap::get)
            .filter(Objects::nonNull)
            .flatMap(TransitiveClosureClassFileFinder::getNonResourceJars)
            .collect(Collectors.toList()));

    List<File> missingClassJars = Lists.newArrayList();
    for (LibraryArtifact jar : jarsToSearch) {
      if (jar.getClassJar() == null || jar.getClassJar().isSource()) {
        continue;
      }
      File classJarFile = decoder.decode(jar.getClassJar());
      VirtualFile classJarVF =
          VirtualFileSystemProvider.getInstance().getSystem().findFileByIoFile(classJarFile);
      if (classJarVF == null) {
        if (classJarFile.exists()) {
          missingClassJars.add(classJarFile);
        }
        continue;
      }
      classFile = findClassInJar(classJarVF, classNamePath);
      if (classFile != null) {
        return classFile;
      }
    }

    maybeRefreshJars(missingClassJars, pendingJarsRefresh);
    return null;
  }

  public static Stream<LibraryArtifact> getNonResourceJars(TargetIdeInfo target) {
    if (target.getJavaIdeInfo() == null) {
      return null;
    }
    Stream<LibraryArtifact> jars = target.getJavaIdeInfo().getJars().stream();
    if (target.getAndroidIdeInfo() != null) {
      jars = jars.filter(jar -> !jar.equals(target.getAndroidIdeInfo().getResourceJar()));
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

  private static VirtualFile getJarRootForLocalFile(VirtualFile file) {
    return ApplicationManager.getApplication().isUnitTestMode()
        ? TempFileSystem.getInstance().findFileByPath(file.getPath() + JarFileSystem.JAR_SEPARATOR)
        : JarFileSystem.getInstance().getJarRootForLocalFile(file);
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
}

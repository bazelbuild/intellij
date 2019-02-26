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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.tools.idea.projectsystem.ClassFileFinder;
import com.android.tools.idea.projectsystem.ClassFileFinderUtil;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.base.Strings;
import com.google.idea.blaze.android.ResourceRepositoryManagerCompat;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import java.io.File;
import org.jetbrains.annotations.Nullable;

/**
 * A Blaze-specific implementation of the {@link ClassFileFinder} interface which searches for class
 * files by finding the corresponding source file, passing this to {@link SourceToTargetMap} to
 * retrieve the corresponding Blaze target, and then checking that target's output jars for the
 * class file.
 *
 * <p>This class is meant to eventually replace {@link TransitiveClosureClassFileFinder}, but until
 * then it is in experimental status under the blaze.class.file.finder.name flag.
 */
public class PsiBasedClassFileFinder implements BlazeClassFileFinder {
  private final Module module;
  private final Project project;

  public PsiBasedClassFileFinder(Module module) {
    this.module = module;
    project = module.getProject();
  }

  @Override
  public boolean shouldSkipResourceRegistration() {
    // PsiBasedClassFileFinder handles registering resource packages as it needs them.
    return true;
  }

  @Nullable
  @Override
  public VirtualFile findClassFile(String fqcn) {
    GlobalSearchScope searchScope = module.getModuleRuntimeScope(false);

    PsiClass[] psiClasses =
        ReadAction.compute(
            () ->
                JavaPsiFacade.getInstance(project)
                    .findClasses(getContainingClassName(fqcn), searchScope));
    if (psiClasses.length == 0) {
      return null;
    }

    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }

    // It's possible that there's more than one source file in the project corresponding to
    // the same fully-qualified class name, with Blaze choosing the appropriate source to use
    // according to some configuration flags. Here we check each of them until we find the one
    // that was chosen during Blaze sync.
    for (PsiClass psiClass : psiClasses) {
      PsiFile psiFile = psiClass.getContainingFile();
      if (psiFile == null) {
        continue;
      }

      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) {
        continue;
      }

      FileType fileType = psiFile.getFileType();
      VirtualFile classFile = null;

      if (fileType == StdFileTypes.JAVA) {
        classFile =
            findClassFileForSourceAndRegisterResourcePackage(projectData, virtualFile, fqcn);
      } else if (fileType == StdFileTypes.CLASS) {
        classFile = findClassFileForIJarClass(projectData, virtualFile, fqcn);
      }

      if (classFile != null) {
        return classFile;
      }
    }

    return null;
  }

  @Nullable
  private VirtualFile findClassFileForSourceAndRegisterResourcePackage(
      BlazeProjectData projectData, VirtualFile source, String fqcn) {
    SourceToTargetMap sourceToTargetMap = SourceToTargetMap.getInstance(project);
    File sourceFile = new File(source.getPath());

    for (TargetKey targetKey : sourceToTargetMap.getRulesForSourceFile(sourceFile)) {
      TargetIdeInfo target = projectData.getTargetMap().get(targetKey);
      if (target == null || target.getJavaIdeInfo() == null) {
        continue;
      }

      for (LibraryArtifact jar : target.getJavaIdeInfo().getJars()) {
        if (jar.getClassJar() == null || jar.getClassJar().isSource()) {
          continue;
        }

        File classJarFile = projectData.getArtifactLocationDecoder().decode(jar.getClassJar());
        VirtualFile classJarVF =
            VirtualFileSystemProvider.getInstance().getSystem().findFileByIoFile(classJarFile);

        if (classJarVF != null) {
          VirtualFile classFile = findClassInJar(classJarVF, fqcn);

          if (classFile != null) {
            // We need to register the target's resource package with ResourceClassRegistry in case
            // the class we just found references something from its package's R.java. We enable
            // class
            // generation for such resource classes instead of using the ones Blaze has already
            // generated
            // because they come from separate output jars (which would lead to conflicting resource
            // IDs).
            registerResourcePackage(target);
            return classFile;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private VirtualFile findClassFileForIJarClass(
      BlazeProjectData projectData, VirtualFile ijarClass, String fqcn) {
    OrderEntry orderEntry = LibraryUtil.findLibraryEntry(ijarClass, project);
    if (!(orderEntry instanceof LibraryOrderEntry)) {
      return null;
    }

    Library intellijLibrary = ((LibraryOrderEntry) orderEntry).getLibrary();
    if (intellijLibrary == null) {
      return null;
    }

    BlazeJavaSyncData syncData = projectData.getSyncState().get(BlazeJavaSyncData.class);
    if (syncData == null) {
      return null;
    }

    LibraryKey libraryKey = LibraryKey.fromIntelliJLibraryName(intellijLibrary.getName());
    BlazeJarLibrary blazeLibrary = syncData.getImportResult().libraries.get(libraryKey);

    File classJarFile =
        projectData.getArtifactLocationDecoder().decode(blazeLibrary.libraryArtifact.getClassJar());
    VirtualFile classJar =
        VirtualFileSystemProvider.getInstance().getSystem().findFileByIoFile(classJarFile);

    return findClassInJar(classJar, fqcn);
  }

  private void registerResourcePackage(TargetIdeInfo resourceDependencyTarget) {
    AndroidIdeInfo androidIdeInfo = resourceDependencyTarget.getAndroidIdeInfo();
    if (androidIdeInfo == null || Strings.isNullOrEmpty(androidIdeInfo.getResourceJavaPackage())) {
      return;
    }

    ResourceRepositoryManager repositoryManager =
        ResourceRepositoryManagerCompat.getResourceRepositoryManager(module);

    ResourceIdManager idManager = ResourceIdManager.get(module);
    if (repositoryManager == null) {
      return;
    }

    // TODO(namespaces)
    ResourceNamespace namespace = ReadAction.compute(() -> repositoryManager.getNamespace());
    ResourceClassRegistry.get(module.getProject())
        .addLibrary(
            ResourceRepositoryManagerCompat.getAppResources(repositoryManager),
            idManager,
            androidIdeInfo.getResourceJavaPackage(),
            namespace);
  }

  private static String getContainingClassName(String fqcn) {
    int firstCashIndex = fqcn.indexOf('$');
    if (firstCashIndex < 0) {
      return fqcn;
    }
    return fqcn.substring(0, firstCashIndex);
  }

  @Nullable
  private static VirtualFile findClassInJar(final VirtualFile classJar, String fqcn) {
    VirtualFile jarRoot = getJarRootForLocalFile(classJar);
    if (jarRoot == null) {
      return null;
    }
    return ClassFileFinderUtil.findClassFileInOutputRoot(jarRoot, fqcn);
  }

  private static VirtualFile getJarRootForLocalFile(VirtualFile file) {
    return ApplicationManager.getApplication().isUnitTestMode()
        ? TempFileSystem.getInstance().findFileByPath(file.getPath() + JarFileSystem.JAR_SEPARATOR)
        : JarFileSystem.getInstance().getJarRootForLocalFile(file);
  }
}

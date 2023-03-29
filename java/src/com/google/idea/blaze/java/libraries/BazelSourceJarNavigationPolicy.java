/*
*  Adapted from https://github.com/JetBrains/intellij-community/blob/39dc99468200a8de1839f771df3ebe9af52a78b8/java/java-impl/src/com/intellij/psi/impl/JavaPsiImplementationHelperImpl.java
*/
package com.google.idea.blaze.java.libraries;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.compiled.ClsCustomNavigationPolicy;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


final class BazelSourceJarNavigationPolicy implements ClsCustomNavigationPolicy {

  // helper to return value from inside
  // inner class
  public class ValContainer<T> {
    private T val;

    public ValContainer() {
    }

    public ValContainer(T v) {
        this.val = v;
    }

    public T getVal() {
        return val;
    }

    public void setVal(T val) {
        this.val = val;
    }
  }

  private Stream<VirtualFile> findSourceRoots(VirtualFile file, Project myProject) {
    Stream<VirtualFile> modelRoots = ProjectFileIndex.SERVICE.getInstance(myProject).getOrderEntriesForFile(file).stream()
      .filter(entry -> entry instanceof LibraryOrSdkOrderEntry && entry.isValid())
      .flatMap(entry -> Stream.of(entry.getFiles(OrderRootType.SOURCES)));

    Stream<VirtualFile> synthRoots = AdditionalLibraryRootsProvider.EP_NAME.getExtensionList().stream()
      .flatMap(provider -> provider.getAdditionalProjectLibraries(myProject).stream())
      .filter(library -> library.contains(file, false, true))
      .flatMap(library -> library.getSourceRoots().stream());

    return Stream.concat(modelRoots, synthRoots);
  }

  @Nullable
  public PsiElement getNavigationElement(@NotNull ClsFileImpl clsFile) {
    Function<VirtualFile, VirtualFile> finder = null;
    Predicate<PsiFile> filter = null;

    PsiClass[] classes = clsFile.getClasses();
    if (classes.length > 0) {
      String sourceFileName = ((ClsClassImpl)classes[0]).getSourceFileName();
      String packageName = clsFile.getPackageName();
      String relativePath = packageName.isEmpty() ? sourceFileName : packageName.replace('.', '/') + '/' + sourceFileName;
      finder = (root) -> {
        VirtualFile sourceFile = root.findFileByRelativePath(relativePath);
        if (sourceFile != null) {
          // was able to find source file named
          // after Java package
          return sourceFile;
        }

        // attempt to find source file by file name only
        final ValContainer<VirtualFile> f = new ValContainer();
        VfsUtilCore.iterateChildrenRecursively(root, null, new ContentIterator() {
          @Override
          public boolean processFile(@NotNull final VirtualFile fileOrDir) {
            if (fileOrDir.getName().equals(sourceFileName)) {
              f.setVal(fileOrDir);
              return false;
            }
            return true;
          }
        });
        return f.getVal();
      };
      filter = PsiClassOwner.class::isInstance;
    }

    if (finder == null) return clsFile;

    Project myProject = clsFile.getProject();

    return findSourceRoots(clsFile.getContainingFile().getVirtualFile(), myProject)
      .map(finder)
      .filter(source -> source != null && source.isValid())
      .map(PsiManager.getInstance(myProject)::findFile)
      .filter(filter)
      .findFirst()
      .orElse(clsFile);
  }
}

package com.google.idea.sdkcompat.cpp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.preprocessor.OCImportGraph;
import java.util.Collection;

/** Compat utilities for {@link OCImportGraph}. */
public class OCImportGraphCompat {
  // #api193
  public static Collection<VirtualFile> getAllHeaderRoots(Project project, VirtualFile headerFile) {
    return OCImportGraph.getInstance(project).getAllHeaderRoots(headerFile);
  }
}

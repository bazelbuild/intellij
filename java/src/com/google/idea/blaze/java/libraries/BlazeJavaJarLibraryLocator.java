package com.google.idea.blaze.java.libraries;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;

class BlazeJavaJarLibraryLocator implements BlazeJarLibraryLocator {

  @Override
  public BlazeJarLibrary findLibraryFromIntellijLibrary(Project project, BlazeProjectData blazeProjectData, Library library) {
    return LibraryActionHelper.findLibraryFromIntellijLibrary(project, blazeProjectData, library);
  }
}

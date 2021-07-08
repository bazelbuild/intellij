package com.google.idea.blaze.scala.libraries;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.java.libraries.BlazeJarLibraryLocator;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.scala.sync.model.BlazeScalaSyncData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;

class BlazeScalaJarLibraryLocator implements BlazeJarLibraryLocator {

  @Override
  public BlazeJarLibrary findLibraryFromIntellijLibrary(
          Project project, BlazeProjectData blazeProjectData, Library library) {

    String libName = library.getName();
    if (libName == null) {
      return null;
    }
    LibraryKey libraryKey = LibraryKey.fromIntelliJLibraryName(libName);
    BlazeScalaSyncData syncData = blazeProjectData.getSyncState().get(BlazeScalaSyncData.class);
    if (syncData == null) {
      Messages.showErrorDialog(project, "Project isn't synced. Please resync project.", "Error");
      return null;
    }
    return syncData.getImportResult().libraries.get(libraryKey);
  }
}

package com.google.idea.sdkcompat.general;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.fileChooser.ex.FileLookup;
import com.intellij.openapi.fileChooser.ex.LocalFsFinder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase;
import com.intellij.util.Restarter;
import com.intellij.util.indexing.diagnostic.dto.JsonDuration;
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics;
import com.intellij.util.indexing.roots.kind.LibraryOrigin;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import javax.annotation.Nullable;

/** Provides SDK compatibility shims for base plugin API classes, available to all IDEs. */
public final class BaseSdkCompat {
  private BaseSdkCompat() {}

  /** #api213: Inline into WorkspaceFileTextField . */
  public static LocalFsFinder.VfsFile getVfsFile(VirtualFile file) {
    return new LocalFsFinder.VfsFile(file);
  }

  /** #api213: Inline into WorkspaceFileTextField . */
  public static FileLookup.LookupFile getIoFile(Path path) {
    return new LocalFsFinder.IoFile(path);
  }

  /** #api213: Inline into BlazeProjectCreator. */
  public static OpenProjectTask createOpenProjectTask(Project project) {
    return OpenProjectTask.build().withProject(project);
  }

  /** #api213 interface is different in 221, inline when 213 support is dropped*/
  public static Project openProject(VirtualFile projectSubdirectory, Project projectToClose, boolean forceOpenInNewFrame) {
    OpenProjectTask options = OpenProjectTask.build().withForceOpenInNewFrame(forceOpenInNewFrame).withProjectToClose(projectToClose);
    return ProjectUtil.openProject(Paths.get(projectSubdirectory.getPath()),options);
  }
}

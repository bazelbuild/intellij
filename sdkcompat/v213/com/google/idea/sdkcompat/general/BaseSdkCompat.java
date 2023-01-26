package com.google.idea.sdkcompat.general;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.fileChooser.ex.FileLookup;
import com.intellij.openapi.fileChooser.ex.LocalFsFinder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.EditorNotificationsImpl;
import com.intellij.util.Restarter;
import com.intellij.util.indexing.diagnostic.dto.JsonDuration;
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics;
import java.io.File;
import java.nio.file.Path;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Provides SDK compatibility shims for base plugin API classes, available to all IDEs. */
public final class BaseSdkCompat {
  private BaseSdkCompat() {}

  /** #api213: inline this method */
  @Nullable
  public static String getIdeRestarterPath() {
    File startFile = Restarter.getIdeStarter();
    return startFile == null ? null : startFile.getPath();
  }

  /** #api213: inline into IndexingLogger */
  public static JsonDuration getTotalIndexingTime(
      JsonFileProviderIndexStatistics providerStatisticInput) {
    return providerStatisticInput.getTotalIndexingTime();
  }

  /** #api213: Inline into KytheRenameProcessor. */
  public static RenamePsiElementProcessor[] renamePsiElementProcessorsList() {
    return RenamePsiElementProcessor.EP_NAME.getExtensions();
  }

  /** #api213: Inline into WorkspaceFileTextField . */
  public static LocalFsFinder.VfsFile getVfsFile(VirtualFile file) {
    return new LocalFsFinder.VfsFile(/* unused LocalFsFinder */ null, file);
  }

  /** #api213: Inline into WorkspaceFileTextField. */
  public static FileLookup.LookupFile getIoFile(Path path) {
    return new LocalFsFinder.IoFile(path.toFile());
  }

  /** #api213: Inline into BlazeProjectCreator. */
  public static OpenProjectTask createOpenProjectTask(Project project) {
    return OpenProjectTask.withCreatedProject(project);
  }

  /* #api213: Inline into usages. */
  public static void registerEditorNotificationProvider(
      Project project, EditorNotifications.Provider<?> provider) {
    EditorNotificationsImpl.EP_PROJECT.getPoint(project).registerExtension(provider);
  }

  /* #api213: Inline into usages. */
  public static void unregisterEditorNotificationProvider(
      Project project, Class<? extends EditorNotifications.Provider<?>> providerClass) {
    EditorNotificationsImpl.EP_PROJECT.getPoint(project).unregisterExtension(providerClass);
  }

  /* #api213: Inline into usages. */
  public static void unregisterEditorNotificationProviders(
      Project project, Predicate<EditorNotifications.Provider<?>> filter) {
    unregisterExtensions(EditorNotificationsImpl.EP_PROJECT.getPoint(project), filter);
  }

  private static <T> void unregisterExtensions(
      ExtensionPoint<T> extensionPoint, Predicate<T> filter) {
    for (T extension : extensionPoint.getExtensions()) {
      if (filter.test(extension)) {
        extensionPoint.unregisterExtension(extension);
      }
    }
  }
}

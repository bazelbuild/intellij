package com.google.idea.sdkcompat.general;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextComponentAccessors;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.ui.TextFieldWithStoredHistory;
import com.intellij.util.Restarter;
import com.intellij.util.indexing.diagnostic.dto.JsonDuration;
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics;
import com.intellij.util.indexing.roots.kind.LibraryOrigin;
import com.intellij.vcs.log.VcsLogProperties;
import com.intellij.vcs.log.VcsLogProperties.VcsLogProperty;
import java.io.File;
import javax.annotation.Nullable;

/** Provides SDK compatibility shims for base plugin API classes, available to all IDEs. */
public final class BaseSdkCompat {
  private BaseSdkCompat() {}

  /** #api212: inline into FileSelectorWithStoredHistory */
  public static final TextComponentAccessor<TextFieldWithStoredHistory>
      TEXT_FIELD_WITH_STORED_HISTORY_WHOLE_TEXT =
          TextComponentAccessors.TEXT_FIELD_WITH_STORED_HISTORY_WHOLE_TEXT;

  /**
   * Creates an {@link IdeModifiableModelsProvider} for performant updates of the project model even
   * when many modifications are involved. {@link IdeModifiableModelsProvider#commit()} must be
   * called for any changes to take effect but call that method only after completing all changes.
   *
   * <p>#api212: New method createModifiableModelsProvider() is only available from 2021.3 on.
   */
  public static IdeModifiableModelsProvider createModifiableModelsProvider(Project project) {
    // Switch to ProjectDataManager#createModifiableModelsProvider in 2021.3 for a public, stable
    // API to create an IdeModifiableModelsProvider.
    return new IdeModifiableModelsProviderImpl(project);
  }

  /** #api212: inline into BlazeNewProjectWizard */
  public static void setContextWizard(WizardContext context, AbstractWizard<?> wizard) {
    context.putUserData(AbstractWizard.KEY, wizard);
  }

  /** #api212: inline this method. */
  @SuppressWarnings("rawtypes")
  public static boolean isIncrementalRefreshProperty(VcsLogProperty property) {
    return property == VcsLogProperties.SUPPORTS_INCREMENTAL_REFRESH;
  }

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

  /** #api213: inline this method. */
  public static String getLibraryNameFromLibraryOrigin(LibraryOrigin libraryOrigin) {
    return libraryOrigin.getLibrary().getName();
  }

  /** #api213: Inline into KytheRenameProcessor. */
  public static RenamePsiElementProcessor[] renamePsiElementProcessorsList() {
    return RenamePsiElementProcessor.EP_NAME.getExtensions();
  }
}

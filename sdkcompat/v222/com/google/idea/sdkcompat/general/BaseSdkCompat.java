package com.google.idea.sdkcompat.general;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextComponentAccessors;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase;
import com.intellij.ui.CoreIconManager;
import com.intellij.ui.IconManager;
import com.intellij.ui.TextFieldWithStoredHistory;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usages.TextChunk;
import com.intellij.util.Restarter;
import com.intellij.util.indexing.diagnostic.dto.JsonDuration;
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics;
import com.intellij.util.indexing.roots.kind.LibraryOrigin;
import com.intellij.util.ui.VcsExecutablePathSelector;
import com.intellij.vcs.log.VcsLogProperties;
import com.intellij.vcs.log.VcsLogProperties.VcsLogProperty;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Provides SDK compatibility shims for base plugin API classes, available to all IDEs. */
public final class BaseSdkCompat {
  private BaseSdkCompat() {}

  /** #api212: inline into FileSelectorWithStoredHistory */
  public static final TextComponentAccessor<TextFieldWithStoredHistory>
      TEXT_FIELD_WITH_STORED_HISTORY_WHOLE_TEXT =
          TextComponentAccessors.TEXT_FIELD_WITH_STORED_HISTORY_WHOLE_TEXT;

  /** #api203: refactor this function back into CodesearchResultData and make it private. */
  public static void addLineNumber(int lineNumber, List<TextChunk> chunks) {
    chunks.add(
        new TextChunk(
            UsageTreeColors.NUMBER_OF_USAGES_ATTRIBUTES.toTextAttributes(),
            String.valueOf(lineNumber)));
  }

  /** #api203: refactor back into MoveChangesToChangeListAction#getUnversionedFileStreamFromEvent */
  @Nullable
  public static Iterable<FilePath> getFilePaths(AnActionEvent e) {
    return e.getData(ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY);
  }

  /**
   * See {@link PathManager#getIndexRoot()}.
   *
   * <p>#api203: Method return type changed in 2021.1 from File to Path.
   */
  public static Path getIndexRoot() {
    return PathManager.getIndexRoot();
  }

  /** #api211 Activating IconManager requires an IconManager parameter in 2021.2 */
  public static void activateIconManager() throws Throwable {
    IconManager.activate(new CoreIconManager());
  }

  /**
   * See {@link ModifiableRootModel#addLibraryEntries(List, DependencyScope, boolean)}.
   *
   * <p>#api211: New method addLibraryEntries() is only available from 2021.2.1 on (or from 2021.1.4
   * if that bugfix release will ever be published).
   */
  public static void addLibraryEntriesToModel(
      ModifiableRootModel modifiableRootModel, List<Library> libraries) {
    // Use the batch addition of libraries as adding them one after the other is not performant.
    // The other parameters (scope + exported flag) are derived from their default values in
    // ModifiableRootModel#addLibraryEntry.
    modifiableRootModel.addLibraryEntries(
        libraries, DependencyScope.COMPILE, /* exported= */ false);
  }

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

  /** #api211: inline into HgConfigurationProjectPanel. Method params changed in 2021.2.4 */
  public static void reset(
      VcsExecutablePathSelector executablePathSelector,
      @Nullable String globalPath,
      boolean pathOverriddenForProject,
      @Nullable String projectPath,
      String autoDetectedPath) {
    executablePathSelector.reset(globalPath, pathOverriddenForProject, projectPath);
    executablePathSelector.setAutoDetectedPath(autoDetectedPath);
  }

  /** #api212: inline this method. */
  @SuppressWarnings("rawtypes")
  public static boolean isIncrementalRefreshProperty(VcsLogProperty property) {
    return property == VcsLogProperties.SUPPORTS_INCREMENTAL_REFRESH;
  }

  /** #api213: inline this method */
  @Nullable
  public static String getIdeRestarterPath() {
    Path startFilePath = Restarter.getIdeStarter();
    return startFilePath == null ? null : startFilePath.toString();
  }

  /** #api213: inline into IndexingLogger */
  public static JsonDuration getTotalIndexingTime(
      JsonFileProviderIndexStatistics providerStatisticInput) {
    return providerStatisticInput.getTotalIndexingVisibleTime();
  }

  /** #api213: inline this method. */
  public static String getLibraryNameFromLibraryOrigin(LibraryOrigin libraryOrigin) {
    // TODO(b/230430213): adapt getLibraryNameFromLibraryOrigin to work in 221
    return "";
  }

  /** #api213: Inline into KytheRenameProcessor. */
  public static RenamePsiElementProcessor[] renamePsiElementProcessorsList() {
    ArrayList<RenamePsiElementProcessor> result = new ArrayList<>();
    for (RenamePsiElementProcessorBase processor :
        RenamePsiElementProcessor.EP_NAME.getExtensions()) {
      if (processor instanceof RenamePsiElementProcessor) {
        result.add((RenamePsiElementProcessor) processor);
      }
    }
    return result.toArray(new RenamePsiElementProcessor[0]);
  }
}

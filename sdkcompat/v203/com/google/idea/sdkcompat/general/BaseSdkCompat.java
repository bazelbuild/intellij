package com.google.idea.sdkcompat.general;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.EditorFacade;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.psi.PsiElement;
import com.intellij.ui.IconManager;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usages.TextChunk;
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory.IndexingTimes;
import com.jetbrains.python.psi.LanguageLevel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Provides SDK compatibility shims for base plugin API classes, available to all IDEs. */
public final class BaseSdkCompat {
  private BaseSdkCompat() {}

  // #api202 TODO(b/181105847) Replace EditorFacade.getInstance() in the future
  @SuppressWarnings({"deprecation", "UnstableApiUsage"})
  public static void doWrapLongLinesIfNecessary(
      Editor editor,
      Project project,
      Document document,
      int startOffset,
      int endOffset,
      List<TextRange> enabledRanges,
      int rightMargin,
      PsiElement element) {
    EditorFacade.getInstance()
        .doWrapLongLinesIfNecessary(
            editor, project, document, startOffset, endOffset, enabledRanges, rightMargin);
  }

  /**
   * #api202: getVersion was deprecated in 2020.3 and it was removed and replaced by getMajorVersion
   * and getMinorVersion in 2021.1. We create a different language level comparator for different
   * IDE versions.
   */
  public static Comparator<LanguageLevel> getLanguageLevelComparator() {
    return Comparator.comparingInt(LanguageLevel::getMajorVersion)
        .thenComparingInt(LanguageLevel::getMinorVersion);
  }

  /** #api203: refactor this function back into CodesearchResultData and make it private. */
  public static void addLineNumber(int lineNumber, List<TextChunk> chunks) {
    EditorColorsScheme colorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
    chunks.add(
        new TextChunk(
            colorsScheme.getAttributes(UsageTreeColors.USAGE_LOCATION),
            String.valueOf(lineNumber)));
  }

  /** #api203: refactor back into MoveChangesToChangeListAction#getUnversionedFileStreamFromEvent */
  @SuppressWarnings("StreamToIterable") // the iterable will be immediately converted to a stream
  @Nullable
  public static Iterable<FilePath> getFilePaths(AnActionEvent e) {
    Stream<FilePath> filePathStream = e.getData(ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY);
    return (filePathStream == null) ? null : filePathStream::iterator;
  }

  /**
   * See {@link PathManager#getIndexRoot()}.
   *
   * <p>#api203: Method return type changed in 2021.1 from File to Path.
   */
  public static Path getIndexRoot() {
    return PathManager.getIndexRoot().toPath();
  }

  /** #api211 Activating IconManager requires an IconManager parameter in 2021.2 */
  public static void activateIconManager() {
    IconManager.activate();
  }

  /** #api203: inline this method into IndexingLogger */
  public static Duration getTotalUpdatingTime(IndexingTimes times) {
    if (times.getTotalEnd() == null || times.getTotalStart() == null) {
      return Duration.ZERO;
    }
    return Duration.between(times.getTotalStart(), times.getTotalEnd());
  }
  /** #api203: inline this method into IndexingLogger */
  public static Duration getScanFilesDuration(IndexingTimes times) {
    if (times.getScanFilesEnd() == null || times.getScanFilesStart() == null) {
      return Duration.ZERO;
    }
    return Duration.between(times.getScanFilesStart(), times.getScanFilesEnd());
  }
  /** #api203: inline this method into IndexingLogger */
  public static Duration getTotalIndexingTime(IndexingTimes times) {
    if (times.getIndexingDuration() == null) {
      return Duration.ZERO;
    }
    return times.getIndexingDuration();
  }

  /**
   * See {@link ModifiableRootModel#addLibraryEntry(Library)}.
   *
   * <p>#api211: New method addLibraryEntries() is only available from 2021.2.1 on (or from 2021.1.4
   * if that bugfix release will ever be published).
   */
  public static void addLibraryEntriesToModel(
      ModifiableRootModel modifiableRootModel, List<Library> libraries) {
    for (Library library : libraries) {
      modifiableRootModel.addLibraryEntry(library);
    }
  }

  /**
   * Creates an {@link IdeModifiableModelsProvider} for performant updates of the project model even
   * when many modifications are involved. {@link IdeModifiableModelsProvider#commit()} must be
   * called for any changes to take effect but call that method only after completing all changes.
   *
   * <p>#api212: New method createModifiableModelsProvider() is only available from 2021.3 on.
   */
  public static IdeModifiableModelsProvider createModifiableModelsProvider(Project project) {
    return new IdeModifiableModelsProviderImpl(project);
  }
}

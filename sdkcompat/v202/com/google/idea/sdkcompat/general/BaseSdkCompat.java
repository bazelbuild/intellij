package com.google.idea.sdkcompat.general;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.conversion.ConversionContext;
import com.intellij.diagnostic.VMOptions;
import com.intellij.diff.DiffContentFactoryImpl;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.ui.content.ContentManager;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usages.TextChunk;
import com.intellij.util.ContentUtilEx;
import com.jetbrains.python.psi.LanguageLevel;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.swing.JComponent;

/** Provides SDK compatibility shims for base plugin API classes, available to all IDEs. */
public final class BaseSdkCompat {
  private BaseSdkCompat() {}

  /** #api201: wildcard generics added in 2020.2. */
  public interface LineMarkerProviderAdapter extends LineMarkerProvider {
    @Override
    default void collectSlowLineMarkers(
        List<? extends PsiElement> elements, Collection<? super LineMarkerInfo<?>> result) {
      doCollectSlowLineMarkers(elements, result);
    }

    void doCollectSlowLineMarkers(
        List<? extends PsiElement> elements, Collection<? super LineMarkerInfo<?>> result);
  }

  /** #api201: project opening API changed in 2020.2. */
  public static void openProject(Project project, Path projectFile) {
    ProjectManagerEx.getInstanceEx()
        .openProject(projectFile, OpenProjectTask.withCreatedProject(project));
  }

  /** #api201: changed in 2020.2 */
  public static boolean isDisabledPlugin(PluginId id) {
    return PluginManagerCore.isDisabled(id);
  }

  /** #api201: changed in 2020.2 */
  public static Charset guessCharsetFromVcsRevisionData(
      Project project, byte[] revisionContent, FilePath filePath) {
    return DiffContentFactoryImpl.guessCharset(project, revisionContent, filePath);
  }

  /** #api201: ContentUtilEx changed in 2020.2 */
  public static void addTabbedContent(
      ContentManager manager,
      JComponent contentComponent,
      String groupPrefix,
      String tabName,
      boolean select,
      @Nullable Disposable childDisposable) {
    ContentUtilEx.addTabbedContent(
        manager, contentComponent, groupPrefix, tabName, select, childDisposable);
  }

  // #api202: Method return type changed in 2020.3 from File to Path
  @Nullable
  public static Path getVMOptionsWriteFile() {
    File file = VMOptions.getWriteFile();
    return file == null ? null : file.toPath();
  }
  /**
   * #api202: {@link ConversionContext#getSettingsBaseDir()} returns Path instead of File since
   * 2020.3.
   */
  @Nullable
  public static Path getSettingsBaseDirWrapper(ConversionContext context) {
    return context.getSettingsBaseDir() == null ? null : context.getSettingsBaseDir().toPath();
  }

  // #api202 doWrapLongLinesIfNecessary not available anymore in CodeFormatterFacade
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
    CodeFormatterFacade codeFormatter =
        new CodeFormatterFacade(
            CodeStyleSettingsManager.getSettings(project), element.getLanguage());

    codeFormatter.doWrapLongLinesIfNecessary(
        editor, element.getProject(), document, startOffset, endOffset, enabledRanges);
  }

  /** #api202: use "SearchEverywhereManager.setSelectedTabID directly" */
  public static void setSelectedTabID(SearchEverywhereManager manager, String id) {
    manager.setSelectedContributor(id);
  }

  /**
   * #api202: getVersion was deprecated in 2020.3 and it was removed and replaced by getMajorVersion
   * and getMinorVersion in 2021.1. We create a different language level comparator for different
   * IDE versions.
   */
  public static Comparator<LanguageLevel> getLanguageLevelComparator() {
    return Comparator.comparingInt(LanguageLevel::getVersion);
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
}

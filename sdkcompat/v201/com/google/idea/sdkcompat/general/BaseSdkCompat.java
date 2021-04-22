package com.google.idea.sdkcompat.general;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.conversion.ConversionContext;
import com.intellij.diagnostic.VMOptions;
import com.intellij.diff.DiffContentFactoryImpl;
import com.intellij.dvcs.branch.BranchType;
import com.intellij.dvcs.branch.DvcsBranchManager;
import com.intellij.dvcs.branch.DvcsBranchSettings;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ContentUtilEx;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.JComponent;

/** Provides SDK compatibility shims for base plugin API classes, available to all IDEs. */
public final class BaseSdkCompat {
  private BaseSdkCompat() {}

  /** #api193: constructor changed in 2020.1. */
  public static class DvcsBranchManagerAdapter extends DvcsBranchManager {
    protected DvcsBranchManagerAdapter(
        Project project, DvcsBranchSettings settings, BranchType[] branchTypes) {
      super(project, settings, branchTypes);
    }
  }

  /** #api201: wildcard generics added in 2020.2. */
  public interface LineMarkerProviderAdapter extends LineMarkerProvider {
    @Override
    default void collectSlowLineMarkers(
        List<PsiElement> elements, Collection<LineMarkerInfo> result) {
      doCollectSlowLineMarkers(elements, result);
    }

    void doCollectSlowLineMarkers(
        List<? extends PsiElement> elements, Collection<? super LineMarkerInfo<?>> result);
  }

  /** #api201: project opening API changed in 2020.2. */
  public static void openProject(Project project, Path projectFile) {
    PlatformProjectOpenProcessor.openExistingProject(
        /* file= */ projectFile, /* projectDir= */ projectFile, new OpenProjectTask(project));
  }

  /** #api201: changed in 2020.2 */
  public static boolean isDisabledPlugin(PluginId id) {
    return PluginManagerCore.getDisabledPlugins().contains(id.getIdString());
  }

  /** #api201: changed in 2020.2 */
  public static Charset guessCharsetFromVcsRevisionData(
      Project project, byte[] revisionContent, FilePath filePath) {
    return DiffContentFactoryImpl.guessCharset(revisionContent, filePath);
  }

  /** #api201: ContentUtilEx changed in 2020.2 */
  public static void addTabbedContent(
      ContentManager manager,
      JComponent contentComponent,
      String groupPrefix,
      String tabName,
      boolean select,
      @Nullable Disposable childDisposable) {
    ContentUtilEx.addTabbedContent(manager, contentComponent, groupPrefix, tabName, select);
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
}

package com.google.idea.sdkcompat.general;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.diagnostic.VMOptions;
import com.intellij.diff.DiffContentFactoryImpl;
import com.intellij.dvcs.branch.BranchType;
import com.intellij.dvcs.branch.DvcsBranchManager;
import com.intellij.dvcs.branch.DvcsBranchSettings;
import com.intellij.find.findUsages.CustomUsageSearcher;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.scratch.ScratchesNamedScope;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.content.ContentManager;
import com.intellij.usages.Usage;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.Processor;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.JComponent;

/** Provides SDK compatibility shims for base plugin API classes, available to all IDEs. */
public final class BaseSdkCompat {
  private BaseSdkCompat() {}

  /** #api193: made public in 2020.1. */
  @Nullable
  public static ProjectExtensionPointName<EditorNotifications.Provider<?>>
      getEditorNotificationsEp() {
    return null;
  }

  /** #api193: constructor changed in 2020.1. */
  public static class DvcsBranchManagerAdapter extends DvcsBranchManager {
    protected DvcsBranchManagerAdapter(
        Project project, DvcsBranchSettings settings, BranchType[] branchTypes) {
      super(settings, branchTypes);
    }
  }

  /** #api193: wildcard generics added in 2020.1. */
  public abstract static class CustomUsageSearcherAdapter extends CustomUsageSearcher {
    /** #api193: wildcard generics added in 2020.1. */
    @Override
    public void processElementUsages(
        PsiElement element, Processor<Usage> processor, FindUsagesOptions options) {
      doProcessElementUsages(element, processor, options);
    }

    protected abstract void doProcessElementUsages(
        PsiElement element, Processor<? super Usage> processor, FindUsagesOptions options);
  }

  /** #api193: wildcard generics added in 2020.1. */
  @SuppressWarnings({"rawtypes", "unchecked"}) // #api193: wildcard generics added in 2020.1
  public interface TreeStructureProviderAdapter extends TreeStructureProvider {
    @Nullable
    default Object doGetData(Collection<AbstractTreeNode<?>> selected, String dataId) {
      return null;
    }

    @Nullable
    @Override
    default Object getData(Collection<AbstractTreeNode> selected, String dataId) {
      return doGetData((Collection) selected, dataId);
    }

    @Override
    default Collection<AbstractTreeNode> modify(
        AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
      return doModify(parent, (Collection) children, settings);
    }

    Collection<AbstractTreeNode<?>> doModify(
        AbstractTreeNode<?> parent,
        Collection<AbstractTreeNode<?>> children,
        ViewSettings settings);
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

  /** #api193: changed in 2020.1. */
  public static final String SCRATCHES_SCOPE_NAME = ScratchesNamedScope.NAME;

  /** #api193: 'project' param removed in 2020.1. */
  public static void setTemplateTesting(Project project, Disposable parentDisposable) {
    TemplateManagerImpl.setTemplateTesting(project, parentDisposable);
  }

  /** #api193: changed in 2020.1. */
  @Nullable
  public static String getActiveToolWindowId(Project project) {
    return ToolWindowManager.getActiveId();
  }

  /** Compat class for {@link AllIcons}. */
  public static final class AllIconsCompat {
    private AllIconsCompat() {}

    /** #api193: changed in 2020.1. */
    public static final Icon collapseAll = AllIcons.General.CollapseAll;

    /** #api193: this is unavailable (and not used) in 2020.1 */
    public static final Icon disabledRun = AllIcons.Process.DisabledRun;

    /** #api193: this is unavailable (and not used) in 2020.1 */
    public static final Icon disabledDebug = AllIcons.Process.DisabledDebug;
  }

  /** #api193: SdkConfigurationUtil changed in 2020.1. */
  public static ProjectJdkImpl createSdk(
      Collection<? extends Sdk> allSdks,
      VirtualFile homeDir,
      SdkType sdkType,
      @Nullable SdkAdditionalData additionalData,
      @Nullable String customSdkSuggestedName) {
    return SdkConfigurationUtil.createSdk(
        allSdks.toArray(new Sdk[0]), homeDir, sdkType, additionalData, customSdkSuggestedName);
  }

  /** #api201: project opening API changed in 2020.2. */
  public static void openProject(Project project, Path projectFile) {
    ProjectManagerEx.getInstanceEx().openProject(project);
  }

  /** #api193: auto-disposed with UI component in 2020.1+ */
  public static void disposeEditorTextField(EditorTextField field) {
    Editor editor = field.getEditor();
    if (editor != null) {
      EditorFactory.getInstance().releaseEditor(editor);
    }
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
}

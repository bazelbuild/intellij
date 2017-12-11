package com.google.idea.blaze.base.actions;

import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.common.actionhelper.ActionPresentationHelper;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.io.File;

import static com.google.idea.blaze.base.buildmap.BuildFileUtil.findBuildTarget;
import static com.google.idea.blaze.base.buildmap.BuildFileUtil.getBuildFile;

/** Copies a blaze target path into the clipboard from any element in the file */
public class CopyBuildTargetFromElementAction extends BlazeProjectAction {

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    Label label = getSelectedTarget(project, e);
    if (label == null) {
      return;
    }
    CopyPasteManager.getInstance().setContents(new StringSelection(label.toString()));
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    Label label = getSelectedTarget(project, e);
    if (label == null) {
      ActionPresentationHelper.of(e).hideIf(true).commit();
    } else {
      ActionPresentationHelper.of(e)
        .setText(String.format("Copy '%s'", shortenNameIfNeed(label.toString()))).commit();
    }
  }

  @Nullable
  private static Label getSelectedTarget(Project project, AnActionEvent e) {
    VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (vf == null) {
      return null;
    }
    BlazePackage parentPackage = getBuildFile(project, vf);
    if (parentPackage == null) {
      return null;
    }
    PsiElement target = findBuildTarget(project, parentPackage, new File(vf.getPath()));
    if (target == null) {
      return null;
    }
    if (!(target instanceof FuncallExpression)) {
      return null;
    }
    Label label = ((FuncallExpression) target).resolveBuildLabel();
    if (label == null) {
      return null;
    }
    return label;
  }

  private static String shortenNameIfNeed(@NotNull String name) {
    return StringUtil.first(name, 40, true);
  }
}
package com.google.idea.blaze.base.ui.problems;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.NotNull;

public class ShowOnlyErrorsAction extends ToggleAction {
  private boolean toggled = false;

  public ShowOnlyErrorsAction() {
    super("Show only errors");
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent anActionEvent) {
    return toggled;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent anActionEvent, boolean b) {
    toggled = b;
    BlazeProblemsView.getInstance(anActionEvent.getProject()).reload();
  }

  public boolean isToggled() {
    return toggled;
  }
}
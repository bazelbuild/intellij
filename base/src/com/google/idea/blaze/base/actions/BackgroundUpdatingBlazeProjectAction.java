package com.google.idea.blaze.base.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import org.jetbrains.annotations.NotNull;

/**
 * {@BackgroundUpdatingBlazeProjectAction} is a mixin for {@BlazeProjectAction}s that need
 * to access project data in their update() functions.
 * Actions can choose whether they run their update() methods on the
 * BackGround Thread or the Event Dispatcher Thread.
 * Implement this if the action requires access to the PSI, VFS, or project data,
 * but _not_ access to UI elements.
 * <p>
 * Ref: <a href="https://plugins.jetbrains.com/docs/intellij/basic-action-system.html#principal-implementation-overrides">...</a>
 */
public abstract class BackgroundUpdatingBlazeProjectAction extends BlazeProjectAction {
  @Override
  @NotNull
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}

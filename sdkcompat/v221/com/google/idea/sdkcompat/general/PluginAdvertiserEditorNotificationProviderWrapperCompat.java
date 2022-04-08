package com.google.idea.sdkcompat.general;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserEditorNotificationProvider;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserEditorNotificationProvider.AdvertiserSuggestion;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;

/**
 * #api212: remove this class and make EditorNotifications.PanelProvider a direct parent of
 * PluginAdvertiserEditorNotificationProviderWrapper. Inline the functionality of this class in
 * PluginAdvertiserEditorNotificationProviderWrapper
 */
public abstract class PluginAdvertiserEditorNotificationProviderWrapperCompat
    extends EditorNotifications.Provider<EditorNotificationPanel> {

  /** #api212: change to private when inline to PluginAdvertiserEditorNotificationProviderWrapper */
  protected final PluginAdvertiserEditorNotificationProvider
      pluginAdvertiserEditorNotificationProvider;

  public PluginAdvertiserEditorNotificationProviderWrapperCompat(
      PluginAdvertiserEditorNotificationProvider pluginAdvertiserEditorNotificationProvider) {

    this.pluginAdvertiserEditorNotificationProvider = pluginAdvertiserEditorNotificationProvider;
  }

  /**
   * #api221 AdvertiserSuggestion collectNotificationData(VirtualFile file, Project project) ->
   * AdvertiserSuggestion collectNotificationData(Project project, VirtualFile file)
   */
  public AdvertiserSuggestion collectNotificationData(VirtualFile file, Project project) {
    return collectNotificationData(project, file);
  }

  @NotNull
  @Override
  public AdvertiserSuggestion collectNotificationData(@NotNull Project project, @NotNull VirtualFile file) {
    return (AdvertiserSuggestion) pluginAdvertiserEditorNotificationProvider.collectNotificationData(project, file);
  }
}

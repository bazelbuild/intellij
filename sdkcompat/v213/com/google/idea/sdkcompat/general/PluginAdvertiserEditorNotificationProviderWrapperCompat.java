package com.google.idea.sdkcompat.general;

import com.intellij.openapi.fileTypes.PlainTextLikeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserEditorNotificationProvider;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserEditorNotificationProvider.AdvertiserSuggestion;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;

/**
 * #api212: remove this class and make EditorNotifications.PanelProvider a direct parent of
 * PluginAdvertiserEditorNotificationProviderWrapper. Inline the functionality of this class in
 * PluginAdvertiserEditorNotificationProviderWrapper
 */
public abstract class PluginAdvertiserEditorNotificationProviderWrapperCompat
    extends EditorNotifications.PanelProvider {

  // #api212: change to private when inline to PluginAdvertiserEditorNotificationProviderWrapper
  protected final PluginAdvertiserEditorNotificationProvider
      pluginAdvertiserEditorNotificationProvider;

  public PluginAdvertiserEditorNotificationProviderWrapperCompat(
      PluginAdvertiserEditorNotificationProvider pluginAdvertiserEditorNotificationProvider) {

    this.pluginAdvertiserEditorNotificationProvider = pluginAdvertiserEditorNotificationProvider;
  }

  @Override
  public AdvertiserSuggestion collectNotificationData(VirtualFile file, Project project) {
    boolean alreadySupported = !(file.getFileType() instanceof PlainTextLikeFileType);
    if (alreadySupported) {
      return null;
    }
    return pluginAdvertiserEditorNotificationProvider.collectNotificationData(file, project);
  }
}

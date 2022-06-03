package com.google.idea.sdkcompat.general;

import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.PlainTextLikeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserEditorNotificationProvider;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.EditorNotificationsImpl;
import javax.annotation.Nullable;

/**
 * #api212: remove this class and make EditorNotifications.PanelProvider a direct parent of
 * PluginAdvertiserEditorNotificationProviderWrapper. Inline the functionality of this class in
 * PluginAdvertiserEditorNotificationProviderWrapper
 */
public abstract class PluginAdvertiserEditorNotificationProviderWrapperCompat
    extends EditorNotifications.Provider<EditorNotificationPanel> {

  // #api212: change to private when inline to PluginAdvertiserEditorNotificationProviderWrapper
  protected final PluginAdvertiserEditorNotificationProvider
      pluginAdvertiserEditorNotificationProvider;

  public PluginAdvertiserEditorNotificationProviderWrapperCompat(
      PluginAdvertiserEditorNotificationProvider pluginAdvertiserEditorNotificationProvider) {

    this.pluginAdvertiserEditorNotificationProvider = pluginAdvertiserEditorNotificationProvider;
  }

  @Override
  public Key<EditorNotificationPanel> getKey() {
    return pluginAdvertiserEditorNotificationProvider.getKey();
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(
      VirtualFile file, FileEditor fileEditor, Project project) {
    boolean alreadySupported = !(file.getFileType() instanceof PlainTextLikeFileType);
    if (alreadySupported) {
      return null;
    }
    return pluginAdvertiserEditorNotificationProvider.createNotificationPanel(
        file, fileEditor, project);
  }

  public static void reregisterExtension(
      Project project, PluginAdvertiserEditorNotificationProviderWrapperCompat replacement) {
    ExtensionPoint<EditorNotifications.Provider<?>> ep =
        EditorNotificationsImpl.EP_PROJECT.getPoint(project);
    ep.unregisterExtension(PluginAdvertiserEditorNotificationProvider.class);
    ep.registerExtension(replacement, project);
  }
}

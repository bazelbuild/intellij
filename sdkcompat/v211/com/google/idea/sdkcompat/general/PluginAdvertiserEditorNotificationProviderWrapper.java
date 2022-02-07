package com.google.idea.sdkcompat.general;

import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.PlainTextLikeFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserEditorNotificationProvider;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.EditorNotificationsImpl;
import javax.annotation.Nullable;

/**
 * Workaround for https://youtrack.jetbrains.com/issue/IDEA-246111 (b/193404906).<br>
 * There is a confirmed bug with files not having any ending, e.g., BUILD, if the plugin supporting
 * the file type is not in the marketplace, then notifications are shown suggesting other plugins.
 * <br>
 * Remove this class once the upstream issue is fixed.
 */
final class PluginAdvertiserEditorNotificationProviderWrapper
    extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {

  private final PluginAdvertiserEditorNotificationProvider
      pluginAdvertiserEditorNotificationProvider;

  public PluginAdvertiserEditorNotificationProviderWrapper(
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

  static class ReplaceBaseProvider implements StartupActivity.DumbAware {
    @Override
    public void runActivity(Project project) {
      ExtensionPoint<EditorNotifications.Provider<?>> ep =
          EditorNotificationsImpl.EP_PROJECT.getPoint(project);
      ep.unregisterExtension(PluginAdvertiserEditorNotificationProvider.class);
      ep.registerExtension(
          new PluginAdvertiserEditorNotificationProviderWrapper(
              new PluginAdvertiserEditorNotificationProvider()),
          project);
      EditorNotifications.getInstance(project).updateAllNotifications();
    }
  }
}

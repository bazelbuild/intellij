package com.google.idea.blaze.base.actions;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.PlatformUtils;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import org.jetbrains.annotations.Nullable;

public final class FileGitHubIssueAction extends BlazeProjectAction {

  private static final String BASE_URL = "https://github.com/bazelbuild/intellij/issues/new?";

  private static final ImmutableList<String> BAZEL_PLUGIN_IDS =
      ImmutableList.of(
          "com.google.idea.bazel.aswb", "com.google.idea.bazel.ijwb", "com.google.idea.bazel.clwb");

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {

    // TODO(jingwen): Find somewhere else to hide this from the Blaze plugin
    if (Blaze.getBuildSystem(project).equals(BuildSystem.Blaze)) {
      Messages.showErrorDialog(
          e.getProject(),
          "GitHub issue filing is only supported when using the plugin with Bazel.",
          "Bazel Plugin Required");
      return;
    }

    URL url = buildGitHubUrl(project);
    if (url == null) {
      // If for some reason we can't construct the URL, attempt to open the issues page directly.
      BrowserUtil.browse(BASE_URL);
    } else {
      BrowserUtil.browse(buildGitHubUrl(project));
    }

  }

  @Nullable
  private URL buildGitHubUrl(Project project) {
    StringBuilder issueParameterBuilder = new StringBuilder();
    StringBuilder bodyParam = new StringBuilder();

    bodyParam.append("#### Description of the issue. Please be specific.\n");
    bodyParam.append("\n");
    bodyParam.append(
        "#### What's the simplest set of steps to reproduce this issue? Please provide "
            + "an example project, if possible.\n");
    bodyParam.append("\n");
    bodyParam.append("#### Version information\n");

    // Get the IDE version
    bodyParam.append(
        String.format("%s: %s\n", getProductId(), ApplicationInfo.getInstance().getFullVersion()));

    // Get information about the operating system
    bodyParam.append(String.format("Platform: %s %s\n", SystemInfo.OS_NAME, SystemInfo.OS_VERSION));

    // Get the plugin version
    for (IdeaPluginDescriptor plugin : PluginManager.getPlugins()) {
      if (BAZEL_PLUGIN_IDS.contains(plugin.getPluginId().getIdString())) {
        bodyParam.append(
            String.format(
                "%s plugin: %s%s\n",
                plugin.getName(), plugin.getVersion(), plugin.isEnabled() ? "" : " (disabled)"));
      }
    }

    // Get the Bazel version
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData != null) {
      bodyParam.append(String.format("Bazel: %s\n", projectData.getBlazeVersionData().toString()));
    }

    try {
      issueParameterBuilder.append("body=" + URLEncoder.encode(bodyParam.toString(), "UTF-8"));
    } catch (UnsupportedEncodingException ex) {
      // If we can't manage to parse the body for some reason (e.g. weird SystemInfo
      // OS_NAME or OS_VERSION), just proceed and open up an empty GitHub issue form.
      return null;
    }

    try {
      return new URL(BASE_URL + issueParameterBuilder);
    } catch (MalformedURLException ex) {
      return null;
    }

  }

  private String getProductId() {
    String platformPrefix = PlatformUtils.getPlatformPrefix();

    // IDEA Community Edition is "Idea", whereas IDEA Ultimate Edition is "idea". Let's make them
    // more useful.
    if (PlatformUtils.isIdeaCommunity()) {
      platformPrefix = "IdeaCommunity";
    } else if (PlatformUtils.isIdeaUltimate()) {
      platformPrefix = "IdeaUltimate";
    }
    return platformPrefix;
  }
}

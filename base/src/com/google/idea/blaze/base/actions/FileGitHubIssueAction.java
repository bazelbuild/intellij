package com.google.idea.blaze.base.actions;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.PlatformUtils;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import org.jetbrains.annotations.Nullable;

/**
 * A an action to open a GitHub issue with pre-filled information like the versions of the
 * plugin, Bazel and the IDE.
 *
 * Read the
 * <a href="https://help.github.com/en/articles/about-automation-for-issues-and-pull-requests-with-query-parameters">GitHub docs</a>
 * for more information on issue automation.
 */
public final class FileGitHubIssueAction extends BlazeProjectAction {

  private static final Logger logger = Logger.getInstance(FileGitHubIssueAction.class);

  private static final String BASE_URL = "https://github.com/bazelbuild/intellij/issues/new";

  private static final ImmutableList<String> BAZEL_PLUGIN_IDS =
      ImmutableList.of(
          "com.google.idea.bazel.aswb", "com.google.idea.bazel.ijwb", "com.google.idea.bazel.clwb");


  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    int retVal =
        ExternalTask.builder(WorkspaceRoot.fromProject(project))
            .args("glogin", "-version")
            .build()
            .run();

    // Only show the menu bar reference if glogin doesn't exist.
    e.getPresentation().setEnabledAndVisible(retVal != 0);
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    URL url = getGitHubTemplateURL(project);
    if (url == null) {
      // If, for some reason, we can't construct the URL, open the issues page directly.
      BrowserUtil.browse(BASE_URL);
    } else {
      BrowserUtil.browse(url);
    }
  }

  /**
   * Constructs a GitHub URL with query parameters containing pre-filled information
   * about a user's IDE installation and system.
   *
   * @param project The Bazel project instance.
   * @return an URL containing pre-filled instructions and information about a user's system.
   */
  @Nullable
  private URL getGitHubTemplateURL(Project project) {
    // q?body=<param value>
    // https://help.github.com/en/articles/about-automation-for-issues-and-pull-requests-with-query-parameters#supported-query-parameters
    StringBuilder bodyParam = new StringBuilder();

    bodyParam.append("#### Description of the issue. Please be specific.\n");
    bodyParam.append("\n");

    bodyParam.append("#### What's the simplest set of steps to reproduce this issue? ");
    bodyParam.append("Please provide an example project, if possible.\n");
    bodyParam.append("\n");

    bodyParam.append("#### Version information\n");

    // Get the IDE version.
    // e.g. IdeaCommunity: 2019.1.2
    bodyParam.append(
        String.format("%s: %s\n", getProductId(), ApplicationInfo.getInstance().getFullVersion()));

    // Get information about the operating system.
    // e.g. Platform: Linux 4.19.37-amd64
    bodyParam.append(String.format("Platform: %s %s\n", SystemInfo.OS_NAME, SystemInfo.OS_VERSION));

    // Get the plugin version.
    // e.g. Bazel plugin: 2019.07.23.0.3
    for (IdeaPluginDescriptor plugin : PluginManager.getPlugins()) {
      if (BAZEL_PLUGIN_IDS.contains(plugin.getPluginId().getIdString())) {
        bodyParam.append(
            String.format(
                "%s plugin: %s%s\n",
                plugin.getName(), plugin.getVersion(), plugin.isEnabled() ? "" : " (disabled)"));
      }
    }

    // Get the Bazel version.
    // e.g. Bazel: 0.28.1
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData != null) {
      bodyParam.append(String.format("Bazel: %s\n", projectData.getBlazeVersionData().toString()));
    }

    try {
      return new URL(BASE_URL + "?body=" + URLEncoder.encode(bodyParam.toString(), "UTF-8"));
    } catch (UnsupportedEncodingException | MalformedURLException ex) {
      // If we can't manage to parse the body for some reason (e.g. weird SystemInfo
      // OS_NAME or OS_VERSION), just proceed and open up an empty GitHub issue form.
      logger.error(ex);
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

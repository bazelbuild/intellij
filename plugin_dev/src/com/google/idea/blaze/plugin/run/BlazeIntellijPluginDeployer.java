/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.plugin.run;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.devtools.intellij.plugin.IntellijPluginTargetDeployInfo.IntellijPluginDeployFile;
import com.google.devtools.intellij.plugin.IntellijPluginTargetDeployInfo.IntellijPluginDeployInfo;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.protobuf.repackaged.TextFormat;
import com.intellij.concurrency.AsyncUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Key;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

/** Handles finding files to deploy and copying these into the sandbox. */
class BlazeIntellijPluginDeployer {

  private static final BoolExperiment deployJavaAgents =
      new BoolExperiment("blaze.plugin.run.deploy.javaagents", true);

  static final Key<BlazeIntellijPluginDeployer> USER_DATA_KEY =
      Key.create(BlazeIntellijPluginDeployer.class.getName());

  private final String sandboxHome;
  private final String buildNumber;
  private final Label pluginTarget;
  private final List<File> deployInfoFiles = new ArrayList<>();
  private final Map<File, File> filesToDeploy = Maps.newHashMap();
  private File executionRoot;

  private Future<Void> fileCopyingTask;

  BlazeIntellijPluginDeployer(String sandboxHome, String buildNumber, Label pluginTarget) {
    this.sandboxHome = sandboxHome;
    this.buildNumber = buildNumber;
    this.pluginTarget = pluginTarget;
  }

  /**
   * Clear data from the last build -- if this build fails, we don't want to silently launch the
   * previously built plugin.
   */
  void buildStarted() {
    executionRoot = null;
    deployInfoFiles.clear();
  }

  void reportBuildComplete(File executionRoot, BuildResultHelper buildResultHelper)
      throws GetArtifactsException {
    this.executionRoot = executionRoot;
    deployInfoFiles.clear();
    for (File file : buildResultHelper.getBuildArtifactsForTarget(pluginTarget)) {
      if (file.getName().endsWith(".intellij-plugin-debug-target-deploy-info")) {
        deployInfoFiles.add(file);
      }
    }
  }

  /**
   * Returns information about which plugins will be deployed, and asynchronously copies the
   * corresponding files to the sandbox.
   */
  DeployedPluginInfo deployNonBlocking() throws ExecutionException {
    if (deployInfoFiles.isEmpty()) {
      throw new ExecutionException("No plugin files found. Did the build fail?");
    }
    List<IntellijPluginDeployInfo> deployInfoList = Lists.newArrayList();
    for (File deployInfoFile : deployInfoFiles) {
      deployInfoList.addAll(readDeployInfoFromFile(deployInfoFile));
    }
    ImmutableMap<File, File> filesToDeploy = getFilesToDeploy(executionRoot, deployInfoList);
    this.filesToDeploy.putAll(filesToDeploy);
    ImmutableSet<File> javaAgentJars =
        deployJavaAgents.getValue() ? listJavaAgentFiles(deployInfoList) : ImmutableSet.of();

    for (File file : filesToDeploy.keySet()) {
      if (!file.exists()) {
        throw new ExecutionException(
            String.format("Plugin file '%s' not found. Did the build fail?", file.getName()));
      }
    }
    // kick off file copying task asynchronously, so it doesn't block the EDT.
    fileCopyingTask =
        BlazeExecutor.getInstance()
            .submit(
                () -> {
                  for (Map.Entry<File, File> entry : filesToDeploy.entrySet()) {
                    copyFileToSandbox(entry.getKey(), entry.getValue());
                  }
                  return null;
                });

    return new DeployedPluginInfo(readPluginIds(filesToDeploy.keySet()), javaAgentJars);
  }

  /** Blocks until the plugin files have been copied to the sandbox */
  void blockUntilDeployComplete() {
    AsyncUtil.get(fileCopyingTask);
    fileCopyingTask = null;
  }

  void deleteDeployment() {
    for (File file : filesToDeploy.values()) {
      if (file.exists()) {
        file.delete();
      }
    }
  }

  private static ImmutableList<IntellijPluginDeployInfo> readDeployInfoFromFile(File deployInfoFile)
      throws ExecutionException {
    ImmutableList.Builder<IntellijPluginDeployInfo> result = ImmutableList.builder();
    try (InputStream inputStream = new BufferedInputStream(new FileInputStream(deployInfoFile))) {
      IntellijPluginDeployInfo.Builder builder = IntellijPluginDeployInfo.newBuilder();
      TextFormat.Parser parser = TextFormat.Parser.newBuilder().setAllowUnknownFields(true).build();
      parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
      IntellijPluginDeployInfo deployInfo = builder.build();
      result.add(deployInfo);
    } catch (IOException e) {
      throw new ExecutionException(e);
    }
    return result.build();
  }

  private ImmutableMap<File, File> getFilesToDeploy(
      File executionRoot, Collection<IntellijPluginDeployInfo> deployInfos) {
    ImmutableMap.Builder<File, File> result = ImmutableMap.builder();
    for (IntellijPluginDeployInfo deployInfo : deployInfos) {
      for (IntellijPluginDeployFile deployFile : deployInfo.getDeployFilesList()) {
        File src = new File(executionRoot, deployFile.getExecutionPath());
        File dest = new File(sandboxPluginDirectory(sandboxHome), deployFile.getDeployLocation());
        result.put(src, dest);
      }
      for (IntellijPluginDeployFile deployFile : deployInfo.getJavaAgentDeployFilesList()) {
        File src = new File(executionRoot, deployFile.getExecutionPath());
        File dest = new File(sandboxPluginDirectory(sandboxHome), deployFile.getDeployLocation());
        result.put(src, dest);
      }
    }
    return result.build();
  }

  private ImmutableSet<File> listJavaAgentFiles(Collection<IntellijPluginDeployInfo> deployInfos) {
    ImmutableSet.Builder<File> result = ImmutableSet.builder();
    for (IntellijPluginDeployInfo deployInfo : deployInfos) {
      for (IntellijPluginDeployFile deployFile : deployInfo.getJavaAgentDeployFilesList()) {
        result.add(new File(sandboxPluginDirectory(sandboxHome), deployFile.getDeployLocation()));
      }
    }
    return result.build();
  }

  private static File sandboxPluginDirectory(String sandboxHome) {
    return new File(sandboxHome, "plugins");
  }

  private ImmutableSet<String> readPluginIds(Collection<File> files) throws ExecutionException {
    ImmutableSet.Builder<String> pluginIds = ImmutableSet.builder();
    for (File file : files) {
      if (file.getName().endsWith(".jar")) {
        String pluginId = readPluginIdFromJar(buildNumber, file);
        if (pluginId != null) {
          pluginIds.add(pluginId);
        }
      }
    }
    return pluginIds.build();
  }

  @Nullable
  private static String readPluginIdFromJar(String buildNumber, File jar)
      throws ExecutionException {
    IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.loadDescriptor(jar, "plugin.xml");
    if (pluginDescriptor == null) {
      return null;
    }
    if (PluginManagerCore.isIncompatible(pluginDescriptor, BuildNumber.fromString(buildNumber))) {
      throw new ExecutionException(
          String.format(
              "Plugin SDK version '%s' is incompatible with this plugin "
                  + "(since: '%s', until: '%s')",
              buildNumber, pluginDescriptor.getSinceBuild(), pluginDescriptor.getUntilBuild()));
    }
    return pluginDescriptor.getPluginId().getIdString();
  }

  private static void copyFileToSandbox(File src, File dest) throws ExecutionException {
    try {
      dest.getParentFile().mkdirs();
      Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
      dest.deleteOnExit();
    } catch (IOException e) {
      throw new ExecutionException("Error copying plugin file to sandbox", e);
    }
  }

  static class DeployedPluginInfo {
    final ImmutableSet<String> pluginIds;
    final ImmutableSet<File> javaAgents;

    DeployedPluginInfo(ImmutableSet<String> pluginIds, ImmutableSet<File> javaAgents) {
      this.pluginIds = pluginIds;
      this.javaAgents = javaAgents;
    }
  }
}

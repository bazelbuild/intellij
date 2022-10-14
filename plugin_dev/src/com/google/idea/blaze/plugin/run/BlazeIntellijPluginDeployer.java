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
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.protobuf.TextFormat;
import com.intellij.concurrency.AsyncUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.util.Key;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/** Handles finding files to deploy and copying these into the sandbox. */
class BlazeIntellijPluginDeployer {

  private static final BoolExperiment deployJavaAgents =
      new BoolExperiment("blaze.plugin.run.deploy.javaagents", true);

  static final Key<BlazeIntellijPluginDeployer> USER_DATA_KEY =
      Key.create(BlazeIntellijPluginDeployer.class.getName());

  private final String sandboxHome;
  private final Label pluginTarget;
  private final List<File> deployInfoFiles = new ArrayList<>();
  private final Map<File, File> filesToDeploy = Maps.newHashMap();
  private File executionRoot;

  private Future<Void> fileCopyingTask;

  BlazeIntellijPluginDeployer(String sandboxHome, Label pluginTarget) {
    this.sandboxHome = sandboxHome;
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
    ImmutableList<OutputArtifact> outputs =
        buildResultHelper.getBuildArtifactsForTarget(
            pluginTarget, file -> file.endsWith(".intellij-plugin-debug-target-deploy-info"));
    deployInfoFiles.addAll(BlazeArtifact.getLocalFiles(outputs));
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

    return new DeployedPluginInfo(javaAgentJars);
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

  private static void copyFileToSandbox(File src, File dest) throws ExecutionException {
    try {
      dest.getParentFile().mkdirs();
      FileOperationProvider.getInstance().copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
      dest.deleteOnExit();
    } catch (IOException e) {
      throw new ExecutionException("Error copying plugin file to sandbox", e);
    }
  }

  static class DeployedPluginInfo {
    final ImmutableSet<File> javaAgents;

    DeployedPluginInfo(ImmutableSet<File> javaAgents) {
      this.javaAgents = javaAgents;
    }
  }
}

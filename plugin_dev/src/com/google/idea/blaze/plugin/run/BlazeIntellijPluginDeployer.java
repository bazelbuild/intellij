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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.plugin.IntellijPluginRule;
import com.google.repackaged.devtools.intellij.plugin.IntellijPluginTargetDeployInfo.IntellijPluginDeployFile;
import com.google.repackaged.devtools.intellij.plugin.IntellijPluginTargetDeployInfo.IntellijPluginDeployInfo;
import com.google.repackaged.protobuf.TextFormat;
import com.intellij.execution.ExecutionException;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.project.Project;
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
import javax.annotation.Nullable;

/** Handles finding files to deploy and copying these into the sandbox. */
class BlazeIntellijPluginDeployer {

  static final Key<BlazeIntellijPluginDeployer> USER_DATA_KEY =
      Key.create(BlazeIntellijPluginDeployer.class.getName());

  private final String sandboxHome;
  private final String buildNumber;
  private final TargetMap targetMap;
  private final List<Label> targetsToDeploy = new ArrayList<>();
  private final List<File> deployInfoFiles = new ArrayList<>();
  private final Map<File, File> filesToDeploy = Maps.newHashMap();
  private File executionRoot;

  BlazeIntellijPluginDeployer(Project project, String sandboxHome, String buildNumber)
      throws ExecutionException {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      throw new ExecutionException("Not synced yet, please sync project");
    }
    this.sandboxHome = sandboxHome;
    this.buildNumber = buildNumber;
    this.targetMap = blazeProjectData.targetMap;
  }

  /** Adds an intellij plugin target to deploy */
  void addTarget(Label label) throws ExecutionException {
    targetsToDeploy.add(label);
  }

  void reportBuildComplete(File executionRoot, BuildResultHelper buildResultHelper) {
    this.executionRoot = executionRoot;
    for (File file : buildResultHelper.getBuildArtifacts()) {
      if (file.getName().endsWith(".intellij-plugin-debug-target-deploy-info")) {
        deployInfoFiles.add(file);
      }
    }
  }

  List<String> deploy() throws ExecutionException {
    List<IntellijPluginDeployInfo> deployInfoList = Lists.newArrayList();
    if (!deployInfoFiles.isEmpty()) {
      for (File deployInfoFile : deployInfoFiles) {
        deployInfoList.addAll(readDeployInfoFromFile(deployInfoFile));
      }
    } else {
      for (Label label : targetsToDeploy) {
        deployInfoList.addAll(findDeployInfoFromBareIntelliJPluginTargets(label));
      }
    }
    ImmutableMap<File, File> filesToDeploy = getFilesToDeploy(executionRoot, deployInfoList);
    this.filesToDeploy.putAll(filesToDeploy);

    for (File file : filesToDeploy.keySet()) {
      if (!file.exists()) {
        throw new ExecutionException(
            String.format("Plugin file '%s' not found. Did the build fail?", file.getName()));
      }
    }
    List<String> pluginIds = readPluginIds(filesToDeploy.keySet());
    for (Map.Entry<File, File> entry : filesToDeploy.entrySet()) {
      copyFileToSandbox(entry.getKey(), entry.getValue());
    }
    return pluginIds;
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

  private ImmutableList<IntellijPluginDeployInfo> findDeployInfoFromBareIntelliJPluginTargets(
      Label label) throws ExecutionException {
    TargetIdeInfo target = targetMap.get(TargetKey.forPlainTarget(label));
    if (target == null) {
      throw new ExecutionException("Target '" + label + "' not imported during sync");
    }
    if (IntellijPluginRule.isSinglePluginTarget(target)) {
      return ImmutableList.of(deployInfoForIntellijPlugin(target));
    }
    throw new ExecutionException("Target is not a supported intellij plugin type.");
  }

  private static IntellijPluginDeployInfo deployInfoForIntellijPlugin(TargetIdeInfo target)
      throws ExecutionException {
    JavaIdeInfo javaIdeInfo = target.javaIdeInfo;
    if (!IntellijPluginRule.isSinglePluginTarget(target) || javaIdeInfo == null) {
      throw new ExecutionException("Target '" + target + "' is not a valid intellij_plugin target");
    }
    Collection<LibraryArtifact> jars = javaIdeInfo.jars;
    if (javaIdeInfo.jars.size() > 1) {
      throw new ExecutionException("Invalid IntelliJ plugin target: it has multiple output jars");
    }
    LibraryArtifact artifact = jars.isEmpty() ? null : jars.iterator().next();
    if (artifact == null || artifact.classJar == null) {
      throw new ExecutionException("No output plugin jar found for '" + target + "'");
    }
    return IntellijPluginDeployInfo.newBuilder()
        .addDeployFiles(
            IntellijPluginDeployFile.newBuilder()
                .setExecutionPath(artifact.classJar.getExecutionRootRelativePath())
                .setDeployLocation(new File(artifact.classJar.relativePath).getName()))
        .build();
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
    }
    return result.build();
  }

  private static File sandboxPluginDirectory(String sandboxHome) {
    return new File(sandboxHome, "plugins");
  }

  private List<String> readPluginIds(Collection<File> files) throws ExecutionException {
    List<String> pluginIds = Lists.newArrayList();
    for (File file : files) {
      if (file.getName().endsWith(".jar")) {
        String pluginId = readPluginIdFromJar(buildNumber, file);
        if (pluginId != null) {
          pluginIds.add(pluginId);
        }
      }
    }
    return pluginIds;
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
    } catch (IOException e) {
      throw new ExecutionException("Error copying plugin file to sandbox", e);
    }
  }
}

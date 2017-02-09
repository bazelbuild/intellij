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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.Dependency.DependencyType;
import com.google.idea.blaze.base.ideinfo.IntellijPluginDeployInfo;
import com.google.idea.blaze.base.ideinfo.IntellijPluginDeployInfo.IntellijPluginDeployFile;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.plugin.IntellijPluginRule;
import com.intellij.execution.ExecutionException;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BuildNumber;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Handles finding files to deploy and copying these into the sandbox. */
class BlazeIntellijPluginDeployer {
  private final String sandboxHome;
  private final String buildNumber;
  private final TargetMap targetMap;
  private final ArtifactLocationDecoder artifactLocationDecoder;
  private Map<File, File> filesToDeploy = Maps.newHashMap();

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
    this.artifactLocationDecoder = blazeProjectData.artifactLocationDecoder;
  }

  /** Adds an intellij plugin target to deploy */
  void addTarget(Label label) throws ExecutionException {
    ImmutableList<IntellijPluginDeployInfo> deployInfos = findDeployInfo(label);
    ImmutableMap<File, File> filesToDeploy = getFilesToDeploy(deployInfos);
    this.filesToDeploy.putAll(filesToDeploy);
  }

  List<String> deploy() throws ExecutionException {
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

  private ImmutableList<IntellijPluginDeployInfo> findDeployInfo(Label label)
      throws ExecutionException {
    TargetIdeInfo target = targetMap.get(TargetKey.forPlainTarget(label));
    if (target == null) {
      throw new ExecutionException("Target '" + label + "' not imported during sync");
    }
    if (IntellijPluginRule.isIntellijPluginDebugTarget(target)) {
      assert target.intellijPluginDeployInfo != null;
      return ImmutableList.of(target.intellijPluginDeployInfo);
    } else if (IntellijPluginRule.isSinglePluginTarget(target)) {
      return ImmutableList.of(deployInfoForIntellijPlugin(target));
    } else if (IntellijPluginRule.isPluginBundle(target)) {
      return deployInfoForLegacyBundle(target);
    }
    throw new ExecutionException("Target is not a supported intellij plugin type.");
  }

  private ImmutableList<IntellijPluginDeployInfo> deployInfoForLegacyBundle(TargetIdeInfo target)
      throws ExecutionException {
    ImmutableList.Builder<IntellijPluginDeployInfo> deployInfoBuilder = ImmutableList.builder();
    for (Dependency dep : target.dependencies) {
      if (dep.dependencyType == DependencyType.COMPILE_TIME && dep.targetKey.isPlainTarget()) {
        TargetIdeInfo depTarget = targetMap.get(dep.targetKey);
        if (depTarget != null && IntellijPluginRule.isSinglePluginTarget(depTarget)) {
          deployInfoBuilder.add(deployInfoForIntellijPlugin(depTarget));
        }
      }
    }
    return deployInfoBuilder.build();
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
    IntellijPluginDeployFile deployFile =
        new IntellijPluginDeployFile(
            artifact.classJar, new File(artifact.classJar.relativePath).getName());
    return new IntellijPluginDeployInfo(ImmutableList.of(deployFile));
  }

  private ImmutableMap<File, File> getFilesToDeploy(
      Collection<IntellijPluginDeployInfo> deployInfos) {
    ImmutableMap.Builder<File, File> result = ImmutableMap.builder();
    for (IntellijPluginDeployInfo deployInfo : deployInfos) {
      for (IntellijPluginDeployFile deployFile : deployInfo.deployFiles) {
        File src = artifactLocationDecoder.decode(deployFile.src);
        File dest = new File(sandboxPluginDirectory(sandboxHome), deployFile.deployLocation);
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

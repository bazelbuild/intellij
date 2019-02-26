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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.PathsList;
import java.io.File;
import java.util.List;

/**
 * Boilerplate for running an IJ application with an additional plugin, copied from
 * org.jetbrains.idea.devkit.run.PluginRunConfiguration
 */
public class IntellijWithPluginClasspathHelper {

  private static final List<String> IJ_LIBRARIES =
      Lists.newArrayList(
          "log4j.jar",
          "trove4j.jar",
          "openapi.jar",
          "util.jar",
          "extensions.jar",
          "bootstrap.jar",
          "idea.jar",
          "idea_rt.jar");

  private static void addIntellijLibraries(JavaParameters params, Sdk ideaJdk) {
    String libPath = ideaJdk.getHomePath() + File.separator + "lib";
    PathsList list = params.getClassPath();
    for (String lib : IJ_LIBRARIES) {
      list.addFirst(libPath + File.separator + lib);
    }
    list.addFirst(((JavaSdkType) ideaJdk.getSdkType()).getToolsPath(ideaJdk));
  }

  public static void addRequiredVmParams(
      JavaParameters params, Sdk ideaJdk, ImmutableSet<File> javaAgents) {
    String canonicalSandbox = IdeaJdkHelper.getSandboxHome(ideaJdk);
    ParametersList vm = params.getVMParametersList();

    String libPath = ideaJdk.getHomePath() + File.separator + "lib";
    vm.add("-Xbootclasspath/a:" + libPath + File.separator + "boot.jar");
    
    vm.defineProperty("idea.config.path", canonicalSandbox + File.separator + "config");
    vm.defineProperty("idea.system.path", canonicalSandbox + File.separator + "system");
    vm.defineProperty("idea.plugins.path", canonicalSandbox + File.separator + "plugins");
    vm.defineProperty("idea.classpath.index.enabled", "false");

    if (SystemInfo.isMac) {
      vm.defineProperty("idea.smooth.progress", "false");
      vm.defineProperty("apple.laf.useScreenMenuBar", "true");
    }

    if (SystemInfo.isXWindow) {
      if (!vm.hasProperty("sun.awt.disablegrab")) {
        vm.defineProperty(
            "sun.awt.disablegrab", "true"); // See http://devnet.jetbrains.net/docs/DOC-1142
      }
    }
    for (File javaAgent : javaAgents) {
      vm.add("-javaagent:" + javaAgent.getAbsolutePath());
    }

    params.setWorkingDirectory(ideaJdk.getHomePath() + File.separator + "bin" + File.separator);
    params.setJdk(ideaJdk);

    addIntellijLibraries(params, ideaJdk);

    params.setMainClass("com.intellij.idea.Main");
  }
}

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
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.PathsList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Boilerplate for running an IJ application with an additional plugin, copied from
 * org.jetbrains.idea.devkit.run.PluginRunConfiguration
 */
public class IntellijWithPluginClasspathHelper {

  private static final ImmutableList<String> IJ_LIBRARIES =
      ImmutableList.of(
          "log4j.jar",
          "trove4j.jar",
          "openapi.jar",
          "util.jar",
          "extensions.jar",
          "bootstrap.jar",
          "idea.jar",
          "idea_rt.jar");

  private static final ImmutableList<String> IJ_LIBRARIES_AFTER_2022_1 =
      ImmutableList.of("util_rt.jar");

  private static final ImmutableList<String> ASWB_LIBRARIES_AFTER_2022_3 =
      ImmutableList.of(
          "app.jar",
          "3rd-party-rt.jar",
          "resources.jar",
          "jps-model.jar",
          "forms_rt.jar",
          "protobuf.jar",
          "stats.jar",
          "annotations.jar");

  private static final Logger logger = Logger.getInstance(IntellijWithPluginClasspathHelper.class);

  private static void addIntellijLibraries(JavaParameters params, Sdk ideaJdk) {
    String libPath = ideaJdk.getHomePath() + File.separator + "lib";
    PathsList list = params.getClassPath();
    for (String lib : IJ_LIBRARIES) {
      list.addFirst(libPath + File.separator + lib);
    }
    String buildNumberStr = IdeaJdkHelper.getBuildNumber(ideaJdk);
    BuildNumber buildNumber = BuildNumber.fromString(buildNumberStr);
    if (buildNumber != null) {
      if (buildNumber.getBaselineVersion() >= 221) {
        for (String lib : IJ_LIBRARIES_AFTER_2022_1) {
          list.addFirst(libPath + File.separator + lib);
        }
      }

      if (buildNumber.getBaselineVersion() >= 223
          && Objects.equals(IdeaJdkHelper.getPlatformPrefix(buildNumberStr), "AndroidStudio")) {
        for (String lib : ASWB_LIBRARIES_AFTER_2022_3) {
          list.addFirst(libPath + File.separator + lib);
        }
      }
    }

    list.addFirst(((JavaSdkType) ideaJdk.getSdkType()).getToolsPath(ideaJdk));
  }

  private static class ProductInfo {
    List<Launch> launch;
  }

  private static class Launch {
    List<String> additionalJvmArguments;
    List<String> bootClassPathJarNames;
    String os;
    String arch;
  }

  @Nullable
  static Launch readLaunchInfo(Sdk ideaJdk) {
    Path location = Paths.get(ideaJdk.getHomePath());
    if (SystemInfo.isMac) {
      location = location.resolve("Resources");
    }
    Path info = location.resolve("product-info.json");
    String os = resolveOsName();
    String arch = System.getProperty("os.arch");

    if (Files.exists(info)) {
      try {
        String json = Files.readString(info);
        ProductInfo productInfo = new Gson().fromJson(json, ProductInfo.class);
        if (productInfo == null) {
          logger.warn("Cannot parse product-info.json");
          return null;
        }
        if (productInfo.launch.isEmpty()) {
          logger.warn("No launch objects found in product-info.json");
          return null;
        }

        return productInfo.launch.stream()
                .filter(l -> Objects.equals(l.os, os) && Objects.equals(l.arch, arch))
                .findFirst()
                .orElse(null);
      } catch (IOException e) {
        logger.error("Error parsing product-info.json", e);
      }
    }
    return null;
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

    Launch launch = readLaunchInfo(ideaJdk);
    if (launch != null && launch.bootClassPathJarNames != null) {
      for (String name : launch.bootClassPathJarNames) {
        params.getClassPath().add(libPath + File.separator + name);
      }
    } else {
      // Fall back to known libraries
      addIntellijLibraries(params, ideaJdk);
    }

    String appPackage = Path.of(ideaJdk.getHomePath()).getParent().toString();
    if (launch != null && launch.additionalJvmArguments != null) {
      for (String arg : launch.additionalJvmArguments) {
        arg = arg.replace("$IDE_HOME", ideaJdk.getHomePath());
        if (SystemInfo.isMac) {
          // Perform replacements that are done by the mac launcher here
          arg = arg.replace("$APP_PACKAGE", appPackage);
        }
        vm.addAll(arg);
      }
    }

    params.setMainClass("com.intellij.idea.Main");
  }

  /**
   * See https://github.com/JetBrains/intellij-community/blob/7015a269e1d7aeeb876a6b86f67ce16c131030da/platform/build-scripts/src/org/jetbrains/intellij/build/product-info.schema.json#L55-L57
   */
  private static String resolveOsName() {
    String osName = System.getProperty("os.name");
    if (Objects.equals(osName, "Linux"))
      return "Linux";
    if (Objects.equals(osName, "Mac OS X"))
      return "macOS";
    if (osName.startsWith("Windows"))
      return "Windows";
    throw new RuntimeException("Could not map Java property 'os.name' to product-info.json schema");
  }
}

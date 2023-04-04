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
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Boilerplate for running an IJ application with an additional plugin, copied from
 * org.jetbrains.idea.devkit.run.PluginRunConfiguration
 */
public class IntellijWithPluginClasspathHelper {

  private static Logger logger = Logger.getInstance(IntellijWithPluginClasspathHelper.class);
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

  private static final ImmutableList<String> IJ_LIBRARIES_AFTER_2022_3 =
      ImmutableList.of(
          "3rd-party-rt.jar",
          "app.jar",
          "xml-dom.jar",
          "xml-dom-impl.jar",
          "jps-model.jar",
          "protobuf.jar",
          "rd.jar",
          "stats.jar",
          "external-system-rt.jar",
          "forms_rt.jar",
          "intellij-test-discovery.jar",
          "annotations.jar",
          "groovy.jar",
          "jsp-base.jar"
      );

  private static final ImmutableList<String> ASWB_LIBRARIES_AFTER_2022_3 =
      ImmutableList.of(
          "app.jar",
          "3rd-party-rt.jar",
          "resources.jar",
          "jps-model.jar",
          "forms_rt.jar",
          "protobuf.jar",
          "stats.jar",
          "annotations.jar"
      );

  // #api222 remove once we drop support for 2022.2. Newer versions should use product-info.json
  private static void addIntellijLibraries(JavaParameters params, Sdk ideaJdk) {
    String libPath = ideaJdk.getHomePath() + File.separator + "lib";
    PathsList list = params.getClassPath();
    addLibrariesToList(IJ_LIBRARIES, libPath, list);

    String buildNumberStr = IdeaJdkHelper.getBuildNumber(ideaJdk);
    BuildNumber buildNumber = BuildNumber.fromString(buildNumberStr);
    if (buildNumber != null) {
      if (buildNumber.getBaselineVersion() >= 221) {
        addLibrariesToList(IJ_LIBRARIES_AFTER_2022_1, libPath, list);
      }
      if (buildNumber.getBaselineVersion() >= 223) {
        if (Objects.equals(IdeaJdkHelper.getPlatformPrefix(buildNumberStr),
                "AndroidStudio")) {
          addLibrariesToList(ASWB_LIBRARIES_AFTER_2022_3, libPath, list);
        } else {
          addLibrariesToList(IJ_LIBRARIES_AFTER_2022_3, libPath, list);
        }
      }
    }
    list.addFirst(((JavaSdkType) ideaJdk.getSdkType()).getToolsPath(ideaJdk));
  }

  private static void addIntellijLibrariesFromProductInfoJson(JavaParameters params, Sdk ideaJdk, ProductInfoJson.LaunchInfo launchInfo) {
    String libPath = ideaJdk.getHomePath() + File.separator + "lib";
    PathsList list = params.getClassPath();
    addLibrariesToList(ImmutableList.copyOf(launchInfo.bootClassPathJarNames), libPath, list);
    list.addFirst(((JavaSdkType) ideaJdk.getSdkType()).getToolsPath(ideaJdk));
  }

  public static ProductInfoJson getProductInfoJson(Sdk ideaJdk) {
    Path productJsonPath = Paths.get(ideaJdk.getHomePath(), "product-info.json");
    Gson gson = new Gson();
    try (Reader reader = Files.newBufferedReader(productJsonPath)) {
      return gson.fromJson(reader, ProductInfoJson.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void addLibrariesToList(ImmutableList<String> ijLibraries, String libPath, PathsList list) {
    for (String lib : ijLibraries) {
      list.addFirst(libPath + File.separator + lib);
    }
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

    BuildNumber buildNumber = BuildNumber.fromString(IdeaJdkHelper.getBuildNumber(ideaJdk));
    if(buildNumber != null && buildNumber.getBaselineVersion() > 223) {
      ProductInfoJson productInfoJson = getProductInfoJson(ideaJdk);
      String os = resolveOsName();
      String arch = System.getProperty("os.arch");
      Optional<ProductInfoJson.LaunchInfo> launchInfo = productInfoJson.launch.stream().filter(l -> Objects.equals(l.os, os) && Objects.equals(l.arch, arch)).findFirst();
      if(launchInfo.isPresent()) {
          addIntellijLibrariesFromProductInfoJson(params, ideaJdk, launchInfo.get());
          for (String productInfoJsonParams : launchInfo.get().additionalJvmArguments) {
              vm.add(productInfoJsonParams);
          }
      } else {
          logger.error(String.format("Could not find 'launch' settings in product-info.json for os:'%s' and arch:'%s'", os, arch));
      }
    } else { // #api223 Drop together with support for 2022.3
      addIntellijLibraries(params, ideaJdk);
    }
    params.setMainClass("com.intellij.idea.Main");
  }

  /**
   * See https://github.com/JetBrains/intellij-community/blob/7015a269e1d7aeeb876a6b86f67ce16c131030da/platform/build-scripts/src/org/jetbrains/intellij/build/product-info.schema.json#L55-L57
   */
  private static String resolveOsName() {
    String osName = System.getProperty("os.name");
    if(Objects.equals(osName, "Linux"))
      return "Linux";
    if(Objects.equals(osName, "Mac OS X"))
      return "macOS";
    if(osName.startsWith("Windows"))
      return "Windows";
    throw new RuntimeException("Could not map Java property 'os.name' to product-info.json schema");
  }
}


/**
 * Class used only to deserialize product-info.json, that's why there are public fields.
 */
class ProductInfoJson {
  static class LaunchInfo {
    public List<String> bootClassPathJarNames;
    public List<String> additionalJvmArguments;
    public String os;
    public String arch;
  }
  public List<LaunchInfo> launch;
}
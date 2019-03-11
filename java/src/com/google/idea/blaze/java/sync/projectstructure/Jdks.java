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
package com.google.idea.blaze.java.sync.projectstructure;

import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static java.util.Collections.emptyList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.sdk.DefaultSdkProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.SystemProperties;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

/** Utility methods related to IDEA JDKs. */
public class Jdks {

  private static final Logger logger = Logger.getInstance(Jdks.class);

  @Nullable
  public static Sdk chooseOrCreateJavaSdk(LanguageLevel langLevel) {
    Sdk existing = findClosestMatch(langLevel);
    if (existing != null) {
      return existing;
    }
    String jdkHomePath = null;
    for (DefaultSdkProvider defaultSdkProvider : DefaultSdkProvider.EP_NAME.getExtensions()) {
      File sdkRoot = defaultSdkProvider.provideSdkForLanguage(LanguageClass.JAVA);
      if (sdkRoot != null) {
        jdkHomePath = sdkRoot.getPath();
        break;
      }
    }

    if (jdkHomePath == null) {
      jdkHomePath = getJdkHomePath(langLevel);
    }
    return jdkHomePath != null ? createJdk(jdkHomePath) : null;
  }

  @Nullable
  @VisibleForTesting
  static Sdk findClosestMatch(LanguageLevel langLevel) {
    return ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()).stream()
        .filter(
            sdk -> {
              LanguageLevel level = getJavaLanguageLevel(sdk);
              return level != null && level.isAtLeast(langLevel);
            })
        .filter(Jdks::isValid)
        .min(Comparator.comparing(Jdks::getJavaLanguageLevel))
        .orElse(null);
  }

  private static boolean isValid(Sdk jdk) {
    // detect the case of JDKs with no-longer-valid roots
    return ApplicationManager.getApplication().isUnitTestMode()
        || jdk.getSdkModificator().getRoots(OrderRootType.CLASSES).length != 0;
  }

  /**
   * Returns null if the SDK is not a java JDK, or doesn't have a recognized java langauge level.
   */
  @Nullable
  private static LanguageLevel getJavaLanguageLevel(Sdk sdk) {
    if (!(sdk.getSdkType() instanceof JavaSdk)) {
      return null;
    }
    JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
    return version != null ? version.getMaxLanguageLevel() : null;
  }

  @Nullable
  private static String getJdkHomePath(LanguageLevel langLevel) {
    Collection<String> jdkHomePaths = new ArrayList<>(JavaSdk.getInstance().suggestHomePaths());
    if (jdkHomePaths.isEmpty()) {
      return null;
    }
    // prefer jdk path of getJavaHome(), since we have to allow access to it in tests
    // see AndroidProjectDataServiceTest#testImportData()
    final List<String> list = new ArrayList<>();
    String javaHome = SystemProperties.getJavaHome();

    if (javaHome != null && !javaHome.isEmpty()) {
      for (Iterator<String> it = jdkHomePaths.iterator(); it.hasNext(); ) {
        final String path = it.next();

        if (path != null && javaHome.startsWith(path)) {
          it.remove();
          list.add(path);
        }
      }
    }
    list.addAll(jdkHomePaths);
    return getBestJdkHomePath(list, langLevel);
  }

  @Nullable
  private static String getBestJdkHomePath(List<String> jdkHomePaths, LanguageLevel langLevel) {
    // Search for JDKs in both the suggest folder and all its sub folders.
    List<String> roots = Lists.newArrayList();
    for (String jdkHomePath : jdkHomePaths) {
      if (StringUtil.isNotEmpty(jdkHomePath)) {
        roots.add(jdkHomePath);
        roots.addAll(getChildrenPaths(jdkHomePath));
      }
    }
    return getBestJdk(roots, langLevel);
  }

  private static List<String> getChildrenPaths(String dirPath) {
    File dir = new File(dirPath);
    if (!dir.isDirectory()) {
      return emptyList();
    }
    List<String> childrenPaths = Lists.newArrayList();
    for (File child : notNullize(dir.listFiles())) {
      boolean directory = child.isDirectory();
      if (directory) {
        childrenPaths.add(child.getAbsolutePath());
      }
    }
    return childrenPaths;
  }

  @Nullable
  private static String getBestJdk(List<String> jdkRoots, LanguageLevel langLevel) {
    return jdkRoots
        .stream()
        .filter(root -> JavaSdk.getInstance().isValidSdkHome(root))
        .filter(root -> getVersion(root).getMaxLanguageLevel().isAtLeast(langLevel))
        .min(Comparator.comparing(o -> getVersion(o).getMaxLanguageLevel()))
        .orElse(null);
  }

  private static JavaSdkVersion getVersion(String jdkRoot) {
    String version = JavaSdk.getInstance().getVersionString(jdkRoot);
    if (version == null) {
      return JavaSdkVersion.JDK_1_0;
    }
    JavaSdkVersion sdkVersion = JavaSdk.getInstance().getVersion(version);
    return sdkVersion == null ? JavaSdkVersion.JDK_1_0 : sdkVersion;
  }

  @Nullable
  private static Sdk createJdk(String jdkHomePath) {
    Sdk jdk = SdkConfigurationUtil.createAndAddSDK(jdkHomePath, JavaSdk.getInstance());
    if (jdk == null) {
      logger.error(String.format("Unable to create JDK from path '%1$s'", jdkHomePath));
    }
    return jdk;
  }
}

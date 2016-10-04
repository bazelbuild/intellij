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
package com.google.idea.blaze.base.lang.buildfile.references;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.RuleName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.util.PathUtil;
import java.util.List;
import javax.annotation.Nullable;

/** Utility methods for working with blaze labels. */
public class LabelUtils {

  /** Label referring to the given file, or null if it cannot be determined. */
  @Nullable
  public static Label createLabelForFile(BlazePackage blazePackage, @Nullable String filePath) {
    if (blazePackage == null || filePath == null) {
      return null;
    }
    String relativeFilePath = blazePackage.getPackageRelativePath(filePath);
    if (relativeFilePath == null) {
      return null;
    }
    return createLabelFromRuleName(blazePackage, relativeFilePath);
  }

  /**
   * Returns null if this is not a valid Label (if either the package path or rule name are invalid)
   */
  @Nullable
  public static Label createLabelFromRuleName(
      @Nullable BlazePackage blazePackage, @Nullable String ruleName) {
    if (blazePackage == null || ruleName == null) {
      return null;
    }
    WorkspacePath packagePath = blazePackage.buildFile.getPackageWorkspacePath();
    RuleName name = RuleName.createIfValid(ruleName);
    if (packagePath == null || name == null) {
      return null;
    }
    // TODO: Is Label too inefficient?
    // (validation done twice,creating List during constructor,
    // re-parsing to extract the package/rule each time)
    return new Label(packagePath, name);
  }

  /**
   * Canonicalizes the label (to the form //packagePath:packageRelativeTarget). Returns null if the
   * string does not represent a valid label.
   */
  @Nullable
  public static Label createLabelFromString(
      @Nullable BuildFile file, @Nullable String labelString) {
    if (labelString == null) {
      return null;
    }
    int colonIndex = labelString.indexOf(':');
    if (labelString.startsWith("//")) {
      if (colonIndex == -1) {
        // add the implicit rule name
        labelString += ":" + PathUtil.getFileName(labelString);
      }
      return Label.createIfValid(labelString);
    }
    WorkspacePath packagePath = file != null ? file.getPackageWorkspacePath() : null;
    if (packagePath == null) {
      return null;
    }
    String localPath = colonIndex == -1 ? labelString : labelString.substring(1);
    return Label.createIfValid("//" + packagePath.relativePath() + ":" + localPath);
  }

  /** The blaze file referenced by the label. */
  @Nullable
  public static BuildFile getReferencedBuildFile(
      @Nullable BuildFile containingFile, String packagePathComponent) {
    if (containingFile == null) {
      return null;
    }
    if (!packagePathComponent.startsWith("//")) {
      return containingFile;
    }
    return BuildReferenceManager.getInstance(containingFile.getProject())
        .resolveBlazePackage(packagePathComponent);
  }

  public static String getRuleComponent(String labelString) {
    if (labelString.startsWith("/")) {
      int colonIndex = labelString.indexOf(':');
      return colonIndex == -1 ? "" : labelString.substring(colonIndex + 1);
    }
    return labelString.startsWith(":") ? labelString.substring(1) : labelString;
  }

  public static String getPackagePathComponent(String labelString) {
    if (!labelString.startsWith("//")) {
      return "";
    }
    int colonIndex = labelString.indexOf(':');
    return colonIndex == -1 ? labelString : labelString.substring(0, colonIndex);
  }

  /** 'load' reference. Of the form [path][/ or :][extra_path/]file_name.bzl */
  @Nullable
  public static String getNiceSkylarkFileName(@Nullable String path) {
    if (path == null) {
      return null;
    }
    int colonIndex = path.lastIndexOf(":");
    if (colonIndex != -1) {
      path = path.substring(colonIndex + 1);
    }
    int lastSlash = path.lastIndexOf("/");
    if (lastSlash == -1) {
      return path;
    }
    return path.substring(lastSlash + 1);
  }

  /**
   * All the possible strings which could resolve to the given target.
   *
   * @param includePackageLocalLabels if true, include strings omitting the package path
   */
  public static List<String> getAllValidLabelStrings(
      Label label, boolean includePackageLocalLabels) {
    List<String> strings = Lists.newArrayList();
    strings.add(label.toString());
    String packagePath = label.blazePackage().relativePath();
    if (packagePath.isEmpty()) {
      return strings;
    }
    String ruleName = label.ruleName().toString();
    if (PathUtil.getFileName(packagePath).equals(ruleName)) {
      strings.add("//" + packagePath); // implicit rule name equal to package name
    }
    if (includePackageLocalLabels) {
      strings.add(":" + ruleName);
      strings.add(ruleName);
    }
    return strings;
  }

  /**
   * IntelliJ inserts an identifier string at the caret position during code completion.<br>
   * We're only interested in the portion of the string before the caret, so trim the rest.
   */
  public static String trimToDummyIdentifier(String string) {
    int dummyIdentifierIndex = string.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER);
    if (dummyIdentifierIndex == -1) {
      dummyIdentifierIndex = string.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);
    }
    return dummyIdentifierIndex == -1 ? string : string.substring(0, dummyIdentifierIndex);
  }
}

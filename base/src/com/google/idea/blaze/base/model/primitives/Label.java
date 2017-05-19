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
package com.google.idea.blaze.base.model.primitives;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.ui.BlazeValidationError;
import com.intellij.openapi.diagnostic.Logger;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** Wrapper around a string for a blaze label ([@external_workspace]//package:rule). */
@Immutable
public final class Label extends TargetExpression {
  private static final Logger logger = Logger.getInstance(Label.class);

  public static final long serialVersionUID = 2L;

  /** Silently returns null if this is not a valid Label */
  @Nullable
  public static Label createIfValid(String label) {
    if (validate(label)) {
      return new Label(label);
    }
    return null;
  }

  public static Label create(String label) {
    List<BlazeValidationError> errors = Lists.newArrayList();
    if (!validate(label, errors)) {
      BlazeValidationError.throwError(errors);
    }
    return new Label(label);
  }

  public static Label create(WorkspacePath packageName, TargetName newTargetName) {
    return create(null, packageName, newTargetName);
  }

  public static Label create(
      @Nullable String externalWorkspaceName, WorkspacePath packagePath, TargetName targetName) {
    String fullLabel =
        String.format(
            "%s//%s:%s",
            externalWorkspaceName != null ? "@" + externalWorkspaceName : "",
            packagePath,
            targetName);
    return new Label(fullLabel);
  }

  private Label(String label) {
    super(label);
  }

  public static boolean validate(String label) {
    return validate(label, null);
  }

  public static boolean validate(String label, @Nullable Collection<BlazeValidationError> errors) {
    int colonIndex = label.indexOf(':');
    if (label.startsWith("//") && colonIndex >= 0) {
      String packageName = label.substring("//".length(), colonIndex);
      if (!validatePackagePath(packageName, errors)) {
        return false;
      }
      String ruleName = label.substring(colonIndex + 1);
      if (!TargetName.validate(ruleName, errors)) {
        return false;
      }
      return true;
    }
    if (label.startsWith("@") && colonIndex >= 0) {
      // a bazel-specific label pointing to a different repository
      int slashIndex = label.indexOf("//");
      if (slashIndex >= 0) {
        return validate(label.substring(slashIndex), errors);
      }
    }
    if (errors != null) {
      errors.add(new BlazeValidationError("Not a valid label, no target name found: " + label));
    }
    return false;
  }

  public boolean isExternal() {
    return toString().startsWith("@");
  }

  /**
   * Returns the external workspace referenced by this label, or null if it's a main workspace
   * label.
   */
  @Nullable
  public String externalWorkspaceName() {
    String label = toString();
    if (!label.startsWith("@")) {
      return null;
    }
    int slashesIndex = label.indexOf("//");
    logger.assertTrue(slashesIndex >= 0);
    return label.substring(1, slashesIndex);
  }

  /**
   * Extract the target name from a label. The target name follows a colon at the end of the label.
   *
   * @return the target name
   */
  public TargetName targetName() {
    String labelStr = toString();
    int colonLocation = labelStr.lastIndexOf(':');
    int targetNameStart = colonLocation + 1;
    String targetNameStr = labelStr.substring(targetNameStart);
    return TargetName.create(targetNameStr);
  }

  /**
   * Return the workspace path for the package label for the given label. For example, if the
   * package is //j/c/g/a/apps/docs:release, it returns j/c/g/a/apps/docs.
   */
  public WorkspacePath blazePackage() {
    String labelStr = toString();
    int startIndex = labelStr.indexOf("//") + "//".length();
    int colonIndex = labelStr.lastIndexOf(':');
    logger.assertTrue(colonIndex >= 0);
    return new WorkspacePath(labelStr.substring(startIndex, colonIndex));
  }

  /** A new label with the same workspace and package paths, but a different target name. */
  @Nullable
  public Label withTargetName(@Nullable String targetName) {
    if (targetName == null) {
      return null;
    }
    TargetName target = TargetName.createIfValid(targetName);
    return target != null ? Label.create(externalWorkspaceName(), blazePackage(), target) : null;
  }

  static boolean validatePackagePath(String path) {
    return validatePackagePath(path, null);
  }

  public static boolean validatePackagePath(
      String path, @Nullable Collection<BlazeValidationError> errors) {
    // Empty packages are legal but not recommended
    if (path.isEmpty()) {
      return true;
    }

    if (path.charAt(0) == '/') {
      BlazeValidationError.collect(
          errors,
          new BlazeValidationError(
              "Invalid package name: " + path + "\n" + "Package names may not start with \"/\"."));
      return false;
    }
    if (path.contains("//")) {
      BlazeValidationError.collect(
          errors,
          new BlazeValidationError(
              "Invalid package name: "
                  + path
                  + "\n "
                  + "package names may not contain \"//\" path separators."));
      return false;
    }
    if (path.endsWith("/")) {
      BlazeValidationError.collect(
          errors,
          new BlazeValidationError(
              "Invalid package name: " + path + "\n " + "package names may not end with \"/\""));
      return false;
    }
    return true;
  }
}

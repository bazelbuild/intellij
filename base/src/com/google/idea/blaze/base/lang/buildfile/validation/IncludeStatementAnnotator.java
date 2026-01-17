/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.validation;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.google.idea.blaze.base.lang.buildfile.psi.IncludeStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;

import javax.annotation.Nullable;

/** Adds warning/error annotations to include statements in MODULE.bazel files. */
public class IncludeStatementAnnotator extends BuildAnnotator {

  @Override
  public void visitIncludeStatement(IncludeStatement node) {
    // Validate that include() is only in MODULE.bazel files
    BuildFile containingFile = node.getContainingFile();
    if (containingFile != null && containingFile.getBlazeFileType() != BlazeFileType.MODULE) {
      markError(node, "include() statements are only allowed in MODULE.bazel files");
      return;
    }

    validateIncludeTarget(node.getImportPsiElement());
  }

  private void validateIncludeTarget(@Nullable StringLiteral target) {
    if (target == null) {
      return;
    }

    String targetString = target.getStringContents();
    if (targetString == null || targetString.isEmpty()) {
      markError(target, "include() requires a file path");
      return;
    }

    // Extract filename from the path
    String filename = extractFilename(targetString);

    // Validate that target ends with .MODULE.bazel
    if (!filename.endsWith(".MODULE.bazel")) {
      markError(target, "Included files must end with .MODULE.bazel");
      return;
    }

    // Don't allow including MODULE.bazel itself (only fragments)
    if (filename.equals("MODULE.bazel")) {
      markError(
          target, "Cannot include MODULE.bazel; only .MODULE.bazel fragments can be included");
    }
  }

  private String extractFilename(String labelString) {
    // Extract filename from label (handle both "file.MODULE.bazel" and "//pkg:file.MODULE.bazel")
    int colonIndex = labelString.lastIndexOf(':');
    if (colonIndex != -1) {
      labelString = labelString.substring(colonIndex + 1);
    }
    int slashIndex = labelString.lastIndexOf('/');
    if (slashIndex != -1) {
      labelString = labelString.substring(slashIndex + 1);
    }
    return labelString;
  }
}

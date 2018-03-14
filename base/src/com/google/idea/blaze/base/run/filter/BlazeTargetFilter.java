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
package com.google.idea.blaze.base.run.filter;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.issueparser.NonProblemHyperlinkInfo;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.lang.buildfile.references.LabelUtils;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.UIUtil;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Parse blaze targets in streamed output. */
public class BlazeTargetFilter implements Filter {

  // See Bazel's LabelValidator class. Whitespace character intentionally not included here.
  private static final String PACKAGE_NAME_CHARS = "a-zA-Z0-9/\\-\\._$()";
  private static final String TARGET_CHARS = "a-zA-Z0-9+,=~#()$_@\\-/";

  // ignore '//' preceded by text (e.g. https://...)
  // format: ([@external_workspace]//package:rule)
  private static final String TARGET_REGEX =
      String.format(
          "(^|[ '\"=])(@[%s]*)?//[%s]*(:[%s]+)?",
          PACKAGE_NAME_CHARS, PACKAGE_NAME_CHARS, TARGET_CHARS);

  @VisibleForTesting static final Pattern TARGET_PATTERN = Pattern.compile(TARGET_REGEX);

  private final Project project;
  private final boolean highlightMatches;

  public BlazeTargetFilter(Project project, boolean highlightMatches) {
    this.project = project;
    this.highlightMatches = highlightMatches;
  }

  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    Matcher matcher = TARGET_PATTERN.matcher(line);
    List<ResultItem> results = new ArrayList<>();
    while (matcher.find()) {
      String labelString = matcher.group();
      String prefix = matcher.group(1);
      if (prefix != null) {
        labelString = labelString.substring(prefix.length());
      }
      Label label = LabelUtils.createLabelFromString(null, labelString);
      if (label == null) {
        continue;
      }
      PsiElement psi = BuildReferenceManager.getInstance(project).resolveLabel(label);
      if (!(psi instanceof NavigatablePsiElement)) {
        continue;
      }
      NonProblemHyperlinkInfo link = project -> ((NavigatablePsiElement) psi).navigate(true);
      int offset = entireLength - line.length();
      results.add(
          new ResultItem(
              matcher.start() + offset, matcher.end() + offset, link, getHighlightAttributes()));
    }
    return results.isEmpty() ? null : new Result(results);
  }

  @Nullable
  private TextAttributes getHighlightAttributes() {
    if (highlightMatches) {
      // normal link highlighting, when we don't expect too many targets in the output
      return null;
    }
    // avoid a sea of blue in sync output: just add a grey underline to navigable targets
    return new TextAttributes(
        UIUtil.getActiveTextColor(),
        null,
        UIUtil.getInactiveTextColor(),
        EffectType.LINE_UNDERSCORE,
        Font.PLAIN);
  }
}

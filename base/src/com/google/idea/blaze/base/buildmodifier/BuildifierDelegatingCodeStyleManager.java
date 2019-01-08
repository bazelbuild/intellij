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
package com.google.idea.blaze.base.buildmodifier;

import static com.google.idea.blaze.base.buildmodifier.BuildFileFormatter.getReplacements;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.google.idea.common.formatter.ExternalFormatterCodeStyleManager;
import com.google.idea.common.formatter.FileBasedFormattingSynchronizer;
import com.google.idea.common.formatter.FileBasedFormattingSynchronizer.Formatter;
import com.google.idea.common.formatter.FormatUtils;
import com.google.idea.common.formatter.FormatUtils.FileContentsProvider;
import com.google.idea.common.formatter.FormatUtils.Replacements;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import java.util.Collection;

/**
 * A {@link CodeStyleManager} implementation which runs buildifier on BUILD files, and otherwise
 * delegates to the existing formatter.
 */
final class BuildifierDelegatingCodeStyleManager extends ExternalFormatterCodeStyleManager {

  BuildifierDelegatingCodeStyleManager(CodeStyleManager original) {
    super(original);
  }

  @Override
  protected boolean overrideFormatterForFile(PsiFile file) {
    return file instanceof BuildFile;
  }

  @Override
  protected void format(PsiFile file, Document document, Collection<TextRange> ranges) {
    if (!(file instanceof BuildFile)) {
      return;
    }
    BlazeFileType type = ((BuildFile) file).getBlazeFileType();
    ListenableFuture<Void> future =
        FileBasedFormattingSynchronizer.applyReplacements(
            file,
            f -> {
              FileContentsProvider fileContents = FileContentsProvider.fromPsiFile(f);
              Replacements replacements = getReplacements(type, fileContents, ranges);
              return new Formatter.Result<>(null, replacements);
            });
    FormatUtils.formatWithProgressDialog(file.getProject(), "Running buildifier", future);
  }
}

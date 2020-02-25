/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.lint;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.lint.AndroidLintIdeSupport;
import com.android.tools.idea.lint.common.LintEditorResult;
import com.android.tools.idea.lint.common.LintExternalAnnotator;
import com.android.tools.idea.lint.common.LintIdeClient;
import com.android.tools.idea.lint.common.LintIdeSupport;
import com.android.tools.lint.detector.api.Desugaring;
import com.android.tools.lint.detector.api.Project;
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFile;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/** Compat class for {@link DesugaringLevelTest} */
public final class DesugaringLevelTestCompat {
  private DesugaringLevelTestCompat() {}

  @NotNull
  public static Set<Desugaring> getDesugaringLevel(PsiFile dummyFile, Module workspaceModule) {
    List<ExternalAnnotator> externalAnnotators =
        ExternalLanguageAnnotators.allForFile(JavaLanguage.INSTANCE, dummyFile).stream()
            .filter(e -> e instanceof LintExternalAnnotator)
            .collect(Collectors.toList());
    assertThat(externalAnnotators).hasSize(1);
    LintExternalAnnotator lintExternalAnnotator = (LintExternalAnnotator) externalAnnotators.get(0);

    LintEditorResult lintEditorResult = lintExternalAnnotator.collectInformation(dummyFile);
    assertThat(lintEditorResult).isNotNull();

    LintIdeSupport lintIdeSupport = LintIdeSupport.get();
    assertThat(lintIdeSupport).isInstanceOf(AndroidLintIdeSupport.class);
    AndroidLintIdeSupport androidLintIdeSupport = (AndroidLintIdeSupport) lintIdeSupport;

    LintIdeClient lintIdeClient = androidLintIdeSupport.createEditorClient(lintEditorResult);
    Project lintProject =
        androidLintIdeSupport.createProjectForSingleFile(
                lintIdeClient, dummyFile.getVirtualFile(), workspaceModule)
            .first;
    return lintIdeClient.getDesugaring(lintProject);
  }
}

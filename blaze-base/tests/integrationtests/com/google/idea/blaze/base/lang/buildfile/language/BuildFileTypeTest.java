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
package com.google.idea.blaze.base.lang.buildfile.language;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.intellij.psi.PsiFile;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests that BUILD files are recognized as such
 */
public class BuildFileTypeTest extends BuildFileIntegrationTestCase {

  public void testSkylarkExtensionRecognized() {
    PsiFile file = createPsiFile("java/com/google/foo/build_defs.bzl");
    assertThat(file).isInstanceOf(BuildFile.class);
  }

  public void testExactNameMatch() {
    PsiFile file = createPsiFile("java/com/google/foo/BUILD");
    assertThat(file).isInstanceOf(BuildFile.class);
  }

  /**
   * We may want to support these in the future (and in the meantime the user can manually have them recognized as BUILD files,
   * for syntax highlighting, etc.).<br>
   * Currently, turned off by default because references won't resolve correctly -- they'll point back to normal BUILD files.
   */
  public void testOtherBuildFilesNotRecognized() {
    PsiFile file = createPsiFile("java/com/google/foo/BUILD.tools");
    assertThat(file).isNotInstanceOf(BuildFile.class);

    file = createPsiFile("java/com/google/foo/BUILD.bazel");
    assertThat(file).isNotInstanceOf(BuildFile.class);
  }

}

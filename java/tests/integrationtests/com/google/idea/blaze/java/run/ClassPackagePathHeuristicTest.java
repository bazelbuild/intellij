/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiFile;
import java.io.File;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link ClassPackagePathHeuristic}. */
@RunWith(JUnit4.class)
public class ClassPackagePathHeuristicTest extends BlazeIntegrationTestCase {

  @Test
  public void testMatchesExactPackageClassPath() {
    PsiFile psiFile =
        workspace.createPsiFile(
            new WorkspacePath("com/google/lib/JavaClass.java"),
            "package com.google.lib;",
            "public class JavaClass {}");
    File file = new File(psiFile.getVirtualFile().getPath());

    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:com/google/lib/JavaClass")
            .setKind("java_test")
            .build()
            .toTargetInfo();
    assertThat(
            new ClassPackagePathHeuristic()
                .matchesSource(getProject(), target, psiFile, file, null))
        .isTrue();
  }

  @Test
  public void testMatchesPackageClassPathWithSomePrefix() {
    PsiFile psiFile =
        workspace.createPsiFile(
            new WorkspacePath("com/google/lib/JavaClass.java"),
            "package com.google.lib;",
            "public class JavaClass {}");
    File file = new File(psiFile.getVirtualFile().getPath());

    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:some_prefix_com/google/lib/JavaClass")
            .setKind("java_test")
            .build()
            .toTargetInfo();
    assertThat(
            new ClassPackagePathHeuristic()
                .matchesSource(getProject(), target, psiFile, file, null))
        .isTrue();
  }

  @Test
  public void testMatchesPackageClassPathWithSomeSuffix() {
    PsiFile psiFile =
        workspace.createPsiFile(
            new WorkspacePath("com/google/lib/JavaClass.java"),
            "package com.google.lib;",
            "public class JavaClass {}");
    File file = new File(psiFile.getVirtualFile().getPath());

    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:com/google/lib/JavaClass_some_suffix")
            .setKind("java_test")
            .build()
            .toTargetInfo();
    assertThat(
            new ClassPackagePathHeuristic()
                .matchesSource(getProject(), target, psiFile, file, null))
        .isTrue();
  }

  @Test
  public void testMatchesPackageClassPathWithPrefixAndSuffix() {
    PsiFile psiFile =
        workspace.createPsiFile(
            new WorkspacePath("com/google/lib/JavaClass.java"),
            "package com.google.lib;",
            "public class JavaClass {}");
    File file = new File(psiFile.getVirtualFile().getPath());

    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:some_prefix_com/google/lib/JavaClass_some_suffix")
            .setKind("java_test")
            .build()
            .toTargetInfo();
    assertThat(
            new ClassPackagePathHeuristic()
                .matchesSource(getProject(), target, psiFile, file, null))
        .isTrue();
  }

  @Test
  public void testDoesNotMatchIncompleteClassPackagePath() {
    PsiFile psiFile =
        workspace.createPsiFile(
            new WorkspacePath("com/google/lib/JavaClass.java"),
            "package com.google.lib;",
            "public class JavaClass {}");
    File file = new File(psiFile.getVirtualFile().getPath());

    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:JavaClass")
            .setKind("java_test")
            .build()
            .toTargetInfo();
    assertThat(
            new ClassPackagePathHeuristic()
                .matchesSource(getProject(), target, psiFile, file, null))
        .isFalse();

    target =
        TargetIdeInfo.builder()
            .setLabel("//foo:lib/JavaClass")
            .setKind("java_test")
            .build()
            .toTargetInfo();
    assertThat(
            new ClassPackagePathHeuristic()
                .matchesSource(getProject(), target, psiFile, file, null))
        .isFalse();
  }
}

/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.projectsystem;

import com.google.common.truth.Truth;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TransitiveClosureClassFileFinder}. */
@RunWith(JUnit4.class)
public class TransitiveClosureClassFileFinderTest {
  @Test
  public void testGetNonResourceJars() throws Exception {
    LibraryArtifact.Builder normalJar =
        LibraryArtifact.builder()
            .setClassJar(ArtifactLocation.builder().setRelativePath("binks/jar.jar").build());
    LibraryArtifact.Builder resourceJar =
        LibraryArtifact.builder()
            .setClassJar(ArtifactLocation.builder().setRelativePath("fridge/jam.jar").build());

    TargetIdeInfo info =
        TargetIdeInfo.builder()
            .setJavaInfo(JavaIdeInfo.builder().addJar(normalJar).addJar(resourceJar))
            .setAndroidInfo(AndroidIdeInfo.builder().setResourceJar(resourceJar))
            .build();

    Truth.assertThat(
            TransitiveClosureClassFileFinder.getNonResourceJars(info).collect(Collectors.toList()))
        .containsExactly(normalJar.build());
  }
}

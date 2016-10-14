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
package com.google.idea.blaze.base.sync.workspace;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link ArtifactLocationDecoder}. */
@RunWith(JUnit4.class)
public class ArtifactLocationDecoderTest extends BlazeTestCase {

  private static final String EXECUTION_ROOT = "/path/to/_blaze_user/1234bf129e/root";

  static class MockFileAttributeProvider extends FileAttributeProvider {
    final Set<File> files = Sets.newHashSet();

    void addFiles(@NotNull File... files) {
      this.files.addAll(Lists.newArrayList(files));
    }

    @Override
    public boolean exists(@NotNull File file) {
      return files.contains(file);
    }
  }

  private MockFileAttributeProvider fileChecker;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);

    fileChecker = new MockFileAttributeProvider();
    applicationServices.register(FileAttributeProvider.class, fileChecker);
  }

  @Test
  public void testManualPackagePaths() throws Exception {
    List<File> packagePaths =
        ImmutableList.of(
            new File("/path/to"),
            new File("/path/to/READONLY/root"),
            new File("/path/to/CUSTOM/root"));

    BlazeRoots blazeRoots =
        new BlazeRoots(
            new File(EXECUTION_ROOT),
            packagePaths,
            new ExecutionRootPath("root/blaze-out/crosstool/bin"),
            new ExecutionRootPath("root/blaze-out/crosstool/genfiles"));

    fileChecker.addFiles(
        new File("/path/to/com/google/Bla.java"),
        new File("/path/to/READONLY/root/com/google/Foo.java"),
        new File("/path/to/CUSTOM/root/com/other/Test.java"));

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoderImpl(
            blazeRoots,
            new WorkspacePathResolverImpl(
                new WorkspaceRoot(new File("/path/to/root")), blazeRoots));

    ArtifactLocation blah =
        ArtifactLocation.builder().setRelativePath("com/google/Bla.java").setIsSource(true).build();
    assertThat(decoder.decode(blah).getPath()).isEqualTo("/path/to/com/google/Bla.java");

    ArtifactLocation foo =
        ArtifactLocation.builder().setRelativePath("com/google/Foo.java").setIsSource(true).build();
    assertThat(decoder.decode(foo).getPath())
        .isEqualTo("/path/to/READONLY/root/com/google/Foo.java");

    ArtifactLocation test =
        ArtifactLocation.builder().setRelativePath("com/other/Test.java").setIsSource(true).build();
    assertThat(decoder.decode(test).getPath())
        .isEqualTo("/path/to/CUSTOM/root/com/other/Test.java");

    ArtifactLocation.Builder temp =
        ArtifactLocation.builder().setRelativePath("third_party/other/Temp.java").setIsSource(true);
    assertThat(decoder.decode(temp.build()).getPath())
        .isEqualTo("/path/to/third_party/other/Temp.java");
  }

  @Test
  public void testGeneratedArtifact() throws Exception {
    ArtifactLocation artifactLocation =
        ArtifactLocation.builder()
            .setRootExecutionPathFragment("/blaze-out/bin")
            .setRelativePath("com/google/Bla.java")
            .setIsSource(false)
            .build();

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoderImpl(
            new BlazeRoots(
                new File(EXECUTION_ROOT),
                ImmutableList.of(new File("/path/to/root")),
                new ExecutionRootPath("root/blaze-out/crosstool/bin"),
                new ExecutionRootPath("root/blaze-out/crosstool/genfiles")),
            null);

    assertThat(decoder.decode(artifactLocation).getPath())
        .isEqualTo(EXECUTION_ROOT + "/blaze-out/bin/com/google/Bla.java");
  }
}

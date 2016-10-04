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
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.AndroidStudioIdeInfo;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.PackageManifestOuterClass;
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

  private static final WorkspaceRoot WORKSPACE_ROOT = new WorkspaceRoot(new File("/path/to/root"));
  private static final String EXECUTION_ROOT = "/path/to/_blaze_user/1234bf129e/root";

  private static final BlazeRoots BLAZE_GIT5_ROOTS =
      new BlazeRoots(
          new File(EXECUTION_ROOT),
          ImmutableList.of(
              WORKSPACE_ROOT.directory(),
              new File(WORKSPACE_ROOT.directory().getParentFile(), "READONLY/root")),
          new ExecutionRootPath("root/blaze-out/crosstool/bin"),
          new ExecutionRootPath("root/blaze-out/crosstool/genfiles"));

  private static final BlazeRoots BLAZE_CITC_ROOTS =
      new BlazeRoots(
          new File(EXECUTION_ROOT),
          ImmutableList.of(WORKSPACE_ROOT.directory()),
          new ExecutionRootPath("root/blaze-out/crosstool/bin"),
          new ExecutionRootPath("root/blaze-out/crosstool/genfiles"));

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
            WORKSPACE_ROOT.directory(),
            new File(WORKSPACE_ROOT.directory().getParentFile(), "READONLY/root"),
            new File(WORKSPACE_ROOT.directory().getParentFile(), "CUSTOM/root"));

    BlazeRoots blazeRoots =
        new BlazeRoots(
            new File(EXECUTION_ROOT),
            packagePaths,
            new ExecutionRootPath("root/blaze-out/crosstool/bin"),
            new ExecutionRootPath("root/blaze-out/crosstool/genfiles"));

    fileChecker.addFiles(
        new File(packagePaths.get(0), "com/google/Bla.java"),
        new File(packagePaths.get(1), "com/google/Foo.java"),
        new File(packagePaths.get(2), "com/other/Test.java"));

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoder(
            blazeRoots, new WorkspacePathResolverImpl(WORKSPACE_ROOT, blazeRoots));

    ArtifactLocationBuilder builder =
        new ArtifactLocationBuilder().setRelativePath("com/google/Bla.java").setIsSource(true);

    assertThat(decoder.decode(builder.buildIdeInfoArtifact()).getRootPath())
        .isEqualTo(packagePaths.get(0).toString());

    builder.setRelativePath("com/google/Foo.java");

    assertThat(decoder.decode(builder.buildIdeInfoArtifact()).getRootPath())
        .isEqualTo(packagePaths.get(1).toString());

    builder.setRelativePath("com/other/Test.java");

    assertThat(decoder.decode(builder.buildIdeInfoArtifact()).getRootPath())
        .isEqualTo(packagePaths.get(2).toString());

    builder.setRelativePath("third_party/other/Temp.java");

    assertThat(decoder.decode(builder.buildIdeInfoArtifact())).isNull();
  }

  @Test
  public void testDerivedArtifact() throws Exception {
    ArtifactLocationBuilder builder =
        new ArtifactLocationBuilder()
            .setRootExecutionPathFragment("/blaze-out/bin")
            .setRelativePath("com/google/Bla.java")
            .setIsSource(false);

    ArtifactLocationDecoder decoder = new ArtifactLocationDecoder(BLAZE_CITC_ROOTS, null);

    ArtifactLocation parsed = decoder.decode(builder.buildIdeInfoArtifact());

    assertThat(parsed).isEqualTo(decoder.decode(builder.buildManifestArtifact()));

    assertThat(parsed)
        .isEqualTo(
            ArtifactLocation.builder()
                .setRootPath(EXECUTION_ROOT + "/blaze-out/bin")
                .setRootExecutionPathFragment("/blaze-out/bin")
                .setRelativePath("com/google/Bla.java")
                .setIsSource(false)
                .build());
  }

  @Test
  public void testSourceArtifactAllVersions() throws Exception {
    ArtifactLocationBuilder builder =
        new ArtifactLocationBuilder().setRelativePath("com/google/Bla.java").setIsSource(true);

    ArtifactLocationDecoder decoder =
        new ArtifactLocationDecoder(
            BLAZE_CITC_ROOTS, new WorkspacePathResolverImpl(WORKSPACE_ROOT, BLAZE_CITC_ROOTS));

    ArtifactLocation parsed = decoder.decode(builder.buildIdeInfoArtifact());

    assertThat(parsed).isEqualTo(decoder.decode(builder.buildManifestArtifact()));

    assertThat(parsed)
        .isEqualTo(
            ArtifactLocation.builder()
                .setRootPath(WORKSPACE_ROOT.toString())
                .setRelativePath("com/google/Bla.java")
                .setIsSource(true)
                .build());
  }

  static class ArtifactLocationBuilder {
    String rootExecutionPathFragment = "";
    String relativePath;
    boolean isSource;

    ArtifactLocationBuilder setRootExecutionPathFragment(String rootExecutionPathFragment) {
      this.rootExecutionPathFragment = rootExecutionPathFragment;
      return this;
    }

    ArtifactLocationBuilder setRelativePath(String relativePath) {
      this.relativePath = relativePath;
      return this;
    }

    ArtifactLocationBuilder setIsSource(boolean isSource) {
      this.isSource = isSource;
      return this;
    }

    AndroidStudioIdeInfo.ArtifactLocation buildIdeInfoArtifact() {
      AndroidStudioIdeInfo.ArtifactLocation.Builder builder =
          AndroidStudioIdeInfo.ArtifactLocation.newBuilder()
              .setIsSource(isSource)
              .setRelativePath(relativePath);
      builder.setRootExecutionPathFragment(rootExecutionPathFragment);
      return builder.build();
    }

    PackageManifestOuterClass.ArtifactLocation buildManifestArtifact() {
      PackageManifestOuterClass.ArtifactLocation.Builder builder =
          PackageManifestOuterClass.ArtifactLocation.newBuilder()
              .setIsSource(isSource)
              .setRelativePath(relativePath);
      builder.setRootExecutionPathFragment(rootExecutionPathFragment);
      return builder.build();
    }
  }
}

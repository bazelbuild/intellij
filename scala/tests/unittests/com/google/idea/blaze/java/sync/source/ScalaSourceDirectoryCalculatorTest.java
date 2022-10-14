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
package com.google.idea.blaze.java.sync.source;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.InputStreamProvider;
import com.google.idea.blaze.base.io.MockInputStreamProvider;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.MockPrefetchService;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.MockRemoteArtifactPrefetcher;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;
import com.google.idea.blaze.scala.ScalaJavaLikeLanguage;
import com.intellij.openapi.extensions.ExtensionPoint;
import java.io.File;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link SourceDirectoryCalculator} with Scala sources. */
@RunWith(JUnit4.class)
public class ScalaSourceDirectoryCalculatorTest extends BlazeTestCase {
  private MockInputStreamProvider mockInputStreamProvider;
  private SourceDirectoryCalculator sourceDirectoryCalculator;
  private final BlazeContext context = BlazeContext.create();
  private final ErrorCollector issues = new ErrorCollector();
  private final WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/root"));

  private final ArtifactLocationDecoder decoder =
      new MockArtifactLocationDecoder() {
        @Override
        public File decode(ArtifactLocation artifactLocation) {
          return new File("/root", artifactLocation.getRelativePath());
        }
      };

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);

    mockInputStreamProvider = new MockInputStreamProvider();
    applicationServices.register(InputStreamProvider.class, mockInputStreamProvider);
    applicationServices.register(JavaSourcePackageReader.class, new JavaSourcePackageReader());
    applicationServices.register(PackageManifestReader.class, new PackageManifestReader());
    applicationServices.register(PrefetchService.class, new MockPrefetchService());
    applicationServices.register(FileOperationProvider.class, new FileOperationProvider());
    applicationServices.register(
        RemoteArtifactPrefetcher.class, new MockRemoteArtifactPrefetcher());

    ExtensionPoint<JavaLikeLanguage> javaLikeLanguages =
        registerExtensionPoint(JavaLikeLanguage.EP_NAME, JavaLikeLanguage.class);
    javaLikeLanguages.registerExtension(new JavaLikeLanguage.Java());
    javaLikeLanguages.registerExtension(new ScalaJavaLikeLanguage());

    context.addOutputSink(IssueOutput.class, issues);
    sourceDirectoryCalculator = new SourceDirectoryCalculator();
  }

  @Test
  public void testSingleScalaSource() {
    mockInputStreamProvider.addFile(
        "/root/src/main/scala/com/google/Foo.scala", "package com.google\n public class Foo {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(
                    TargetKey.forPlainTarget(Label.create("//src/main/scala/com/google:foo")))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("src/main/scala/com/google/Foo.scala")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(new WorkspacePath("src/main/scala/com/google")),
            sourceArtifacts,
            ImmutableMap.of());
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/src/main/scala/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/scala/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .build());
    issues.assertNoIssues();
  }

  @Test
  public void testMultipleScalaSources() {
    mockInputStreamProvider
        .addFile(
            "/root/src/main/scala/com/google/Foo.scala",
            "package com.google;\n public class Foo {}")
        .addFile(
            "/root/src/main/scala/com/google/Bar.scala", "package com.google\n public class Bar {}")
        .addFile(
            "/root/src/main/scala/com/alphabet/Baz.scala",
            "package com.alphabet {\n public class Baz {} }");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(
                    TargetKey.forPlainTarget(Label.create("//src/main/scala/com/google:foo")))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("src/main/scala/com/google/Foo.scala")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(
                    TargetKey.forPlainTarget(Label.create("//src/main/scala/com/google:bar")))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("src/main/scala/com/google/Bar.scala")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(
                    TargetKey.forPlainTarget(Label.create("//src/main/scala/com/alphabet:baz")))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("src/main/scala/com/alphabet/Baz.scala")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                new WorkspacePath("src/main/scala/com/google"),
                new WorkspacePath("src/main/scala/com/alphabet")),
            sourceArtifacts,
            ImmutableMap.of());
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/src/main/scala/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/scala/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .build(),
            BlazeContentEntry.builder("/root/src/main/scala/com/alphabet")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/scala/com/alphabet")
                        .setPackagePrefix("com.alphabet")
                        .build())
                .build());
    issues.assertNoIssues();
  }

  @Test
  public void testMixedScalaAndJavaSources() {
    mockInputStreamProvider
        .addFile(
            "/root/src/main/java/com/google/Foo.java", "package com.google;\n public class Foo {}")
        .addFile(
            "/root/src/main/scala/com/google/Bar.scala", "package com.google\n public class Bar {}")
        .addFile(
            "/root/src/main/scala/com/alphabet/Baz.scala",
            "package com.alphabet {\n public class Baz {} }");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(
                    TargetKey.forPlainTarget(Label.create("//src/main/java/com/google:foo")))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("src/main/java/com/google/Foo.java")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(
                    TargetKey.forPlainTarget(Label.create("//src/main/scala/com/google:bar")))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("src/main/scala/com/google/Bar.scala")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(
                    TargetKey.forPlainTarget(Label.create("//src/main/scala/com/alphabet:baz")))
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("src/main/scala/com/alphabet/Baz.scala")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            decoder,
            buildImportRoots(
                new WorkspacePath("src/main/java/com/google"),
                new WorkspacePath("src/main/scala/com/google"),
                new WorkspacePath("src/main/scala/com/alphabet")),
            sourceArtifacts,
            ImmutableMap.of());
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/src/main/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .build(),
            BlazeContentEntry.builder("/root/src/main/scala/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/scala/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .build(),
            BlazeContentEntry.builder("/root/src/main/scala/com/alphabet")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/scala/com/alphabet")
                        .setPackagePrefix("com.alphabet")
                        .build())
                .build());
    issues.assertNoIssues();
  }

  private ImportRoots buildImportRoots(WorkspacePath... rootDirectories) {
    ImportRoots.Builder builder = ImportRoots.builder(workspaceRoot, BuildSystemName.Blaze);
    for (WorkspacePath path : rootDirectories) {
      builder.add(DirectoryEntry.include(path));
    }
    return builder.build();
  }
}

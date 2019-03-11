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
package com.google.idea.blaze.base.command.buildresult;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.TargetCompletedId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.TargetConfiguredId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.TestResultId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.NamedSetOfFiles;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.TargetComplete;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.TargetConfigured;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.TestResult;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules.RuleTypes;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult.TestStatus;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Unit tests for {@link BuildEventProtocolOutputReader}. */
@RunWith(Parameterized.class)
public class BuildEventProtocolOutputReaderTest extends BlazeTestCase {

  @Parameters
  public static Collection<Object[]> data() {
    return ImmutableList.of(new Object[] {true}, new Object[] {false});
  }

  // BEP file URI format changed from 'file://[abs_path]' to 'file:[abs_path]'
  @Parameter public boolean useOldFormatFileUri = false;

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    ExtensionPointImpl<Kind.Provider> ep =
        registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    ep.registerExtension(new GenericBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());
  }

  @Test
  public void parseAllOutputFilenames_singleFileEvent_returnsAllFilenames() throws IOException {
    ImmutableList<String> filePaths =
        ImmutableList.of(
            "/usr/local/lib/File.py", "/usr/bin/python2.7", "/usr/local/home/script.sh");
    BuildEvent.Builder event = BuildEvent.newBuilder().setNamedSetOfFiles(setOfFiles(filePaths));

    ImmutableList<OutputArtifact> parsedFilenames =
        BuildEventProtocolOutputReader.parseAllOutputFilenames(asInputStream(event), path -> true);

    assertThat(parsedFilenames)
        .containsExactly(
            filePaths.stream().map(File::new).map(LocalFileOutputArtifact::new).toArray())
        .inOrder();
  }

  @Test
  public void parseAllOutputFilenamesWithFilter_singleFileEvent_returnsFilteredFilenames()
      throws IOException {
    Predicate<String> filter = path -> path.endsWith(".py");
    ImmutableList<String> filePaths =
        ImmutableList.of(
            "/usr/local/lib/File.py", "/usr/bin/python2.7", "/usr/local/home/script.sh");
    BuildEvent.Builder event = BuildEvent.newBuilder().setNamedSetOfFiles(setOfFiles(filePaths));

    ImmutableList<OutputArtifact> parsedFilenames =
        BuildEventProtocolOutputReader.parseAllOutputFilenames(asInputStream(event), filter);

    assertThat(parsedFilenames)
        .containsExactly(new LocalFileOutputArtifact(new File("/usr/local/lib/File.py")));
  }

  @Test
  public void parseAllOutputFilenames_nonFileEvent_returnsEmptyList() throws IOException {
    BuildEvent.Builder targetFinishedEvent =
        BuildEvent.newBuilder()
            .setCompleted(BuildEventStreamProtos.TargetComplete.getDefaultInstance());

    ImmutableList<OutputArtifact> parsedFilenames =
        BuildEventProtocolOutputReader.parseAllOutputFilenames(
            asInputStream(targetFinishedEvent), path -> true);

    assertThat(parsedFilenames).isEmpty();
  }

  @Test
  public void parseAllOutputFilenames_streamWithOneFileEvent_returnsAllFilenames()
      throws IOException {
    ImmutableList<String> filePaths =
        ImmutableList.of(
            "/usr/local/lib/Provider.java",
            "/usr/local/home/Executor.java",
            "/google/code/script.sh");
    List<BuildEvent.Builder> events =
        ImmutableList.of(
            BuildEvent.newBuilder()
                .setStarted(BuildEventStreamProtos.BuildStarted.getDefaultInstance()),
            BuildEvent.newBuilder()
                .setProgress(BuildEventStreamProtos.Progress.getDefaultInstance()),
            BuildEvent.newBuilder().setNamedSetOfFiles(setOfFiles(filePaths)));

    ImmutableList<OutputArtifact> parsedFilenames =
        BuildEventProtocolOutputReader.parseAllOutputFilenames(asInputStream(events), path -> true);

    assertThat(parsedFilenames)
        .containsExactly(
            filePaths.stream().map(File::new).map(LocalFileOutputArtifact::new).toArray())
        .inOrder();
  }

  @Test
  public void parseAllOutputFilenames_streamWithMultipleFileEvents_returnsAllFilenames()
      throws IOException {
    ImmutableList<String> fileSet1 =
        ImmutableList.of(
            "/usr/local/lib/Provider.java",
            "/usr/local/home/Executor.java",
            "/google/code/script.sh");

    ImmutableList<String> fileSet2 =
        ImmutableList.of(
            "/usr/local/code/ParserTest.java",
            "/usr/local/code/action_output.bzl",
            "/usr/genfiles/BUILD.bazel");

    List<BuildEvent.Builder> events =
        ImmutableList.of(
            BuildEvent.newBuilder()
                .setStarted(BuildEventStreamProtos.BuildStarted.getDefaultInstance()),
            BuildEvent.newBuilder()
                .setProgress(BuildEventStreamProtos.Progress.getDefaultInstance()),
            BuildEvent.newBuilder().setNamedSetOfFiles(setOfFiles(fileSet1)),
            BuildEvent.newBuilder()
                .setProgress(BuildEventStreamProtos.Progress.getDefaultInstance()),
            BuildEvent.newBuilder().setNamedSetOfFiles(setOfFiles(fileSet2)),
            BuildEvent.newBuilder()
                .setCompleted(BuildEventStreamProtos.TargetComplete.getDefaultInstance()));

    ImmutableList<OutputArtifact> allFiles =
        ImmutableList.<String>builder().addAll(fileSet1).addAll(fileSet2).build().stream()
            .map(File::new)
            .map(LocalFileOutputArtifact::new)
            .collect(toImmutableList());

    ImmutableList<OutputArtifact> parsedFilenames =
        BuildEventProtocolOutputReader.parseAllOutputFilenames(asInputStream(events), path -> true);

    assertThat(parsedFilenames).containsExactlyElementsIn(allFiles).inOrder();
  }

  @Test
  public void parseAllOutputFilenames_streamWithDuplicateFiles_returnsUniqueFilenames()
      throws IOException {
    ImmutableList<String> fileSet1 = ImmutableList.of("/usr/out/genfiles/foo.pb.h");

    ImmutableList<String> fileSet2 =
        ImmutableList.of("/usr/out/genfiles/foo.pb.h", "/usr/out/genfiles/foo.proto.h");

    List<BuildEvent.Builder> events =
        ImmutableList.of(
            BuildEvent.newBuilder()
                .setStarted(BuildEventStreamProtos.BuildStarted.getDefaultInstance()),
            BuildEvent.newBuilder()
                .setProgress(BuildEventStreamProtos.Progress.getDefaultInstance()),
            BuildEvent.newBuilder().setNamedSetOfFiles(setOfFiles(fileSet1)),
            BuildEvent.newBuilder()
                .setProgress(BuildEventStreamProtos.Progress.getDefaultInstance()),
            BuildEvent.newBuilder().setNamedSetOfFiles(setOfFiles(fileSet2)),
            BuildEvent.newBuilder()
                .setCompleted(BuildEventStreamProtos.TargetComplete.getDefaultInstance()));

    ImmutableList<OutputArtifact> allFiles =
        ImmutableSet.of("/usr/out/genfiles/foo.pb.h", "/usr/out/genfiles/foo.proto.h").stream()
            .map(File::new)
            .map(LocalFileOutputArtifact::new)
            .collect(toImmutableList());

    ImmutableList<OutputArtifact> parsedFilenames =
        BuildEventProtocolOutputReader.parseAllOutputFilenames(asInputStream(events), path -> true);

    assertThat(parsedFilenames).containsExactlyElementsIn(allFiles).inOrder();
  }

  @Test
  public void testStatusEnum_handlesAllProtoEnumValues() {
    Set<String> protoValues =
        EnumSet.allOf(BuildEventStreamProtos.TestStatus.class)
            .stream()
            .map(Enum::name)
            .collect(Collectors.toSet());
    protoValues.remove(BuildEventStreamProtos.TestStatus.UNRECOGNIZED.name());
    Set<String> handledValues =
        EnumSet.allOf(TestStatus.class).stream().map(Enum::name).collect(Collectors.toSet());

    assertThat(protoValues).containsExactlyElementsIn(handledValues);
  }

  @Test
  public void parseTestResults_singleEvent_returnsTestResults() throws IOException {
    Label label = Label.create("//java/com/google:unit_tests");
    BuildEventStreamProtos.TestStatus status = BuildEventStreamProtos.TestStatus.FAILED;
    ImmutableList<String> filePaths = ImmutableList.of("/usr/local/tmp/_cache/test_result.xml");
    BuildEvent.Builder event = testResultEvent(label.toString(), status, filePaths);

    BlazeTestResults results =
        BuildEventProtocolOutputReader.parseTestResults(asInputStream(event));

    assertThat(results.perTargetResults.keySet()).containsExactly(label);
    assertThat(results.perTargetResults.get(label)).hasSize(1);
    BlazeTestResult result = results.perTargetResults.get(label).iterator().next();
    assertThat(result.getTestStatus()).isEqualTo(TestStatus.FAILED);
    assertThat(getOutputXmlFiles(result))
        .containsExactly(new File("/usr/local/tmp/_cache/test_result.xml"));
  }

  @Test
  public void parseTestResults_singleTestEventWithTargetConfigured_resultsIncludeTargetKind()
      throws IOException {
    Label label = Label.create("//java/com/google:unit_tests");
    BuildEventStreamProtos.TestStatus status = BuildEventStreamProtos.TestStatus.FAILED;
    ImmutableList<String> filePaths = ImmutableList.of("/usr/local/tmp/_cache/test_result.xml");
    InputStream events =
        asInputStream(
            targetConfiguredEvent(label.toString(), "sh_test rule"),
            testResultEvent(label.toString(), status, filePaths));

    BlazeTestResults results = BuildEventProtocolOutputReader.parseTestResults(events);

    assertThat(results.perTargetResults.keySet()).containsExactly(label);
    assertThat(results.perTargetResults.get(label)).hasSize(1);
    BlazeTestResult result = results.perTargetResults.get(label).iterator().next();
    assertThat(result.getTargetKind()).isEqualTo(RuleTypes.SH_TEST.getKind());
    assertThat(result.getTestStatus()).isEqualTo(TestStatus.FAILED);
    assertThat(getOutputXmlFiles(result))
        .containsExactly(new File("/usr/local/tmp/_cache/test_result.xml"));
  }

  @Test
  public void parseTestResults_singleTestEventWithTargetCompleted_resultsIncludeTargetKind()
      throws IOException {
    Label label = Label.create("//java/com/google:unit_tests");
    BuildEventStreamProtos.TestStatus status = BuildEventStreamProtos.TestStatus.FAILED;
    ImmutableList<String> filePaths = ImmutableList.of("/usr/local/tmp/_cache/test_result.xml");
    InputStream events =
        asInputStream(
            targetCompletedEvent(label.toString(), "sh_test rule"),
            testResultEvent(label.toString(), status, filePaths));

    BlazeTestResults results = BuildEventProtocolOutputReader.parseTestResults(events);

    assertThat(results.perTargetResults.keySet()).containsExactly(label);
    assertThat(results.perTargetResults.get(label)).hasSize(1);
    BlazeTestResult result = results.perTargetResults.get(label).iterator().next();
    assertThat(result.getTargetKind()).isEqualTo(RuleTypes.SH_TEST.getKind());
    assertThat(result.getTestStatus()).isEqualTo(TestStatus.FAILED);
    assertThat(getOutputXmlFiles(result))
        .containsExactly(new File("/usr/local/tmp/_cache/test_result.xml"));
  }

  @Test
  public void parseTestResults_multipleTargetKindSources_resultsIncludeCorrectTargetKind()
      throws IOException {
    Label label = Label.create("//java/com/google:unit_tests");
    BuildEventStreamProtos.TestStatus status = BuildEventStreamProtos.TestStatus.FAILED;
    ImmutableList<String> filePaths = ImmutableList.of("/usr/local/tmp/_cache/test_result.xml");
    InputStream events =
        asInputStream(
            targetConfiguredEvent(label.toString(), "sh_test rule"),
            targetCompletedEvent(label.toString(), "sh_test rule"),
            testResultEvent(label.toString(), status, filePaths));

    BlazeTestResults results = BuildEventProtocolOutputReader.parseTestResults(events);

    assertThat(results.perTargetResults.keySet()).containsExactly(label);
    assertThat(results.perTargetResults.get(label)).hasSize(1);
    BlazeTestResult result = results.perTargetResults.get(label).iterator().next();
    assertThat(result.getTargetKind()).isEqualTo(RuleTypes.SH_TEST.getKind());
    assertThat(result.getTestStatus()).isEqualTo(TestStatus.FAILED);
    assertThat(getOutputXmlFiles(result))
        .containsExactly(new File("/usr/local/tmp/_cache/test_result.xml"));
  }

  @Test
  public void parseTestResults_singleEvent_ignoresNonXmlOutputFiles() throws IOException {
    Label label = Label.create("//java/com/google:unit_tests");
    BuildEventStreamProtos.TestStatus status = BuildEventStreamProtos.TestStatus.FAILED;
    ImmutableList<String> filePaths =
        ImmutableList.of(
            "/usr/local/tmp/_cache/test_result.xml",
            "/usr/local/tmp/_cache/test_result.log",
            "/usr/local/tmp/other_output_file");
    BuildEvent.Builder event = testResultEvent(label.toString(), status, filePaths);

    BlazeTestResults results =
        BuildEventProtocolOutputReader.parseTestResults(asInputStream(event));

    BlazeTestResult result = results.perTargetResults.get(label).iterator().next();
    assertThat(getOutputXmlFiles(result))
        .containsExactly(new File("/usr/local/tmp/_cache/test_result.xml"));
  }

  @Test
  public void parseTestResults_singleTargetWithMultipleEvents_returnsTestResults()
      throws IOException {
    Label label = Label.create("//java/com/google:unit_tests");
    BuildEventStreamProtos.TestStatus status = BuildEventStreamProtos.TestStatus.PASSED;
    BuildEvent.Builder shard1 =
        testResultEvent(
            label.toString(), status, ImmutableList.of("/usr/local/tmp/_cache/shard1_of_2.xml"));
    BuildEvent.Builder shard2 =
        testResultEvent(
            label.toString(), status, ImmutableList.of("/usr/local/tmp/_cache/shard2_of_2.xml"));

    BlazeTestResults results =
        BuildEventProtocolOutputReader.parseTestResults(asInputStream(shard1, shard2));

    assertThat(results.perTargetResults).hasSize(2);
    Collection<BlazeTestResult> targetResults = results.perTargetResults.get(label);
    assertThat(targetResults)
        .containsExactly(
            BlazeTestResult.create(
                label,
                null,
                TestStatus.PASSED,
                ImmutableSet.of(
                    new LocalFileOutputArtifact(
                        new File("/usr/local/tmp/_cache/shard1_of_2.xml")))),
            BlazeTestResult.create(
                label,
                null,
                TestStatus.PASSED,
                ImmutableSet.of(
                    new LocalFileOutputArtifact(
                        new File("/usr/local/tmp/_cache/shard2_of_2.xml")))));
  }

  @Test
  public void parseTestResults_multipleEvents_returnsAllResults() throws IOException {
    BuildEvent.Builder test1 =
        testResultEvent(
            "//java/com/google:Test1",
            BuildEventStreamProtos.TestStatus.PASSED,
            ImmutableList.of("/usr/local/tmp/_cache/test_result.xml"));
    BuildEvent.Builder test2 =
        testResultEvent(
            "//java/com/google:Test2",
            BuildEventStreamProtos.TestStatus.INCOMPLETE,
            ImmutableList.of("/usr/local/tmp/_cache/second_result.xml"));

    BlazeTestResults results =
        BuildEventProtocolOutputReader.parseTestResults(asInputStream(test1, test2));

    assertThat(results.perTargetResults).hasSize(2);
    assertThat(results.perTargetResults.get(Label.create("//java/com/google:Test1"))).hasSize(1);
    assertThat(results.perTargetResults.get(Label.create("//java/com/google:Test2"))).hasSize(1);
    BlazeTestResult result1 =
        results.perTargetResults.get(Label.create("//java/com/google:Test1")).iterator().next();
    assertThat(result1.getTestStatus()).isEqualTo(TestStatus.PASSED);
    assertThat(getOutputXmlFiles(result1))
        .containsExactly(new File("/usr/local/tmp/_cache/test_result.xml"));
    BlazeTestResult result2 =
        results.perTargetResults.get(Label.create("//java/com/google:Test2")).iterator().next();
    assertThat(result2.getTestStatus()).isEqualTo(TestStatus.INCOMPLETE);
    assertThat(getOutputXmlFiles(result2))
        .containsExactly(new File("/usr/local/tmp/_cache/second_result.xml"));
  }

  private static ImmutableList<File> getOutputXmlFiles(BlazeTestResult result) {
    return LocalFileOutputArtifact.getLocalOutputFiles(result.getOutputXmlFiles());
  }

  private static InputStream asInputStream(BuildEvent.Builder... events) throws IOException {
    return asInputStream(Arrays.asList(events));
  }

  private static InputStream asInputStream(Iterable<BuildEvent.Builder> events) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    for (BuildEvent.Builder event : events) {
      event.build().writeDelimitedTo(output);
    }
    return new ByteArrayInputStream(output.toByteArray());
  }

  private static BuildEvent.Builder targetConfiguredEvent(String label, String targetKind) {
    return BuildEvent.newBuilder()
        .setId(
            BuildEventId.newBuilder()
                .setTargetConfigured(TargetConfiguredId.newBuilder().setLabel(label)))
        .setConfigured(TargetConfigured.newBuilder().setTargetKind(targetKind));
  }

  private BuildEvent.Builder targetCompletedEvent(String label, String targetKind) {
    return BuildEvent.newBuilder()
        .setId(
            BuildEventId.newBuilder()
                .setTargetCompleted(TargetCompletedId.newBuilder().setLabel(label)))
        .setCompleted(TargetComplete.newBuilder().setTargetKind(targetKind));
  }

  private BuildEvent.Builder testResultEvent(
      String label, BuildEventStreamProtos.TestStatus status, List<String> filePaths) {
    return BuildEvent.newBuilder()
        .setId(BuildEventId.newBuilder().setTestResult(TestResultId.newBuilder().setLabel(label)))
        .setTestResult(
            TestResult.newBuilder()
                .setStatus(status)
                .addAllTestActionOutput(
                    filePaths.stream().map(this::toEventFile).collect(toImmutableList())));
  }

  private NamedSetOfFiles setOfFiles(List<String> filePaths) {
    return NamedSetOfFiles.newBuilder()
        .addAllFiles(filePaths.stream().map(this::toEventFile).collect(toImmutableList()))
        .build();
  }

  private BuildEventStreamProtos.File toEventFile(String filePath) {
    return BuildEventStreamProtos.File.newBuilder().setUri(fileUri(filePath)).build();
  }

  private String fileUri(String filePath) {
    return useOldFormatFileUri
        ? LocalFileSystem.PROTOCOL_PREFIX + filePath
        : new File(filePath).toURI().toString();
  }
}

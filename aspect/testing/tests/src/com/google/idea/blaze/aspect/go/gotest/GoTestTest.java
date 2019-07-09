package com.google.devtools.intellij.blaze.plugin.aspect.go.gotest;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.blaze.plugin.aspect.BlazeIntellijAspectTest;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests go_test */
@RunWith(JUnit4.class)
public class GoTestTest extends BlazeIntellijAspectTest {
  @Test
  public void testGoTest() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":simple_test_fixture");
    TargetIdeInfo testTarget = findTarget(testFixture, ":simple_test");
    assertThat(testTarget.getKindString()).isEqualTo("go_test");
    assertThat(testTarget.hasGoIdeInfo()).isTrue();
    assertThat(testTarget.hasPyIdeInfo()).isFalse();
    assertThat(testTarget.hasJavaIdeInfo()).isFalse();
    assertThat(testTarget.hasCIdeInfo()).isFalse();
    assertThat(testTarget.hasAndroidIdeInfo()).isFalse();

    assertThat(relativePathsForArtifacts(testTarget.getGoIdeInfo().getSourcesList()))
        .containsExactly(testRelative("simple_test.go"));

    assertThat(testTarget.getGoIdeInfo().getImportPath()).isEmpty();
    assertThat(testTarget.getGoIdeInfo().getLibraryLabel())
        .isEqualTo("//javatests/com/google/devtools/intellij/blaze/plugin/aspect/go/gotest:simple");
    assertThat(testTarget.getGoIdeInfo().getLibraryLabelsList())
        .containsExactly(
            "//javatests/com/google/devtools/intellij/blaze/plugin/aspect/go/gotest:simple");

    TargetIdeInfo libraryTarget = findTarget(testFixture, ":simple");
    assertThat(libraryTarget.getKindString()).isEqualTo("go_library");
    assertThat(libraryTarget.hasGoIdeInfo()).isTrue();
    assertThat(libraryTarget.hasPyIdeInfo()).isFalse();
    assertThat(libraryTarget.hasJavaIdeInfo()).isFalse();
    assertThat(libraryTarget.hasCIdeInfo()).isFalse();
    assertThat(libraryTarget.hasAndroidIdeInfo()).isFalse();

    assertThat(relativePathsForArtifacts(libraryTarget.getGoIdeInfo().getSourcesList()))
        .containsExactly(testRelative("simple.go"));
    assertThat(libraryTarget.getGoIdeInfo().getImportPath()).isEmpty();
    assertThat(libraryTarget.getGoIdeInfo().getLibraryLabel()).isEmpty();
    assertThat(libraryTarget.getGoIdeInfo().getLibraryLabelsList()).isEmpty();

    TargetIdeInfo noLibraryTestTarget = findTarget(testFixture, ":simple_no_library_test");
    assertThat(noLibraryTestTarget.getKindString()).isEqualTo("go_test");
    assertThat(noLibraryTestTarget.hasGoIdeInfo()).isTrue();
    assertThat(noLibraryTestTarget.hasPyIdeInfo()).isFalse();
    assertThat(noLibraryTestTarget.hasJavaIdeInfo()).isFalse();
    assertThat(noLibraryTestTarget.hasCIdeInfo()).isFalse();
    assertThat(noLibraryTestTarget.hasAndroidIdeInfo()).isFalse();

    assertThat(relativePathsForArtifacts(noLibraryTestTarget.getGoIdeInfo().getSourcesList()))
        .containsExactly(testRelative("simple_no_library_test.go"));
    assertThat(noLibraryTestTarget.getGoIdeInfo().getLibraryLabel()).isEmpty();
    assertThat(noLibraryTestTarget.getGoIdeInfo().getLibraryLabelsList()).isEmpty();

    assertThat(findTarget(testFixture, ":simple_no_ide")).isNull();

    TargetIdeInfo noIdeTestTarget = findTarget(testFixture, ":simple_no_ide_test");
    assertThat(noIdeTestTarget.getKindString()).isEqualTo("go_test");
    assertThat(noIdeTestTarget.hasGoIdeInfo()).isTrue();
    assertThat(noIdeTestTarget.hasPyIdeInfo()).isFalse();
    assertThat(noIdeTestTarget.hasJavaIdeInfo()).isFalse();
    assertThat(noIdeTestTarget.hasCIdeInfo()).isFalse();
    assertThat(noIdeTestTarget.hasAndroidIdeInfo()).isFalse();

    assertThat(relativePathsForArtifacts(noIdeTestTarget.getGoIdeInfo().getSourcesList()))
        .containsExactly(testRelative("simple_no_ide_test.go"));
    assertThat(noIdeTestTarget.getGoIdeInfo().getLibraryLabel())
        .isEqualTo(
            "//javatests/com/google/devtools/intellij/blaze/plugin/aspect/go/gotest:simple_no_ide");
    assertThat(noIdeTestTarget.getGoIdeInfo().getLibraryLabelsList())
        .containsExactly(
            "//javatests/com/google/devtools/intellij/blaze/plugin/aspect/go/gotest:simple_no_ide");

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-go"))
        .containsExactly(
            testRelative(intellijInfoFileName("simple")),
            testRelative(intellijInfoFileName("simple_test")),
            testRelative(intellijInfoFileName("simple_no_library_test")),
            testRelative(intellijInfoFileName("simple_no_ide_test")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-go"))
        .containsExactly(
            testRelative("simple.go"),
            testRelative("simple_test.go"),
            testRelative("simple_no_library_test.go"),
            testRelative("simple_no_ide_test.go"),
            testRelative("simple.a"),
            testRelative("simple.x"),
            testRelative("simple_test_testlib.a"),
            testRelative("simple_test_testlib.x"),
            testRelative("simple_no_library_test.a"),
            testRelative("simple_no_library_test.x"),
            testRelative("simple_no_ide_test_testlib.a"),
            testRelative("simple_no_ide_test_testlib.x"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-go"))
        .containsExactly(
            testRelative("simple.go"),
            testRelative("simple_test.go"),
            testRelative("simple_no_library_test.go"),
            testRelative("simple_no_ide_test.go"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic")).isEmpty();
  }
}

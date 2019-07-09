package com.google.devtools.intellij.blaze.plugin.aspect.go.goappenginelibrary;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.blaze.plugin.aspect.BlazeIntellijAspectTest;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests go_appengine_library */
@RunWith(JUnit4.class)
public class GoAppengineLibraryTest extends BlazeIntellijAspectTest {
  @Test
  public void testGoAppengineLibrary() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":simple_fixture");
    TargetIdeInfo target = findTarget(testFixture, ":simple");
    assertThat(target.getKindString()).isEqualTo("go_appengine_library");
    assertThat(target.hasGoIdeInfo()).isTrue();
    assertThat(target.hasPyIdeInfo()).isFalse();
    assertThat(target.hasJavaIdeInfo()).isFalse();
    assertThat(target.hasCIdeInfo()).isFalse();
    assertThat(target.hasAndroidIdeInfo()).isFalse();

    assertThat(relativePathsForArtifacts(target.getGoIdeInfo().getSourcesList()))
        .containsExactly(testRelative("simple.go"));
    assertThat(target.getGoIdeInfo().getImportPath()).isEmpty();
    // assertThat(target.getGoIdeInfo().getLibraryLabel()).isEmpty();

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-go"))
        .containsExactly(testRelative(intellijInfoFileName("simple")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-go"))
        .contains(testRelative("simple.a"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-go")).isEmpty();
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic")).isEmpty();
  }
}

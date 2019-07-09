package com.google.devtools.intellij.blaze.plugin.aspect.go.gowrapcc;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.blaze.plugin.aspect.BlazeIntellijAspectTest;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests go_wrap_cc */
@RunWith(JUnit4.class)
public class GoWrapCcTest extends BlazeIntellijAspectTest {
  @Test
  public void testGoWrapCc() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":simple_fixture");
    TargetIdeInfo target = findTarget(testFixture, ":simple");
    assertThat(target.getKindString()).isEqualTo("go_wrap_cc");
    assertThat(target.hasGoIdeInfo()).isTrue();
    assertThat(target.hasPyIdeInfo()).isFalse();
    assertThat(target.hasJavaIdeInfo()).isFalse();
    assertThat(target.hasCIdeInfo()).isFalse();
    assertThat(target.hasAndroidIdeInfo()).isFalse();

    assertThat(target.getGoIdeInfo().getImportPath()).isEmpty();

    assertThat(relativePathsForArtifacts(target.getGoIdeInfo().getSourcesList()))
        .containsExactly(testRelative(mangledGoOutput("simple")));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-go"))
        .containsExactly(testRelative(intellijInfoFileName("simple")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-go"))
        .containsExactly(testRelative(mangledGoOutput("simple")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-go"))
        .containsExactly(testRelative(mangledGoOutput("simple")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic")).isEmpty();
  }

  private String mangledGoOutput(String target) {
    String packagePath = "javatests_com_google_devtools_intellij_blaze_plugin_aspect_go_gowrapcc";
    return String.format("%s_%s.go", packagePath, target);
  }
}

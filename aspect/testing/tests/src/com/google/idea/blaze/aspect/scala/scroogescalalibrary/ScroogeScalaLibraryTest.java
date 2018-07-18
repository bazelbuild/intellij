package com.google.idea.blaze.aspect.scala.scroogescalalibrary;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import com.google.idea.blaze.aspect.IntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class ScroogeScalaLibraryTest extends BazelIntellijAspectTest {
    @Test
    public void testScroogeScalaLibrary() throws Exception {
        IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture testFixture = loadTestFixture(":simple_fixture");
        IntellijIdeInfo.TargetIdeInfo target = findTarget(testFixture, ":simple");
        assertThat(target.getKindString()).isEqualTo("scrooge_scala_library");
        assertThat(target.hasJavaIdeInfo()).isTrue();
        assertThat(target.hasCIdeInfo()).isFalse();
        assertThat(target.hasAndroidIdeInfo()).isFalse();
        assertThat(target.hasPyIdeInfo()).isFalse();

        assertThat(relativePathsForArtifacts(target.getJavaIdeInfo().getSourcesList()))
                .isEmpty();
        assertThat(
                target
                        .getJavaIdeInfo()
                        .getJarsList()
                        .stream()
                        .map(IntellijAspectTest::libraryArtifactToString)
                        .collect(Collectors.toList()))
                .containsExactly(jarString(testRelative("thrift/thrift_scrooge.jar"), null, null));

        assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
                .containsExactly(testRelative("thrift/thrift_scrooge.jar"));

        assertThat(getOutputGroupFiles(testFixture, "intellij-info-java"))
                .containsExactly(testRelative("simple.intellij-info.txt"));
        assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java"))
                .containsExactly(testRelative("thrift/thrift_scrooge.jar"));
        assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic"))
                .containsExactly(testRelative("thrift/thrift.intellij-info.txt"));

        assertThat(target.getJavaIdeInfo().getMainClass()).isEmpty();
    }
}

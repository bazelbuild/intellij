package com.google.devtools.intellij.blaze.plugin.aspect.go.goprotolibrary;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.blaze.plugin.aspect.BlazeIntellijAspectTest;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.Dependency;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.Dependency.DependencyType;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.GoIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests go_proto_library */
@RunWith(JUnit4.class)
public class GoProtoLibraryTest extends BlazeIntellijAspectTest {
  @Test
  public void testGoLibrary() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":lib_fixture");

    TargetIdeInfo target = findTarget(testFixture, ":lib");
    assertThat(target.getKindString()).isEqualTo("go_library");
    assertThat(target.hasGoIdeInfo()).isTrue();

    TargetIdeInfo barGoProto = findTarget(testFixture, ":bar_go_proto");
    TargetIdeInfo bazGoProto = findTarget(testFixture, ":baz_go_proto");
    // No duplicates with proto_library.
    assertThat(barGoProto.hasGoIdeInfo()).isFalse();
    assertThat(bazGoProto.hasGoIdeInfo()).isFalse();

    // We shouldn't have reached either of the underlying base proto_library targets
    assertThat(findTarget(testFixture, ":foo_proto")).isNull();
    assertThat(findTarget(testFixture, ":bar_proto")).isNull();
    assertThat(findTarget(testFixture, ":baz")).isNull();

    TargetIdeInfo fooProto = findAspectTarget(testFixture, ":foo_proto", "GoProtoAspect");
    TargetIdeInfo barProto = findAspectTarget(testFixture, ":bar_proto", "GoProtoAspect");
    TargetIdeInfo bazProto = findAspectTarget(testFixture, ":baz", "GoProtoAspect");
    assertThat(fooProto).isNotNull();
    assertThat(barProto).isNotNull();
    assertThat(bazProto).isNotNull();

    // gpl -> (proto + gpl aspect)
    assertThat(barGoProto.getDepsList())
        .containsExactly(
            Dependency.newBuilder()
                .setDependencyType(DependencyType.COMPILE_TIME)
                .setTarget(barProto.getKey())
                .build());

    assertThat(barProto.getDepsList())
        .contains(
            Dependency.newBuilder()
                .setDependencyType(DependencyType.COMPILE_TIME)
                .setTarget(fooProto.getKey())
                .build());

    assertThat(fooProto.hasGoIdeInfo()).isTrue();
    GoIdeInfo fooGoIdeInfo = fooProto.getGoIdeInfo();
    assertThat(fooGoIdeInfo.getImportPath()).isEmpty();
    assertThat(relativePathsForArtifacts(fooGoIdeInfo.getSourcesList()))
        .containsExactly(testRelative("foo.pb.go"));

    assertThat(barProto.hasGoIdeInfo()).isTrue();
    GoIdeInfo barGoIdeInfo = barProto.getGoIdeInfo();
    assertThat(barGoIdeInfo.getImportPath()).isEmpty();
    assertThat(relativePathsForArtifacts(barGoIdeInfo.getSourcesList()))
        .containsExactly(testRelative("bar.pb.go"));
    // assertThat(barGoIdeInfo.getLibraryLabel()).isEmpty();

    assertThat(bazProto.hasGoIdeInfo()).isTrue();
    GoIdeInfo bazGoIdeInfo = bazProto.getGoIdeInfo();
    assertThat(bazGoIdeInfo.getImportPath()).isEmpty();
    assertThat(relativePathsForArtifacts(bazGoIdeInfo.getSourcesList()))
        .containsExactly(testRelative("baz.pb.go"));
    // assertThat(bazGoIdeInfo.getLibraryLabel()).isEmpty();

    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-go"))
        .containsExactly(
            testRelative("foo.pb.go"), testRelative("bar.pb.go"), testRelative("baz.pb.go"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-go"))
        .containsAllOf(
            testRelative("foo.pb.go"), testRelative("bar.pb.go"), testRelative("baz.pb.go"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic"))
        .containsExactly(
            testRelative(intellijInfoFileName("bar_go_proto")),
            testRelative(intellijInfoFileName("baz_go_proto")));
  }
}

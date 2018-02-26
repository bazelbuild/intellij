package com.google.idea.blaze.aspect.kotlin.kotlinlibrary;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class KotlinLibraryTest extends BazelIntellijAspectTest {
  @Test
  public void testKotlinLibrary() throws IOException {
    IntellijAspectTestFixture testFixture = loadTestFixture(":simple_fixture");

    TargetIdeInfo target = findTarget(testFixture, ":simple");
    assertThat(target.getKindString()).isEqualTo("kt_jvm_library");
    assertThat(target.hasJavaIdeInfo()).isTrue();

    TargetIdeInfo ideInfo =
        testFixture
            .getTargetsList()
            .stream()
            .filter(
                t ->
                    t.getKey()
                        .getLabel()
                        .equals("@io_bazel_rules_kotlin//kotlin:kt_toolchain_ide_info"))
            .findFirst()
            .orElse(null);

    assertThat(ideInfo).isNotNull();
    assertThat(ideInfo.getKindString()).isEqualTo("kt_toolchain_ide_info");
    assertThat(ideInfo.hasKtToolchainIdeInfo()).isTrue();
    assertThat(ideInfo.getKtToolchainIdeInfo().getLocation()).isNotNull();

    // TODO remove this block
    assertThat(ideInfo.getJavaIdeInfo()).isNotNull();
    assertThat(ideInfo.getJavaIdeInfo().getSourcesList()).hasSize(1);
  }
}

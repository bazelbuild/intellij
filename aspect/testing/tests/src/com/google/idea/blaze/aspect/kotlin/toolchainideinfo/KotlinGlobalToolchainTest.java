package com.google.idea.blaze.aspect.kotlin.toolchainideinfo;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

@RunWith(JUnit4.class)
public class KotlinGlobalToolchainTest extends BazelIntellijAspectTest {
  @Test
  public void testGlobalToolchainInfo() throws IOException {
    IntellijAspectTestFixture testFixture = loadTestFixture(":global_toolchain_fixture");
    IntellijIdeInfo.TargetIdeInfo ideInfo =
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
    assertThat(ideInfo.getKtToolchainIdeInfo().getJsonInfoFile()).isNotNull();

    File toolchainFile = Paths.get("external", "io_bazel_rules_kotlin", "kotlin", "kt_toolchain_ide_info.json").toFile();
    assertWithMessage("the workspace should expose a toolchain info file").that(toolchainFile.exists()).isTrue();
  }
}

package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;
import static com.google.idea.blaze.clwb.base.Utils.setIncludesCacheEnabled;

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.clwb.base.AllowedVfsRoot;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.testing.headless.BazelVersionRule;
import com.google.idea.testing.headless.ProjectViewBuilder;
import com.intellij.util.system.OS;
import java.util.ArrayList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProtobufTest extends ClwbHeadlessTestCase {

  // protobuf requires bazel 7+
  @Rule
  public final BazelVersionRule bazelRule = new BazelVersionRule(7, 0);

  // on windows clang-cl is required to compile protobuf and therefore also bazel 8+
  @Rule
  public final BazelVersionRule bazelWindowsRule = new BazelVersionRule(OS.Windows, 8, 0);

  @Test
  public void testClwb() {
    setIncludesCacheEnabled(false);

    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkProto();
  }

  @Override
  protected ProjectViewBuilder projectViewText(BazelVersion version) {
    final var builder = super.projectViewText(version);

    if (OS.CURRENT.equals(OS.Windows)) {
      builder.addBuildFlag("--extra_toolchains=@local_config_cc//:cc-toolchain-x64_windows-clang-cl");
      builder.addBuildFlag("--extra_execution_platforms=//:x64_windows-clang-cl");
    }

    return builder;
  }

  @Override
  protected void addAllowedVfsRoots(ArrayList<AllowedVfsRoot> roots) {
    super.addAllowedVfsRoots(roots);
    roots.add(AllowedVfsRoot.bazelBinRecursive(myBazelInfo, "proto"));
  }

  private void checkProto() {
    final var compilerSettings = findFileCompilerSettings("main/main.cc");

    final var headersSearchRoots = compilerSettings.getHeadersSearchRoots().getAllRoots();
    assertThat(headersSearchRoots).isNotEmpty();

    assertContainsHeader("proto/addressbook.pb.h", compilerSettings);
  }
}

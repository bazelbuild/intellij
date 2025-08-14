package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;
import static com.google.idea.blaze.clwb.base.Assertions.assertCachedHeader;
import static com.google.idea.blaze.clwb.base.TestUtils.setIncludesCacheEnabled;

import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.testing.headless.BazelVersionRule;
import com.intellij.util.system.OS;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProtobufCacheTest extends ClwbHeadlessTestCase {

  // protobuf requires bazel 7+
  @Rule
  public final BazelVersionRule bazelRule = new BazelVersionRule(7, 0);

  // on windows clang-cl is required to compile protobuf and therefore also bazel 8+
  @Rule
  public final BazelVersionRule bazelWindowsRule = new BazelVersionRule(OS.Windows, 8, 0);

  @Test
  public void testClwb() {
    setIncludesCacheEnabled(true);

    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoIssues();

    checkProto();
  }

  private void checkProto() {
    final var compilerSettings = findFileCompilerSettings("main/main.cc");

    final var headersSearchRoots = compilerSettings.getHeadersSearchRoots().getAllRoots();
    assertThat(headersSearchRoots).isNotEmpty();

    assertContainsHeader("proto/addressbook.pb.h", compilerSettings);
    assertCachedHeader("proto/addressbook.pb.h", compilerSettings, myProject);
  }
}

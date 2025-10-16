package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertCachedHeader;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;
import static com.google.idea.blaze.clwb.base.Assertions.assertWorkspaceHeader;
import static com.google.idea.blaze.clwb.base.Utils.resolveHeader;
import static com.google.idea.blaze.clwb.base.Utils.setIncludesCacheEnabled;

import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.testing.headless.BazelVersionRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VirtualIncludesCacheTest extends ClwbHeadlessTestCase {

  // use_repo_rule requires bazel 7+
  @Rule
  public final BazelVersionRule bazelRule = new BazelVersionRule(7, 0);

  @Test
  public void testClwb() {
    setIncludesCacheEnabled(true);

    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkIncludes();
    checkImplDeps();
    checkCoptIncludes();
  }

  private void checkIncludes() {
    final var compilerSettings = findFileCompilerSettings("main/main.cc");

    assertContainsHeader("strip_absolut/strip_absolut.h", compilerSettings);
    assertCachedHeader("strip_absolut/strip_absolut.h", compilerSettings, myProject);

    assertContainsHeader("strip_absolut/generated.h", compilerSettings);
    assertCachedHeader("strip_absolut/generated.h", compilerSettings, myProject);

    assertContainsHeader("strip_relative.h", compilerSettings);
    assertCachedHeader("strip_relative.h", compilerSettings, myProject);

    assertContainsHeader("raw_default.h", compilerSettings);
    assertWorkspaceHeader("raw_default.h", compilerSettings, myProject);

    assertContainsHeader("raw_system.h", compilerSettings);
    assertWorkspaceHeader("raw_system.h", compilerSettings, myProject);

    assertContainsHeader("raw_quote.h", compilerSettings);
    assertWorkspaceHeader("raw_quote.h", compilerSettings, myProject);

  }

  private void checkCoptIncludes() {
    final var compilerSettings = findFileCompilerSettings("main/raw.cc");

    assertContainsHeader("raw_default.h", compilerSettings);
    assertWorkspaceHeader("raw_default.h", compilerSettings, myProject);

    assertContainsHeader("raw_system.h", compilerSettings);
    assertWorkspaceHeader("raw_system.h", compilerSettings, myProject);

    assertContainsHeader("raw_quote.h", compilerSettings);
    assertWorkspaceHeader("raw_quote.h", compilerSettings, myProject);

  }

  private void checkImplDeps() {
    final var compilerSettings = findFileCompilerSettings("lib/impl_deps/impl.cc");

    final var headersSearchRoots = compilerSettings.getHeadersSearchRoots().getAllRoots();
    assertThat(headersSearchRoots).isNotEmpty();

    assertContainsHeader("strip_relative.h", compilerSettings);
    assertCachedHeader("strip_relative.h", compilerSettings, myProject);
  }
}

package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;
import static com.google.idea.blaze.clwb.base.Assertions.assertCachedHeader;
import static com.google.idea.blaze.clwb.base.Assertions.assertWorkspaceHeader;
import static com.google.idea.blaze.clwb.base.TestUtils.setIncludesCacheEnabled;

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.testing.headless.ProjectViewBuilder;
import com.intellij.util.system.OS;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VirtualIncludesCacheTest extends ClwbHeadlessTestCase {

  @Test
  public void testClwb() {
    setIncludesCacheEnabled(true);

    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoIssues();

    checkIncludes();
    checkImplDeps();
  }

  @Override
  protected ProjectViewBuilder projectViewText(BazelVersion version) {
    final var builder = super.projectViewText(version);

    if (OS.CURRENT == OS.Windows) {
      // TODO: separate protobuf tests, since protobuf will be dropping support for MSVC + Bazel in 34.0
      builder.addBuildFlag("--define=protobuf_allow_msvc=true");
    }

    return builder;
  }

  private void checkIncludes() {
    final var compilerSettings = findFileCompilerSettings("main/virtual_includes.cc");

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

    assertContainsHeader("lib/transitive/generated.h", compilerSettings);
    assertCachedHeader("lib/transitive/generated.h", compilerSettings, myProject);

  }

  private void checkImplDeps() {
    final var compilerSettings = findFileCompilerSettings("lib/impl_deps/impl.cc");

    final var headersSearchRoots = compilerSettings.getHeadersSearchRoots().getAllRoots();
    assertThat(headersSearchRoots).isNotEmpty();

    assertContainsHeader("strip_relative.h", compilerSettings);
    assertCachedHeader("strip_relative.h", compilerSettings, myProject);
  }
}

package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;
import static com.google.idea.blaze.clwb.base.Assertions.assertCachedHeader;
import static com.google.idea.blaze.clwb.base.Assertions.assertWorkspaceHeader;

import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.testing.headless.BazelVersionRule;
import com.intellij.openapi.util.registry.Registry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VirtualIncludesCacheTest extends ClwbHeadlessTestCase {

  // protobuf requires bazel 7+
  @Rule
  public final BazelVersionRule bazelRule = new BazelVersionRule(7, 0);

  @Test
  public void testClwb() {
    Registry.get("bazel.cpp.sync.allow.bazel.bin.header.search.path").setValue(false);
    Registry.get("bazel.cc.includes.cache.enabled").setValue(true);

    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkIncludes();
    checkImplDeps();
    checkProto();
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

  private void checkProto() {
    final var compilerSettings = findFileCompilerSettings("main/proto.cc");

    final var headersSearchRoots = compilerSettings.getHeadersSearchRoots().getAllRoots();
    assertThat(headersSearchRoots).isNotEmpty();

    assertContainsHeader("proto/addressbook.pb.h", compilerSettings);
    assertCachedHeader("proto/addressbook.pb.h", compilerSettings, myProject);
  }
}

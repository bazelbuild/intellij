package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;
import static com.google.idea.blaze.clwb.base.TestUtils.setIncludesCacheEnabled;

import com.google.idea.blaze.clwb.base.AllowedVfsRoot;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.blaze.clwb.base.TestUtils;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VirtualIncludesTest extends ClwbHeadlessTestCase {

  @Test
  public void testClwb() {
    setIncludesCacheEnabled(false);

    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoIssues();

    checkIncludes();
    checkImplDeps();
  }

  @Override
  protected void addAllowedVfsRoots(ArrayList<AllowedVfsRoot> roots) {
    super.addAllowedVfsRoots(roots);
    roots.add(AllowedVfsRoot.bazelBinRecursive(myBazelInfo, "lib/strip_absolut/_virtual_includes"));
    roots.add(AllowedVfsRoot.bazelBinRecursive(myBazelInfo, "lib/transitive"));
    roots.add(AllowedVfsRoot.bazelBinRecursive(myBazelInfo, "external/+_repo_rules+sqlite"));
  }

  private void checkIncludes() {
    final var compilerSettings = findFileCompilerSettings("main/virtual_includes.cc");

    assertContainsHeader("strip_absolut/strip_absolut.h", compilerSettings);
    assertContainsHeader("strip_absolut/generated.h", compilerSettings);
    assertContainsHeader("strip_relative.h", compilerSettings);
    assertContainsHeader("raw_default.h", compilerSettings);
    assertContainsHeader("raw_system.h", compilerSettings);
    assertContainsHeader("raw_quote.h", compilerSettings);
    assertContainsHeader("lib/transitive/generated.h", compilerSettings);
    assertContainsHeader("sqlite3.h", compilerSettings);

    assertThat(findProjectFile("lib/strip_absolut/strip_absolut.h"))
        .isEqualTo(TestUtils.resolveHeader("strip_absolut/strip_absolut.h", compilerSettings));

    assertThat(findProjectFile("lib/strip_relative/include/strip_relative.h"))
        .isEqualTo(TestUtils.resolveHeader("strip_relative.h", compilerSettings));

    assertThat(findProjectFile("lib/impl_deps/impl.h"))
        .isEqualTo(TestUtils.resolveHeader("lib/impl_deps/impl.h", compilerSettings));

    assertThat(findProjectFile("lib/raw_files/default/raw_default.h"))
        .isEqualTo(TestUtils.resolveHeader("raw_default.h", compilerSettings));

    assertThat(findProjectFile("lib/raw_files/system/raw_system.h"))
        .isEqualTo(TestUtils.resolveHeader("raw_system.h", compilerSettings));

    assertThat(findProjectFile("lib/raw_files/quote/raw_quote.h"))
        .isEqualTo(TestUtils.resolveHeader("raw_quote.h", compilerSettings));
  }

  private void checkImplDeps() {
    final var compilerSettings = findFileCompilerSettings("lib/impl_deps/impl.cc");

    final var headersSearchRoots = compilerSettings.getHeadersSearchRoots().getAllRoots();
    assertThat(headersSearchRoots).isNotEmpty();

    assertContainsHeader("strip_relative.h", compilerSettings);
  }
}

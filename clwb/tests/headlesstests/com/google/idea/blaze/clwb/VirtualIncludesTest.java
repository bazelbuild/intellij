package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.clwb.base.AllowedVfsRoot;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.testing.headless.ProjectViewBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.system.OS;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import java.util.ArrayList;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VirtualIncludesTest extends ClwbHeadlessTestCase {

  @Test
  public void testClwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkIncludes();
    checkImplDeps();
    checkCoptIncludes();
  }

  @Override
  protected ProjectViewBuilder projectViewText(BazelVersion version) {
    final var builder = super.projectViewText(version);

    // required for com.google.idea.blaze.base.sync.workspace.VirtualIncludesHandler to guess the target key
    // this will eventually be replaced by the some kind of includes cache on our side
    if (OS.CURRENT == OS.Windows) {
      builder.addBuildFlag("--features=-shorten_virtual_includes");
    }

    return builder;
  }

  @Override
  protected void addAllowedVfsRoots(ArrayList<AllowedVfsRoot> roots) {
    super.addAllowedVfsRoots(roots);
    roots.add(AllowedVfsRoot.bazelBinRecursive(myBazelInfo, "lib/strip_absolut/_virtual_includes"));
  }

  private @Nullable VirtualFile findHeader(String fileName, OCCompilerSettings settings) {
    final var roots = settings.getHeadersSearchRoots().getAllRoots();

    for (final var root : roots) {
      final var rootFile = root.getVirtualFile();
      if (rootFile == null) continue;

      final var headerFile = rootFile.findFileByRelativePath(fileName);
      if (headerFile == null) continue;

      return headerFile;
    }

    return null;
  }

  private void checkIncludes() {
    final var compilerSettings = findFileCompilerSettings("main/main.cc");

    assertContainsHeader("strip_absolut/strip_absolut.h", compilerSettings);
    assertContainsHeader("strip_absolut/generated.h", compilerSettings);
    assertContainsHeader("strip_relative.h", compilerSettings);
    assertContainsHeader("raw_default.h", compilerSettings);
    assertContainsHeader("raw_system.h", compilerSettings);
    assertContainsHeader("raw_quote.h", compilerSettings);

    assertThat(findProjectFile("lib/strip_absolut/strip_absolut.h"))
        .isEqualTo(findHeader("strip_absolut/strip_absolut.h", compilerSettings));

    assertThat(findProjectFile("lib/strip_relative/include/strip_relative.h"))
        .isEqualTo(findHeader("strip_relative.h", compilerSettings));

    assertThat(findProjectFile("lib/impl_deps/impl.h"))
        .isEqualTo(findHeader("lib/impl_deps/impl.h", compilerSettings));

    assertThat(findProjectFile("lib/raw_files/default/raw_default.h"))
        .isEqualTo(findHeader("raw_default.h", compilerSettings));

    assertThat(findProjectFile("lib/raw_files/system/raw_system.h"))
        .isEqualTo(findHeader("raw_system.h", compilerSettings));

    assertThat(findProjectFile("lib/raw_files/quote/raw_quote.h"))
        .isEqualTo(findHeader("raw_quote.h", compilerSettings));
  }

  private void checkCoptIncludes() {
    final var compilerSettings = findFileCompilerSettings("main/raw.cc");

    assertContainsHeader("raw_default.h", compilerSettings);
    assertContainsHeader("raw_system.h", compilerSettings);
    assertContainsHeader("raw_quote.h", compilerSettings);

    assertThat(findProjectFile("lib/raw_files/default/raw_default.h"))
        .isEqualTo(findHeader("raw_default.h", compilerSettings));

    assertThat(findProjectFile("lib/raw_files/system/raw_system.h"))
        .isEqualTo(findHeader("raw_system.h", compilerSettings));

    assertThat(findProjectFile("lib/raw_files/quote/raw_quote.h"))
        .isEqualTo(findHeader("raw_quote.h", compilerSettings));
  }

  private void checkImplDeps() {
    final var compilerSettings = findFileCompilerSettings("lib/impl_deps/impl.cc");

    final var headersSearchRoots = compilerSettings.getHeadersSearchRoots().getAllRoots();
    assertThat(headersSearchRoots).isNotEmpty();

    assertContainsHeader("strip_relative.h", compilerSettings);
  }
}

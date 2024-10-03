package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;

import com.google.idea.blaze.clwb.base.ClwbIntegrationTestCase;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VirtualIncludesTest extends ClwbIntegrationTestCase {

  @Test
  public void testClwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkIncludes();
  }

  private @Nullable VirtualFile findHeader(String fileName, List<HeadersSearchRoot> roots) {
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
    final var compilerSettings = findFileCompilerSettings("main/hello-world.cc");

    final var headersSearchRoots = compilerSettings.getHeadersSearchRoots().getAllRoots();
    assertThat(headersSearchRoots).isNotEmpty();

    assertContainsHeader("strip_absolut/strip_absolut.h", headersSearchRoots);
    assertContainsHeader("strip_absolut/generated.h", headersSearchRoots);
    assertContainsHeader("strip_relative.h", headersSearchRoots);

    assertThat(findProjectFile("lib/strip_absolut/strip_absolut.h"))
        .isEqualTo(findHeader("strip_absolut/strip_absolut.h", headersSearchRoots));

    assertThat(findProjectFile("lib/strip_relative/include/strip_relative.h"))
        .isEqualTo(findHeader("strip_relative.h", headersSearchRoots));
  }
}

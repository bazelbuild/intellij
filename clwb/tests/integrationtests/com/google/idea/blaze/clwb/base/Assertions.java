package com.google.idea.blaze.clwb.base;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRootProcessor;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class Assertions {

  public static void assertContainsHeader(String fileName, List<HeadersSearchRoot> roots) {
    assertContainsHeader(fileName, true, roots);
  }

  public static void assertDoesntContainHeader(String fileName, List<HeadersSearchRoot> roots) {
    assertContainsHeader(fileName, false, roots);
  }

  private static void assertContainsHeader(String fileName, boolean shouldContain, List<HeadersSearchRoot> roots) {
    final var found = new Ref<VirtualFile>();
    final var foundIn = new Ref<PsiFileSystemItem>();

    for (final var root : roots) {
      root.processChildren(new HeadersSearchRootProcessor() {
        @Override
        public boolean process(@NotNull VirtualFile file) {
          if (file.isDirectory() || !FileUtil.namesEqual(file.getName(), fileName)) {
            return true;
          }

          found.set(file);
          foundIn.set(root);
          return false;
        }
      });

      if (!found.isNull()) {
        break;
      }
    }

    if (shouldContain) {
      assertWithMessage(String.format("%s not found in:\n%s", fileName, StringUtil.join(roots, "\n")))
          .that(found.isNull())
          .isFalse();
    } else {
      assertWithMessage(String.format("%s found in: %s", fileName, foundIn.get()))
          .that(found.isNull())
          .isTrue();
    }
  }
}

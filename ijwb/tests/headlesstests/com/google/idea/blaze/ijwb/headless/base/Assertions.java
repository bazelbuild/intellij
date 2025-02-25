package com.google.idea.blaze.ijwb.headless.base;

import static com.google.common.truth.Truth.assertWithMessage;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;

public class Assertions {

  public static void assertModuleContainsFile(Module module, VirtualFile file) {
    final var roots = ModuleRootManager.getInstance(module).getContentRoots();
    final var found = Arrays.stream(roots).anyMatch((root) -> VfsUtilCore.isAncestor(root, file, false));

    final var message = String.format(
        "%s not found in:\n%s",
        file.getPath(),
        StringUtil.join(roots, VirtualFile::getPath, "\n")
    ) ;

    assertWithMessage(message).that(found).isTrue();
  }
}

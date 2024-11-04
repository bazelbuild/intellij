package com.google.idea.blaze.clwb.base;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.truth.StringSubject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import com.jetbrains.cidr.project.ui.widget.Status;
import com.jetbrains.cidr.project.ui.widget.WidgetStatus;
import com.jetbrains.cidr.project.ui.widget.WidgetStatusProvider;
import java.util.Objects;
import java.util.regex.Pattern;

public class Assertions {

  private final static Pattern defineRx = Pattern.compile("#define ([^ ]+) ?(.*)");

  public static void assertContainsHeader(String fileName, OCCompilerSettings settings) {
    assertContainsHeader(fileName, true, settings);
  }

  public static void assertDoesntContainHeader(String fileName, OCCompilerSettings settings) {
    assertContainsHeader(fileName, false, settings);
  }

  private static void assertContainsHeader(String fileName, boolean shouldContain, OCCompilerSettings settings) {
    final var roots = settings.getHeadersSearchRoots().getAllRoots();
    assertThat(roots).isNotEmpty();

    final var found = new Ref<VirtualFile>();
    final var foundIn = new Ref<HeadersSearchRoot>();

    for (final var root : roots) {
      final var rootFile = root.getVirtualFile();
      if (rootFile == null) continue;

      final var headerFile = rootFile.findFileByRelativePath(fileName);
      if (headerFile == null || !headerFile.exists()) continue;

      found.set(headerFile);
      foundIn.set(root);

      break;
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

  public static StringSubject assertDefine(String symbol, OCCompilerSettings settings) {
    final var defines = settings.getPreprocessorDefines();
    assertThat(defines).isNotEmpty();

    for (final var define : defines) {
      final var matcher = defineRx.matcher(define);
      if (!matcher.find()) {
        continue;
      }

      final var defineSymbol = matcher.group(1);
      if (defineSymbol == null || !defineSymbol.equals(symbol)) {
        continue;
      }

      final var defineValue = matcher.group(2);
      return assertWithMessage(define).that(defineValue);
    }

    return assertWithMessage("symbol is not defined: " + symbol).that((String) null);
  }

  public static void assertProjectStatus(Project project, VirtualFile file, Status expected) {
    final var actual = WidgetStatusProvider.Companion.getEP_NAME().getExtensionList().stream()
        .map((it) -> it.getWidgetStatus(project, file))
        .filter(Objects::nonNull)
        .findFirst()
        .map(WidgetStatus::getStatus)
        .orElse(null);

    assertThat(actual).isEqualTo(expected);
  }
}

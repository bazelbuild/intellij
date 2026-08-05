/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.filter;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.run.filter.GenericFileMessageFilter.CustomOpenFileHyperlinkInfo;
import com.intellij.execution.filters.Filter.Result;
import com.intellij.mock.MockLocalFileSystem;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link GenericFileMessageFilter}. */
@RunWith(JUnit4.class)
public class GenericFileMessageFilterTest extends BlazeTestCase {

  private static final File mockFile = new File("filename");

  /** Every path string the filter asked the {@link FileResolver} chain to resolve. */
  private static final List<String> resolvedPaths = new ArrayList<>();

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    registerExtensionPoint(FileResolver.EP_NAME, FileResolver.class).registerExtension((project, path) -> {
      resolvedPaths.add(path);
      return mockFile;
    });
    applicationServices.register(VirtualFileSystemProvider.class, MockLocalFileSystem::new);
  }

  @After
  public final void doTearDown() {
    resolvedPaths.clear();
  }

  @Test
  public void testCatch2FailureLocation() {
    // the line from https://youtrack.jetbrains.com/issue/CPP-51382
    assertHasMatch("at test/adder/adder_tests.cpp:16", "test/adder/adder_tests.cpp", 16, 1);
  }

  @Test
  public void testIndentedFilePath() {
    assertHasMatch("  at test/adder/adder_tests.cpp:16", "test/adder/adder_tests.cpp", 16, 1);
  }

  @Test
  public void testGoogleTestFailureLocation() {
    assertHasMatch("src/lib/greeting_test.cc:15: Failure", "src/lib/greeting_test.cc", 15, 1);
  }

  @Test
  public void testRelativeFilePathWithColumn() {
    assertHasMatch("relative/file/p-a_th.go:10:50: some other message", "relative/file/p-a_th.go", 10, 50);
  }

  @Test
  public void testFilePathWithoutColumn() {
    assertHasMatch("file/path.go:10: string", "file/path.go", 10, 1);
  }

  /** Regression test: the whole path must be captured, not just its last two segments. */
  @Test
  public void testPathWithOddNumberOfSegments() {
    assertHasMatch("a/b/c.cpp:7", "a/b/c.cpp", 7, 1);
  }

  @Test
  public void testGeneratedFilePath() {
    assertHasMatch("bazel-out/k8-fastbuild/bin/gen/x.h:3:9: error", "bazel-out/k8-fastbuild/bin/gen/x.h", 3, 9);
  }

  @Test
  public void testSurroundingDelimitersAreNotPartOfThePath() {
    assertHasMatch("\"test/adder/x.cpp:16\"", "test/adder/x.cpp", 16, 1);
    assertHasMatch("(test/adder/x.cpp:16)", "test/adder/x.cpp", 16, 1);
  }

  @Test
  public void testExplicitlyRelativeFilePath() {
    assertHasMatch("./test/x.cpp:16", "./test/x.cpp", 16, 1);
  }

  /**
   * Regression test: without the leading look-behind, back-tracking matches an absolute path from
   * its second character, and the resulting relative path resolves against the workspace root --
   * silently opening the wrong file.
   */
  @Test
  public void testIgnoreAbsoluteFilePath() {
    assertNoMatch("/absolute/file/path.go:10:50: error");
    assertNoMatch("C:\\ws\\foo\\bar.cc:12");
  }

  @Test
  public void testIgnoreTokensWithoutAPathSeparator() {
    assertNoMatch("Total:15");
    assertNoMatch("12:34:56");
    assertNoMatch("INFO: Elapsed time: 12:34");
    assertNoMatch("  Duration: 1:23");
  }

  @Test
  public void testIgnoreOtherCatch2OutputLines() {
    assertNoMatch("with expansion:");
    assertNoMatch("  CHECK( theAdder.add(1, 2) == 5 )");
  }

  private void assertHasMatch(String text, String expectedPath, int line, int column) {
    Result result = findMatch(text);
    assertThat(result).isNotNull();

    assertThat(resolvedPaths).containsExactly(expectedPath);
    assertThat(result.getFirstHyperlinkInfo()).isInstanceOf(CustomOpenFileHyperlinkInfo.class);

    final var link = (CustomOpenFileHyperlinkInfo) result.getFirstHyperlinkInfo();
    assertThat(link).isNotNull();
    assertThat(link.vf).isNotNull();
    assertThat(link.line).isEqualTo(line - 1);
    assertThat(link.column).isEqualTo(column - 1);

    // the hyperlink covers the path and the line/column numbers, and nothing else
    final var expectedLink = expectedPath + ":" + line + (column > 1 ? ":" + column : "");
    assertThat(text.substring(result.getHighlightStartOffset(), result.getHighlightEndOffset()))
        .isEqualTo(expectedLink);

    resolvedPaths.clear();
  }

  private void assertNoMatch(String text) {
    assertThat(findMatch(text)).isNull();
    assertThat(resolvedPaths).isEmpty();
  }

  @Nullable
  private Result findMatch(String line) {
    return new GenericFileMessageFilter(project).applyFilter(line, line.length());
  }
}

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
import com.google.idea.blaze.base.run.filter.GenericFileMessageFilter.CustomOpenFileHyperlinkInfo;
import com.intellij.execution.filters.Filter.Result;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link GenericFileMessageFilter}. */
@RunWith(JUnit4.class)
public class GenericFileMessageFilterTest extends BlazeTestCase {

  private static final VirtualFile mockFile = MockVirtualFile.file("filename");
  private static final Map<String, VirtualFile> filePathToVirtualFile = new HashMap<>();

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    registerExtensionPoint(FileResolver.EP_NAME, FileResolver.class)
        .registerExtension((project, path) -> filePathToVirtualFile.get(path));
  }

  @After
  public final void doTearDown() {
    filePathToVirtualFile.clear();
  }

  @Test
  public void testAbsoluteFilePath() {
    filePathToVirtualFile.put("/absolute/file/path.go", mockFile);
    assertHasMatch("/absolute/file/path.go:10:50: error", 10, 50);
  }

  @Test
  public void testRelativeFilePath() {
    filePathToVirtualFile.put("relative/file/p-a_th.go", mockFile);
    assertHasMatch("relative/file/p-a_th.go:10:50: some other message", 10, 50);
  }

  @Test
  public void testIgnoreLinesWithLeadingWhitespace() {
    filePathToVirtualFile.put("/absolute/file/path.go", mockFile);
    assertThat(findMatch(" /absolute/file/path.go:10:50: string")).isNull();
  }

  @Test
  public void testIgnoreLineNumberWithoutColumnNumber() {
    filePathToVirtualFile.put("file/path.go", mockFile);
    assertThat(findMatch("file/path.go:10: string")).isNull();
  }

  private void assertHasMatch(String text, int line, int column) {
    Result result = findMatch(text);
    assertThat(result).isNotNull();
    assertThat(result.getFirstHyperlinkInfo()).isInstanceOf(CustomOpenFileHyperlinkInfo.class);

    CustomOpenFileHyperlinkInfo link = (CustomOpenFileHyperlinkInfo) result.getFirstHyperlinkInfo();
    assertThat(link.vf).isEqualTo(mockFile);
    assertThat(link.line).isEqualTo(line - 1);
    assertThat(link.column).isEqualTo(column - 1);
  }

  @Nullable
  private Result findMatch(String line) {
    return new GenericFileMessageFilter(project).applyFilter(line, line.length());
  }
}

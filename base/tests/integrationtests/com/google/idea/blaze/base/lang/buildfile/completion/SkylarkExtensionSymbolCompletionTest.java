/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.completion;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.intellij.openapi.vfs.VirtualFile;

/** Tests auto-complete of symbols loaded from skylark bzl files. */
public class SkylarkExtensionSymbolCompletionTest extends BuildFileIntegrationTestCase {

  private VirtualFile createAndSetCaret(String filePath, String... fileContents) {
    VirtualFile file = createFile(filePath, fileContents);
    testFixture.configureFromExistingVirtualFile(file);
    return file;
  }

  public void testGlobalVariable() {
    createFile("skylark.bzl", "VAR = []");
    VirtualFile file = createAndSetCaret("BUILD", "load(':skylark.bzl', '<caret>')");

    assertThat(completeIfUnique()).isTrue();
    assertFileContents(file, "load(':skylark.bzl', 'VAR')");
  }

  public void testFunctionStatement() {
    createFile("skylark.bzl", "def fn(param):stmt");
    VirtualFile file = createAndSetCaret("BUILD", "load(':skylark.bzl', '<caret>')");

    assertThat(completeIfUnique()).isTrue();
    assertFileContents(file, "load(':skylark.bzl', 'fn')");
  }

  public void testMultipleOptions() {
    createFile("skylark.bzl", "def fn(param):stmt", "VAR = []");
    VirtualFile file = createAndSetCaret("BUILD", "load(':skylark.bzl', '<caret>')");

    String[] options = getCompletionItemsAsStrings();
    assertThat(options).asList().containsExactly("'fn'", "'VAR'");
  }

  public void testRulesNotIncluded() {
    createFile("skylark.bzl", "java_library(name = 'lib')", "native.java_library(name = 'foo'");

    VirtualFile file = createAndSetCaret("BUILD", "load(':skylark.bzl', '<caret>')");

    assertThat(testFixture.completeBasic()).isEmpty();
  }

  public void testLoadedSymbols() {
    createFile("other.bzl", "def function()");
    createFile("skylark.bzl", "load(':other.bzl', 'function')");
    VirtualFile file = createAndSetCaret("BUILD", "load(':skylark.bzl', '<caret>')");

    assertThat(completeIfUnique()).isTrue();
    assertFileContents(file, "load(':skylark.bzl', 'function')");
  }

  public void testNotLoadedSymbolsAreNotIncluded() {
    createFile("other.bzl", "def function():stmt", "def other_function():stmt");
    createFile("skylark.bzl", "load(':other.bzl', 'function')");
    VirtualFile file = createAndSetCaret("BUILD", "load(':skylark.bzl', '<caret>')");

    assertThat(completeIfUnique()).isTrue();
    assertFileContents(file, "load(':skylark.bzl', 'function')");
  }
}

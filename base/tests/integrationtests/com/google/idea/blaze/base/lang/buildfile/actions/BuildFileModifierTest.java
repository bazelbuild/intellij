/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.actions;

import com.google.idea.blaze.base.buildmodifier.BuildFileModifier;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BuildFileModifier}. */
@RunWith(JUnit4.class)
public class BuildFileModifierTest extends BuildFileIntegrationTestCase {

  @Test
  public void testAddNewTarget() {
    BuildFile buildFile =
        createBuildFile(new WorkspacePath("BUILD"), "java_library(name = 'existing')", "");
    WriteCommandAction.runWriteCommandAction(
        getProject(),
        (Computable<Boolean>)
            () ->
                BuildFileModifier.getInstance()
                    .addRule(
                        getProject(),
                        new BlazeContext(),
                        Label.create("//:new_target"),
                        Kind.JAVA_TEST));
    assertFileContents(
        buildFile,
        "java_library(name = 'existing')",
        "java_test(",
        "    name = \"new_target\"",
        ")");
  }
}

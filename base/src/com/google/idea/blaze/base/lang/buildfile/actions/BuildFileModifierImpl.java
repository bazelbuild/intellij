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
package com.google.idea.blaze.base.lang.buildfile.actions;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.idea.blaze.base.buildmodifier.BuildFileModifier;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.Expression;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.BuildElementGenerator;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Implementation of BuildFileModifier. Modifies the PSI tree directly. */
public class BuildFileModifierImpl implements BuildFileModifier {

  private static final Logger logger = Logger.getInstance(BuildFileModifierImpl.class);

  @Override
  public boolean addRule(Project project, Label newRule, Kind ruleKind) {
    BuildReferenceManager manager = BuildReferenceManager.getInstance(project);
    File file = manager.resolvePackage(newRule.blazePackage());
    if (file == null) {
      return false;
    }
    LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(file));
    BuildFile buildFile = manager.resolveBlazePackage(newRule.blazePackage());
    if (buildFile == null) {
      logger.error("No BUILD file found at location: " + newRule.blazePackage());
      return false;
    }
    buildFile.add(createRule(project, ruleKind, newRule.targetName().toString()));
    return true;
  }

  private static String run(String[] command) throws IOException, InterruptedException {
    return run(command, "");
  }

  private static String run(String[] command, String input) throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder(command);
    Process process = builder.start();
    process.getOutputStream().write(input.getBytes(UTF_8));
    process.getOutputStream().close();
    String result = CharStreams.toString(new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8)));
    String errors = CharStreams.toString(new BufferedReader(new InputStreamReader(process.getErrorStream(), UTF_8)));
    process.waitFor();
    if (errors.length() > 0) {
      String message = Joiner.on(' ').join(command, errors);
      if (result.length() == 0) {
        throw new IOException(message);
      } else {
        logger.warn(message);
      }
    }
    return result;
  }

  private PsiElement createRule(Project project, Kind ruleKind, String ruleName) {
    String command = Joiner.on(" ").join("new", ruleKind.toString(), ruleName);
    String text = ruleKind.toString() + "(name = \"" + ruleName + "\")";
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    try {
      text = run(new String[] { settings.getBuildozerBinaryPath(), command, "-:*" });
    } catch (IOException|InterruptedException ec) {
      try {
        if (!settings.isBuildozerDefault()) {
          text = run(new String[] { settings.getDefaultBuildozerPath(), command, "-:*" });
        } else {
          throw ec;
        }
      } catch (IOException|InterruptedException ed) {
        logger.warn("Unable to invoke buildozer, falling back to string concatenation", ed);
      }
    }
    Expression expr = BuildElementGenerator.getInstance(project).createExpressionFromText(text);
    assert (expr instanceof FuncallExpression);
    return expr;
  }
}

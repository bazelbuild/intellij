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
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.buildmodifier.BuildFileModifier;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.Expression;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.util.BuildElementGenerator;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiElement;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Implementation of BuildFileModifier. Modifies the PSI tree directly. */
public class BuildFileModifierImpl implements BuildFileModifier {

  private static final Logger logger = Logger.getInstance(BuildFileModifierImpl.class);

  @Override
  public boolean addRule(Project project, Label newRule, Kind ruleKind) {
    return addRule(project, newRule, ruleKind, ImmutableMap.of());
  }

  @Override
  public boolean addRule(Project project, Label newRule, Kind ruleKind, ImmutableMap<String, String> arguments) {
    BuildFile buildFile = getBuildFile(project, newRule.blazePackage());
    if (buildFile == null) {
      return false;
    }
    buildFile.add(createRule(project, ruleKind, newRule.targetName().toString(), arguments));
    return true;
  }

  @Override
  public boolean addLoadStatement(Project project, WorkspacePath packagePath, Label label, String... symbols) {
    BuildFile buildFile = getBuildFile(project, packagePath);
    if (buildFile == null) {
      return false;
    }

    LoadStatement statement =
      Arrays.stream(buildFile.findChildrenByClass(LoadStatement.class))
        .filter(s -> s.getImportedPath().equals(label.toString()))
        .findFirst()
      .orElse(null);

    if (statement == null) {
      buildFile.add(createLoadStatement(project, label, Arrays.asList(symbols), true));
    } else {
      List<String> combinedSymbols =
        Stream.of(statement.getVisibleSymbolNames(), symbols)
        .flatMap(Arrays::stream)
        .distinct()
        .collect(Collectors.toList());
      statement.replace(createLoadStatement(project, label, combinedSymbols, false));
    }

    return true;
  }

  public static BuildFile getBuildFile(Project project, WorkspacePath packagePath) {
    BuildReferenceManager manager = BuildReferenceManager.getInstance(project);
    File file = manager.resolvePackage(packagePath);
    if (file == null) {
      return null;
    }

    LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(file));
    BuildFile buildFile = manager.resolveBlazePackage(packagePath);
    if (buildFile == null) {
      logger.error("No BUILD file found at location: " + packagePath);
      return null;
    }

    return buildFile;
  }

  private PsiElement createRule(Project project, Kind ruleKind, String ruleName, ImmutableMap<String, String> args) {
    ImmutableMap allArgs =
      ImmutableMap.builder()
         .putAll(ImmutableMap.of("name", "\"" + ruleName + "\""))
         .putAll(args)
         .build();
    String text =
      Joiner.on(System.lineSeparator())
        .join(ruleKind.toString() + "(",
          buildString(allArgs),
          ")");
    BuildElementGenerator generator = BuildElementGenerator.getInstance(project);
    Expression expr = generator.createExpressionFromText(text);
    assert (expr instanceof FuncallExpression);

    expr.add(generator.createNewline());
    return expr;
  }

  private String buildString(ImmutableMap<String, String> args) {
    return args.entrySet().stream()
      .map(e -> "    " + e.getKey() + "=" + e.getValue() + ",")
      .collect(Collectors.joining(System.lineSeparator()));
  }

  private PsiElement createLoadStatement(Project project, Label label, List<String> symbols, boolean newLine) {
    String symbolsString =
      symbols.stream().collect(Collectors.joining("','", "'", "'"));
    String text =
      String.format("load('%s', %s)", label.toString(), symbolsString);
    BuildElementGenerator generator = BuildElementGenerator.getInstance(project);
    PsiElement expr = generator.createElementFromText(text);
    assert (expr instanceof LoadStatement);

    if (newLine) expr.add(generator.createNewline());

    return expr;
  }
}

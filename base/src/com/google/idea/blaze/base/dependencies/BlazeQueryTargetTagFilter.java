/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.dependencies;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

/**
 * This class is able to filter a list of targets selecting those that are
 * tagged with one or more of a number of tags.
 */

public class BlazeQueryTargetTagFilter implements TargetTagFilter {

  private static final Logger logger =
      Logger.getInstance(BlazeQueryTargetTagFilter.class);

  /**
   * Tags are checked against this {@link Pattern} to ensure they can be used with
   * the Bazel queries run in this logic. See
   * {@link com.google.idea.blaze.base.ideinfo.Tags} for example Tags that would
   * be used inside the Plugin; all of which conform to this {@link Pattern}.
   */
  private static final Pattern PATTERN_TAG = Pattern.compile("^[a-zA-Z0-9_-]+$");

  @Nullable
  @Override
  public List<TargetExpression> doFilterCodeGen(
      Project project,
      BlazeContext context,
      List<TargetExpression> targets,
      Set<String> tags) {

    if (targets.isEmpty() || tags.isEmpty()) {
      return ImmutableList.of();
    }

    return runQuery(project, getQueryString(targets, tags), context);
  }

  @VisibleForTesting
  public static String getQueryString(List<TargetExpression> targets, Set<String> tags) {
    Preconditions.checkArgument(null != targets && !targets.isEmpty(), "the targets must be supplied");
    Preconditions.checkArgument(null != tags && !tags.isEmpty(), "the tags must be supplied");

    for (String tag : tags) {
      if (!PATTERN_TAG.matcher(tag).matches()) {
        throw new IllegalStateException("the tag [" + tag + "] is not able to be used for filtering");
      }
    }

    String targetsExpression = targets.stream().map(Object::toString).collect(Collectors.joining(" + "));
    String matchExpression = String.format("[\\[ ](%s)[,\\]]", String.join("|", ImmutableList.sortedCopyOf(tags)));

    if (SystemInfo.isWindows) {
      return String.format("attr('tags', '%s', %s)", matchExpression, targetsExpression);
    }

    return String.format("attr(\"tags\", \"%s\", %s)", matchExpression, targetsExpression);
  }

  @javax.annotation.Nullable
  private static List<TargetExpression> runQuery(
      Project project,
      String query,
      BlazeContext context) {

    BuildSystem buildSystem = Blaze.getBuildSystemProvider(project).getBuildSystem();

    BlazeCommand.Builder command =
        BlazeCommand.builder(
                buildSystem.getDefaultInvoker(project, context), BlazeCommandName.QUERY, project)
            .addBlazeFlags("--output=label")
            .addBlazeFlags(BlazeFlags.KEEP_GOING)
            .addBlazeFlags(query);

    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);

    try (BuildResultHelper helper = invoker.createBuildResultHelper();
        InputStream queryResultStream = invoker.getCommandRunner()
            .runQuery(project, command, helper, context)) {

      return new BufferedReader(new InputStreamReader(queryResultStream, UTF_8))
          .lines()
          .map(Label::createIfValid)
          .collect(Collectors.toList());

    } catch (IOException | BuildException e) {
      logger.error(e.getMessage(), e);
      return null;
    }
  }

}

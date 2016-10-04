/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run;

import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Arrays;

/**
 * Utility class for creating Blaze configuration names of the form "{build system name} {command
 * name} {target string}", where each component is optional.
 */
public class BlazeConfigurationNameBuilder {
  private String buildSystemName;
  private String commandName;
  private String targetString;

  public BlazeConfigurationNameBuilder() {}

  /**
   * Use the passed {@code configuration} to initialize the build system name, command name, and
   * target string. If the configuration's command name is null, this will default to "command". If
   * the configuration's target is null, target string will also be null.
   */
  public BlazeConfigurationNameBuilder(BlazeCommandRunConfiguration configuration) {
    setBuildSystemName(configuration.getProject());

    String commandName = configuration.getHandler().getCommandName();
    setCommandName((commandName == null) ? "command" : commandName);

    TargetExpression targetExpression = configuration.getTarget();
    if (targetExpression != null) {
      setTargetString(targetExpression);
    }
  }

  /**
   * Sets the build system name to the name of the build system used by {@code project}, e.g.
   * "Blaze" or "Bazel".
   */
  public BlazeConfigurationNameBuilder setBuildSystemName(Project project) {
    buildSystemName = Blaze.buildSystemName(project);
    return this;
  }

  /** Sets the command name to {@code commandName}. */
  public BlazeConfigurationNameBuilder setCommandName(String commandName) {
    this.commandName = commandName;
    return this;
  }

  /** Sets the target string to {@code targetString}. */
  public BlazeConfigurationNameBuilder setTargetString(String targetString) {
    this.targetString = targetString;
    return this;
  }

  /**
   * Sets the target string to a string of the form "{package}:{target}", where 'target' is {@code
   * label}'s target, and the 'package' is the containing package. For example, the {@link Label}
   * "//javatests/com/google/foo/bar/baz:FooTest" will set the target string to "baz:FooTest".
   */
  public BlazeConfigurationNameBuilder setTargetString(Label label) {
    this.targetString =
        String.format("%s:%s", getImmediatePackage(label), label.ruleName().toString());
    return this;
  }

  /**
   * If {@code targetExpression} is a {@link Label}, this is equivalent to {@link
   * #setTargetString(Label)}. Otherwise, the target string is set to the string value of {@code
   * targetExpression}.
   */
  public BlazeConfigurationNameBuilder setTargetString(TargetExpression targetExpression) {
    if (targetExpression instanceof Label) {
      return setTargetString((Label) targetExpression);
    }
    return setTargetString(targetExpression.toString());
  }

  /**
   * Get the portion of a label between the colon and the preceding slash. Example:
   * "//javatests/com/google/foo/bar/baz:FooTest" -> "baz".
   */
  private static String getImmediatePackage(Label label) {
    String labelString = label.toString();
    int colonIndex = labelString.lastIndexOf(':');
    assert colonIndex >= 0;
    int slashIndex = labelString.lastIndexOf('/', colonIndex);
    assert slashIndex >= 0;
    return labelString.substring(slashIndex + 1, colonIndex);
  }

  /**
   * Builds a name of the form "{build system name} {command name} {target string}". Any null
   * components are omitted, and there is always one space inserted between each included component.
   */
  public String build() {
    // Use this instead of String.join to omit null terms.
    return StringUtil.join(Arrays.asList(buildSystemName, commandName, targetString), " ");
  }
}

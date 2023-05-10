/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.fastbuild;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class JavaCommandBuilder implements ModifiableJavaCommand {

  private File javaBinary;
  private File workingDirectory;
  private String mainClass;
  private final List<String> jvmArgs = new ArrayList<>();
  private final List<File> classpaths = new ArrayList<>();
  private final List<String> programArgs = new ArrayList<>();
  private final Map<String, String> systemProperties = new HashMap<>();
  private final Map<String, String> environmentVariables = new HashMap<>();

  @CanIgnoreReturnValue
  JavaCommandBuilder setJavaBinary(File javaBinary) {
    checkNotNull(javaBinary);
    checkArgument(this.javaBinary == null, "javaBinary was previously set");
    this.javaBinary = javaBinary;
    return this;
  }

  @CanIgnoreReturnValue
  JavaCommandBuilder setWorkingDirectory(File workingDirectory) {
    checkNotNull(workingDirectory);
    checkArgument(this.workingDirectory == null, "workingDirectory was previously set");
    this.workingDirectory = workingDirectory;
    return this;
  }

  @Override
  public File getWorkingDirectory() {
    return workingDirectory;
  }

  @CanIgnoreReturnValue
  JavaCommandBuilder setMainClass(String mainClass) {
    checkNotNull(mainClass);
    checkArgument(this.mainClass == null, "mainClass was previously set");
    this.mainClass = mainClass;
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public JavaCommandBuilder addJvmArgument(String jvmArgument) {
    checkNotNull(jvmArgument);
    this.jvmArgs.add(jvmArgument);
    return this;
  }

  ImmutableList<String> getJvmArgs() {
    return ImmutableList.copyOf(jvmArgs);
  }

  @CanIgnoreReturnValue
  @Override
  public JavaCommandBuilder addClasspathElement(File classpath) {
    checkNotNull(classpath);
    this.classpaths.add(classpath);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public JavaCommandBuilder addProgramArgument(String programArgument) {
    checkNotNull(programArgument);
    this.programArgs.add(programArgument);
    return this;
  }

  ImmutableList<String> getProgramArgs() {
    return ImmutableList.copyOf(programArgs);
  }

  @CanIgnoreReturnValue
  @Override
  public JavaCommandBuilder addSystemProperty(String property, String value) {
    checkNotNull(property);
    checkNotNull(value);
    this.systemProperties.put(property, value);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public JavaCommandBuilder addEnvironmentVariable(String variable, String value) {
    checkNotNull(variable);
    checkNotNull(value);
    this.environmentVariables.put(variable, value);
    return this;
  }

  @Override
  public String getEnvironmentVariable(String variable) {
    String value = environmentVariables.get(variable);
    checkArgument(
        value != null, "JavaCommandBuilder doesn't have '%s' environment variable set", variable);
    return value;
  }

  GeneralCommandLine build() {
    checkArgument(javaBinary != null, "javaBinary was not set");
    checkArgument(mainClass != null, "mainClass was not set");
    GeneralCommandLine commandLine = new GeneralCommandLine(javaBinary.getPath());
    commandLine.addParameters("-cp", classpaths.stream().map(File::getPath).collect(joining(":")));
    commandLine.addParameters(jvmArgs);
    commandLine.addParameters(
        systemProperties.entrySet().stream()
            .map(e -> "-D" + e.getKey() + '=' + e.getValue())
            .collect(toList()));
    commandLine.addParameter(mainClass);
    commandLine.addParameters(programArgs);

    if (workingDirectory != null) {
      commandLine.setWorkDirectory(workingDirectory);
    }

    commandLine.withParentEnvironmentType(ParentEnvironmentType.NONE);
    environmentVariables.forEach(commandLine::withEnvironment);

    return commandLine;
  }
}

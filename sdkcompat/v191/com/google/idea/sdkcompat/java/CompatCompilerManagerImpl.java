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
package com.google.idea.sdkcompat.java;

import com.google.common.collect.ImmutableList;
import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.openapi.compiler.ClassObject;
import com.intellij.openapi.compiler.CompilationException;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Compat implementation for {@link CompilerManagerImpl}.
 *
 * <p>#api191
 */
public abstract class CompatCompilerManagerImpl extends CompilerManagerImpl {
  private final Project project;

  public CompatCompilerManagerImpl(Project project, MessageBus messageBus) {
    super(project, messageBus);
    this.project = project;
  }

  @Override
  public Collection<ClassObject> compileJavaCode(
      List<String> options,
      Collection<File> platformCp,
      Collection<File> classpath,
      Collection<File> upgradeModulePath,
      Collection<File> modulePath,
      Collection<File> sourcePath,
      Collection<File> files,
      File outputDir)
      throws IOException, CompilationException {
    return super.compileJavaCode(
        options,
        platformCp,
        updateClasspath(project, classpath),
        upgradeModulePath,
        modulePath,
        sourcePath,
        files,
        outputDir);
  }

  private Collection<File> updateClasspath(Project project, Collection<File> classpath) {
    return ImmutableList.<File>builder()
        .addAll(classpath)
        .addAll(getAdditionalProjectJars(project))
        .build();
  }

  protected abstract Collection<File> getAdditionalProjectJars(Project project);
}

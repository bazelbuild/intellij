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
package com.google.idea.sdkcompat.java;

import com.google.common.collect.ImmutableList;
import com.intellij.execution.actions.RunConfigurationProducer;

/** List of java-related configuration producers for a given plugin version. */
public class JavaConfigurationProducerList {

  /**
   * Returns a list of run configuration producers to suppress for Blaze projects.
   *
   * <p>These classes must all be accessible from the Blaze plugin's classpath (e.g. they shouldn't
   * belong to any plugins not listed as dependencies of the Blaze plugin).
   */
  public static final ImmutableList<Class<? extends RunConfigurationProducer<?>>>
      PRODUCERS_TO_SUPPRESS =
          ImmutableList.of(
              com.intellij.execution.junit.AllInDirectoryConfigurationProducer.class,
              com.intellij.execution.junit.AllInPackageConfigurationProducer.class,
              com.intellij.execution.junit.TestInClassConfigurationProducer.class,
              com.intellij.execution.junit.TestClassConfigurationProducer.class,
              com.intellij.execution.junit.TestMethodConfigurationProducer.class,
              com.intellij.execution.junit.PatternConfigurationProducer.class,
              com.intellij.execution.application.ApplicationConfigurationProducer.class);
}

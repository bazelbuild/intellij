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
package com.google.idea.sdkcompat.python;

import com.google.common.collect.ImmutableList;

/** List of python configuration producers for a given plugin version. */
public class PyConfigurationProducersList {

  public static final ImmutableList<Class<?>> PRODUCERS_TO_SUPPRESS =
      ImmutableList.of(
          com.jetbrains.python.run.PythonRunConfigurationProducer.class,
          com.jetbrains.python.testing.universalTests.PyUniversalTestsConfigurationProducer.class,
          com.jetbrains.python.testing.nosetest.PythonNoseTestConfigurationProducer.class,
          com.jetbrains.python.testing.doctest.PythonDocTestConfigurationProducer.class,
          com.jetbrains.python.testing.pytest.PyTestConfigurationProducer.class,
          com.jetbrains.python.testing.unittest.PythonUnitTestConfigurationProducer.class);
}

# Copyright 2016 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Custom rule for creating IntelliJ plugin tests.
"""

# The JVM flags common to all test rules
JVM_FLAGS_FOR_TESTS = [
    "-Didea.classpath.index.enabled=false",
    "-Djava.awt.headless=true",
]

def intellij_test(name,
                  srcs,
                  test_package_root,
                  deps,
                  platform_prefix="Idea",
                  required_plugins=None,
                  integration_tests=False):
  """Creates a java_test rule comprising all valid test classes
  in the specified srcs.

  Args:
    name: name of this rule.
    srcs: the test classes.
    test_package_root: only tests under this package root will be run.
    deps: the required deps.
    plugin_jar: a target building the plugin to be tested. This will be added to the classpath.
    platform_prefix: Specifies the JetBrains product these tests are run against. Examples are
        'Idea' (IJ CE), 'idea' (IJ UE), 'CLion', 'AndroidStudio'. See
        com.intellij.util.PlatformUtils for other options.
    required_plugins: optional comma-separated list of plugin IDs. Integration tests will fail if
        these plugins aren't loaded at runtime.
    integration_tests: if true, bundled IJ core plugins will be added to the classpath.
  """

  if integration_tests:
    deps.append("//intellij-platform-sdk:bundled_plugins")


  jvm_flags = JVM_FLAGS_FOR_TESTS + [
      "-Didea.platform.prefix=" + platform_prefix,
      "-Didea.test.package.root=" + test_package_root,
  ]

  if required_plugins:
    jvm_flags.append("-Didea.required.plugins.id=" + required_plugins)

  native.java_test(
      name = name,
      srcs = srcs,
      deps = deps,
      size = "medium" if integration_tests else "small",
      jvm_flags = jvm_flags,
      test_class = "com.google.idea.blaze.base.suite.TestSuiteBuilder",
  )
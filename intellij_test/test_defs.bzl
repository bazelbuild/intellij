"""Custom rule for creating IntelliJ plugin tests.
"""

def intellij_unit_test_suite(name, srcs, test_package_root, **kwargs):
  """Creates a java_test rule comprising all valid test classes in the specified srcs.

  Args:
    name: name of this rule.
    srcs: the test classes.
    test_package_root: only tests under this package root will be run.
    **kwargs: Any other args to be passed to the java_test.
  """
  test_srcs = [test for test in srcs if test.endswith("Test.java")]
  test_classes = [_get_test_class(test_src, test_package_root) for test_src in test_srcs]
  suite_class_name = name + "TestSuite"
  suite_class = test_package_root + "." + suite_class_name
  _generate_test_suite(
      name = suite_class_name,
      test_package_root = test_package_root,
      test_classes = test_classes,
  )
  native.java_test(
      name = name,
      srcs = srcs + [suite_class_name],
      test_class = suite_class,
      **kwargs)

def _generate_test_suite(name, test_package_root, test_classes):
  """Generates a JUnit test suite pulling in all the referenced classes."""
  lines = []
  lines.append("package %s;" % test_package_root)
  lines.append("")
  lines.append("import org.junit.runner.RunWith;")
  lines.append("import org.junit.runners.Suite;")
  lines.append("")
  for test_class in test_classes:
    lines.append("import %s;" % test_class)
  lines.append("")
  lines.append("@RunWith(Suite.class)")
  lines.append("@Suite.SuiteClasses({")
  for test_class in test_classes:
    lines.append("    %s.class," % test_class.split(".")[-1])
  lines.append("})")
  lines.append("class %s {}" % name)

  contents = "\\n".join(lines)
  native.genrule(
      name = name,
      cmd = "printf '%s' > $@" % contents,
      outs = [name + ".java"],
  )


def _get_test_class(test_src, test_package_root):
  """Returns the test class of the source relative to the given root."""
  temp = test_src[:-5]
  temp = temp.replace("/", ".")
  i = temp.rfind(test_package_root)
  if i < 0:
    fail("Test source '%s' not under package root '%s'" % (test_src, test_package_root))
  test_class = temp[i:]
  return test_class

def intellij_integration_test_suite(
    name,
    srcs,
    test_package_root,
    deps,
    runtime_deps = [],
    platform_prefix="Idea",
    required_plugins=None,
    **kwargs):
  """Creates a java_test rule comprising all valid test classes in the specified srcs.

  Args:
    name: name of this rule.
    srcs: the test classes.
    test_package_root: only tests under this package root will be run.
    deps: the required deps.
    runtime_deps: the required runtime_deps.
    platform_prefix: Specifies the JetBrains product these tests are run against. Examples are
        'Idea' (IJ CE), 'idea' (IJ UE), 'CLion', 'AndroidStudio'. See
        com.intellij.util.PlatformUtils for other options.
    required_plugins: optional comma-separated list of plugin IDs. Integration tests will fail if
        these plugins aren't loaded at runtime.
    **kwargs: Any other args to be passed to the java_test.
  """

  runtime_deps = list(runtime_deps)
  runtime_deps.extend([
      "//intellij_test:lib",
      "//intellij_platform_sdk:bundled_plugins",
      "//third_party:jdk8_tools",
  ])

  jvm_flags = [
      "-Didea.classpath.index.enabled=false",
      "-Djava.awt.headless=true",
      "-Didea.platform.prefix=" + platform_prefix,
      "-Didea.test.package.root=" + test_package_root,
  ]

  if required_plugins:
    jvm_flags.append("-Didea.required.plugins.id=" + required_plugins)

  native.java_test(
      name = name,
      srcs = srcs,
      deps = deps,
      runtime_deps = runtime_deps,
      size = "medium",
      jvm_flags = jvm_flags,
      test_class = "com.google.idea.blaze.base.suite.TestSuiteBuilder",
      **kwargs)

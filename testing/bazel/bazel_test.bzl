load("@bazel_versions//:versions.bzl", "VERSIONS", "resolve")
load("//build_defs:common.bzl", "intellij_common")
load("//build_defs:intellij_integration_test.bzl", "intellij_integration_test")
load(":fixture_config.bzl", "bazel_fixture_config")
load(":project_archive.bzl", "project_archive")
load(":project_cache.bzl", "bazel_project_cache")

def _ge(v, t):
    return v >= t

def _gt(v, t):
    return v > t

def _le(v, t):
    return v <= t

def _lt(v, t):
    return v < t

_SPEC_OPS = {
    ">=": _ge,
    ">": _gt,
    "<=": _le,
    "<": _lt,
}

_OS_CONSTRAINTS = {
    "linux": "@platforms//os:linux",
    "macos": "@platforms//os:macos",
    "windows": "@platforms//os:windows",
}

_JVM_INTELLIJ_CONFIG_FLAGS = [
    # disables the default bazel security manager, causes tests to fail on windows
    "-Dcom.google.testing.junit.runner.shouldInstallTestSecurityManager=false",
    # fixes preferences not writable on mac
    "-Djava.util.prefs.PreferencesFactory=com.google.idea.testing.headless.InMemoryPreferencesFactory",
    # enable detailed logging in tests to diagnose issues in CI
    "-Didea.log.trace.categories=com.jetbrains.cidr.lang.workspace,com.google.idea.blaze.cpp.BlazeCWorkspace",
    # since test run with flat class loader lldbforntend cannot be loaded form the plugin
    "-Dcidr.debugger.use.lldbfrontend.from.plugin=false",
]

def _parse_version_spec(spec):
    """Parses version spec: None, int, '>=8', '>7', '<=9', '<10'."""
    if spec == None:
        return VERSIONS.keys()

    if type(spec) == "int":
        return [spec]

    for op, fn in _SPEC_OPS.items():
        if spec.startswith(op):
            t = int(spec[len(op):])
            return [v for v in VERSIONS.keys() if fn(v, t)]

    return [int(spec)]

def _build_compatible_with(os):
    """Builds a target_compatible_with select() from a list of OS names."""
    if os == None:
        return None

    excluded = [name for name in _OS_CONSTRAINTS if name not in os]
    if not excluded:
        return None

    conditions = {"//conditions:default": []}
    for name in excluded:
        conditions[_OS_CONSTRAINTS[name]] = ["@platforms//:incompatible"]

    return select(conditions)

def bazel_test(test, project, name = None, bazel_version = None, cache_flags = None, cache_targets = None, test_class = None, tags = None, os = None, **kwargs):
    name = name or test.removesuffix(".kt").removesuffix(".java")
    tags = tags or []
    target_compatible_with = _build_compatible_with(os)

    versions = [resolve(m) for m in sorted(_parse_version_spec(bazel_version))]

    bazel_project_cache(
        name = name + "_cache",
        bazel_versions = [v.label for v in versions],
        project = project,
        targets = cache_targets,
        flags = cache_flags,
        target_compatible_with = target_compatible_with,
    )

    for v in versions:
        test_name = "%s_%s" % (name, v.version.replace(".", "_"))

        bazel_fixture_config(
            name = test_name + "_config",
            project = project,
            cache = name + "_cache",
            bazel_binary = v.label,
        )

        intellij_integration_test(
            test = test,
            name = test_name,
            test_class = test_class or intellij_common.derive_test_class(name, "headlesstests"),
            runtime_deps = ["//clwb:clwb_bazel"],
            jvm_flags = _JVM_INTELLIJ_CONFIG_FLAGS,
            data = [test_name + "_config", project],
            tags = tags + ["exclusive"],
            env = {
                # disables automatic conversion of bazel target names to absolut windows paths by msys
                "MSYS_NO_PATHCONV": "true",
                # configures the test BazelProjectFixture
                "BAZEL_TEST_CONFIG": "$(rlocationpath %s_config)" % test_name,
                # for backwards compatability with rules_bazel_integration_test
                "BIT_BAZEL_VERSION": v.version,
            },
            # inherit bash shell and visual studio path from host for Windows
            env_inherit = ["BAZEL_SH", "BAZEL_VC", "BAZEL_LLVM", "PATH"],
            target_compatible_with = target_compatible_with,
            size = "enormous",
            **kwargs
        )

    native.test_suite(
        name = name,
        tags = ["manual"],
        tests = ["%s_%s" % (name, v.version.replace(".", "_")) for v in versions],
    )

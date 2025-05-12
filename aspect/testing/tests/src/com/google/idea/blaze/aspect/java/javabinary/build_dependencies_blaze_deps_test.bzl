"""Rules for testing aspect/build_dependencies_blaze_deps.bzl with java target."""

load("//aspect/testing/rules:build_dependencies_blaze_deps_test_fixture.bzl", "TargetsInfo")
load("//third_party/bazel_skylib/lib:unittest.bzl", "analysistest", "asserts")

def _impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)

    asserts.equals(env, None, target_under_test[TargetsInfo].target_infos[0].kotlin_info)
    asserts.equals(env, None, target_under_test[TargetsInfo].target_infos[0].java_proto_info)
    asserts.false(env, None, target_under_test[TargetsInfo].target_infos[0].cc_toolchain_target)  # why it contains cc_toolchain_target?

    asserts.equals(env, "foo", target_under_test[TargetsInfo].target_infos[0].label.name)
    jar_list = target_under_test[TargetsInfo].target_infos[0].java_info.compile_jars.to_list()
    asserts.equals(env, 0, len(jar_list))
    return analysistest.end(env)

_test = analysistest.make(_impl)

# Macro to setup the test.
def _setup(target_under_test):
    _test(
        name = "test",
        target_under_test = target_under_test,
    )

def test_suite(name, target_under_test):
    _setup(target_under_test = target_under_test)

    native.test_suite(
        name = name,
        tests = [
            ":test",
        ],
    )

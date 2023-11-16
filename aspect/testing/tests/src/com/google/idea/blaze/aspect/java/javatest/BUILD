licenses(["notice"])

load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)
load(
    "//aspect/testing/rules:fast_build_aspect_test_fixture.bzl",
    "fast_build_aspect_test_fixture",
)

java_test(
    name = "FooTest",
    size = "large",
    srcs = ["FooTest.java"],
    deps = ["@junit//jar"],
)

intellij_aspect_test_fixture(
    name = "footest_fixture",
    deps = [":FooTest"],
)

java_test(
    name = "JavaTestTest",
    srcs = ["JavaTestTest.java"],
    data = [":footest_fixture"],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing:guava",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
    ],
)

java_test(
    name = "FooTestNoLauncher",
    srcs = ["FooTest.java"],
    test_class = "com.google.idea.blaze.aspect.java.javatest.FooTest",
    deps = ["@junit//jar"],
)

fast_build_aspect_test_fixture(
    name = "footest_no_launcher_fast_build_fixture",
    deps = [":FooTestNoLauncher"],
)

cc_binary(
    name = "custom_java_launcher",
    srcs = ["java_launcher.cc"],
)

java_test(
    name = "FooTestWithCustomLauncher",
    srcs = ["FooTest.java"],
    launcher = ":custom_java_launcher",
    test_class = "com.google.idea.blaze.aspect.java.javatest.FooTest",
    deps = ["@junit//jar"],
)

fast_build_aspect_test_fixture(
    name = "footest_with_custom_launcher_fast_build_fixture",
    deps = [":FooTestWithCustomLauncher"],
)

java_test(
    name = "FooTestWithCustomMainClass",
    srcs = ["FooTest.java"],
    main_class = "com.google.idea.blaze.aspect.java.javatest.mock.MainClass",
    test_class = "com.google.idea.blaze.aspect.java.javatest.FooTest",
    deps = ["@junit//jar"],
)

fast_build_aspect_test_fixture(
    name = "footest_with_custom_main_class_fast_build_fixture",
    deps = [":FooTestWithCustomMainClass"],
)

java_test(
    name = "JavaTestFastBuildAspectTest",
    srcs = ["JavaTestFastBuildAspectTest.java"],
    data = [
        ":footest_no_launcher_fast_build_fixture",
        ":footest_with_custom_launcher_fast_build_fixture",
        ":footest_with_custom_main_class_fast_build_fixture",
    ],
    deps = [
        "//aspect/testing:guava",
        "//aspect/testing/rules:FastBuildAspectLoader",
        "//aspect/testing/rules:fast_build_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:fast_build_info_java_proto",
        "@junit//jar",
    ],
)

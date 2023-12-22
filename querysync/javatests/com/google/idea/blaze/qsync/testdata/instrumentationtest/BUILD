load(
    "@build_bazel_rules_android//android:rules.bzl",
    "android_binary",
    "android_instrumentation_test",
    "android_library",
)

android_binary(
    name = "app",
    manifest = "AppManifest.xml",
    deps = ["//querysync/javatests/com/google/idea/blaze/qsync/testdata/android"],
)

android_library(
    name = "test_lib",
    srcs = ["AndroidInstrumentationTest.java"],
)

android_binary(
    name = "test_app",
    instruments = ":app",
    manifest = "AndroidManifest.xml",
    deps = [":test_lib"],
)

android_instrumentation_test(
    name = "instrumentationtest",
    target_device = "//tools/mobile/devices/android/generic_phone:android_23_x86",
    test_app = ":test_app",
)

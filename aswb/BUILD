#
# Description: Builds ASwB for blaze and bazel
#

load(
    "//:build-visibility.bzl",
    "ASWB_PACKAGES_VISIBILITY",
    "ASWB_PLUGIN_PACKAGES_VISIBILITY",
    "ASWB_SUBPACKAGES_VISIBILITY",
    "DEFAULT_TEST_VISIBILITY",
    "TEST_ASWB_SUBPACKAGES_VISIBILITY",
)
load("//:version.bzl", "VERSION")
load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "intellij_plugin_library",
    "optional_plugin_xml",
    "plugin_deploy_zip",
    "repackaged_files",
    "stamped_plugin_xml",
)
load(
    "//build_defs:intellij_plugin_debug_target.bzl",
    "intellij_plugin_debug_target",
)
load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)
load(
    "//intellij_platform_sdk:build_defs.bzl",
    "combine_visibilities",
    "select_for_plugin_api",
)

optional_plugin_xml(
    name = "optional_ndk_xml",
    module = ["com.android.tools.ndk"],
    plugin_xml = "src/META-INF/ndk_contents.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_ndk_xml"],
    plugin_xmls = ["src/META-INF/aswb.xml"],
    visibility = ASWB_PLUGIN_PACKAGES_VISIBILITY,
    deps = [":aswb_lib"],
)

stamped_plugin_xml(
    name = "stamped_plugin_xml",
    changelog_file = "//:changelog",
    description_file = "src/META-INF/description.html",
    plugin_id = "com.google.idea.bazel.aswb",
    plugin_name = "Bazel for Android Studio",
    # #api212: We depend on an API which is only contained in 2021.2.1+.
    since_build_numbers = {"212": "212.5080.55"},
    stamp_since_build = True,
    stamp_until_build = True,
    version = VERSION,
)

java_library(
    name = "unit_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/unit/**/*.java"]) + select_for_plugin_api({
        "android-studio-2022.3": [
            "tests/utils/sdkcompat/as223/com/android/ide/common/repository/GoogleMavenArtifactIdCompat.java",
        ],
        "android-studio-2023.1": [
            "tests/utils/sdkcompat/as231/com/android/ide/common/repository/GoogleMavenArtifactIdCompat.java",
        ],
        "android-studio-2023.2": [
            "tests/utils/sdkcompat/as232/com/android/ide/common/repository/GoogleMavenArtifactIdCompat.java",
        ],
    }),
    visibility = combine_visibilities(TEST_ASWB_SUBPACKAGES_VISIBILITY, DEFAULT_TEST_VISIBILITY),
    deps = [
        ":aswb_lib",
        "//base",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "@com_google_guava_guava//jar",
    ],
)

java_library(
    name = "integration_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/integration/**/*.java"]) + select_for_plugin_api({
        "android-studio-2022.3": [
            "sdkcompat/as223/com/android/tools/rendering/RenderResultCompat.java",
            "tests/utils/sdkcompat/as223/com/android/tools/idea/sdk/IdeSdksCompat.java",
            "tests/utils/sdkcompat/as223/com/google/idea/blaze/android/functional/AndroidDeviceCompat.java",
            "tests/utils/sdkcompat/as223/com/google/idea/blaze/android/tools/idea/run/editor/AndroidDebuggerCompat.java",
        ],
        "android-studio-2023.1": [
            "sdkcompat/as231/com/android/tools/rendering/RenderResultCompat.java",
            "tests/utils/sdkcompat/as231/com/android/tools/idea/sdk/IdeSdksCompat.java",
            "tests/utils/sdkcompat/as231/com/google/idea/blaze/android/functional/AndroidDeviceCompat.java",
            "tests/utils/sdkcompat/as231/com/google/idea/blaze/android/tools/idea/run/editor/AndroidDebuggerCompat.java",
        ],
        "android-studio-2023.2": [
            "sdkcompat/as232/com/android/tools/rendering/RenderResultCompat.java",
            "tests/utils/sdkcompat/as232/com/android/tools/idea/sdk/IdeSdksCompat.java",
            "tests/utils/sdkcompat/as232/com/google/idea/blaze/android/functional/AndroidDeviceCompat.java",
            "tests/utils/sdkcompat/as232/com/google/idea/blaze/android/tools/idea/run/editor/AndroidDebuggerCompat.java",
        ],
    }),
    visibility = TEST_ASWB_SUBPACKAGES_VISIBILITY,
    deps = [
        ":aswb_lib",
        ":unit_test_utils",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//cpp",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",  # unuseddeps: keep
        "//intellij_platform_sdk:test_libs",
        "//java",
        "//shared:artifact",
        "//testing:lib",
        "@com_google_guava_guava//jar",
        "@error_prone_annotations//jar",
        "@junit//jar",
    ],
)

java_library(
    name = "aswb_lib",
    srcs = glob(
        ["src/**/*.java"],
        # These source files below should be omitted for versions < 2023.1.1.11
        exclude = ["src/com/google/idea/blaze/android/run/binary/tasks/*.java"],
    ) + select_for_plugin_api({
        "android-studio-2022.3": glob(["sdkcompat/as223/**/*.java"]),
        "android-studio-2023.1": glob(["sdkcompat/as231/**/*.java"] + [
            "src/com/google/idea/blaze/android/run/binary/tasks/*.java",
        ]),
        "android-studio-2023.2": glob(["sdkcompat/as232/**/*.java"] + [
            "src/com/google/idea/blaze/android/run/binary/tasks/*.java",
        ]),
    }),
    resources = glob(["resources/**/*"]),
    visibility = combine_visibilities(
        ASWB_SUBPACKAGES_VISIBILITY,
        TEST_ASWB_SUBPACKAGES_VISIBILITY,
    ),
    deps = [
        "//base",
        "//common/experiments",
        "//cpp",
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep
        "//intellij_platform_sdk:kotlin",
        "//intellij_platform_sdk:plugin_api",
        "//java",
        "//proto:proto_deps",
        "//querysync",
        "//shared",
        "//shared:artifact",
        "//third_party/auto_value",
        "@gson//jar",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(
        ["tests/unittests/**/*.java"],
        exclude = [
            "tests/unittests/com/google/idea/blaze/android/sync/model/idea/BlazeClassJarProviderTest.java",  # b/145809318
        ] + glob(["tests/unittests/sdkcompat/**"]),
    ) + select_for_plugin_api({
        "android-studio-2022.3": glob(["tests/unittests/sdkcompat/as223/**/*.java"]),
        "android-studio-2023.1": glob(["tests/unittests/sdkcompat/as231/**/*.java"]),
        "android-studio-2023.2": glob(["tests/unittests/sdkcompat/as232/**/*.java"]),
    }),
    test_package_root = "com.google.idea.blaze.android",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":aswb_lib",
        ":unit_test_utils",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//java",
        "//proto:proto_deps",
        "//shared",
        "//shared:artifact",
        "//testing:lib",
        "@com_google_guava_guava//jar",
        "@junit//jar",
    ],
)

test_suite(
    name = "integration_tests",
    tests = [
        #b/139825934 ":NdkDependenciesTest",
        ":normal_integration_tests",
    ],
    visibility = DEFAULT_TEST_VISIBILITY,
)

intellij_integration_test_suite(
    name = "normal_integration_tests",
    srcs = glob(
        [
            "tests/integrationtests/com/google/idea/blaze/android/**/*.java",
        ],
        exclude = [
            "tests/integrationtests/com/google/idea/blaze/android/plugin/NdkDependenciesTest.java",  # Extracted to separate target
            "tests/integrationtests/com/google/idea/blaze/android/functional/AswbMergedManifestTest.java",  #b/222322106
        ] + glob(["tests/integrationtests/sdkcompat/**"]),
    ) + select_for_plugin_api({
        "android-studio-2022.3": glob(["tests/integrationtests/sdkcompat/as223/**/*.java"]),
        "android-studio-2023.1": glob(["tests/integrationtests/sdkcompat/as231/**/*.java"]),
        "android-studio-2023.2": glob(["tests/integrationtests/sdkcompat/as232/**/*.java"]),
        "default": [],
    }),
    additional_class_rules = select_for_plugin_api({
        "android-studio-2022.3": ["com.google.idea.blaze.android.NormalIntegrationTestSetupRule"],
        "android-studio-2023.1": ["com.google.idea.blaze.android.NormalIntegrationTestSetupRule"],
        "android-studio-2023.2": ["com.google.idea.blaze.android.NormalIntegrationTestSetupRule"],
        "default": [],
    }),
    data = [
        "testdata/golden.png",
        "testdata/ic_banner.png",
        "tools/adt/idea/android/annotations/empty_file",
    ],
    required_plugins = "com.google.idea.bazel.aswb,com.android.tools.ndk",
    test_package_root = "com.google.idea.blaze.android",
    runtime_deps = [
        ":aswb_bazel",
        # Needed to work around one-version issue
        "//intellij_platform_sdk:test_libs",
    ],
    deps = [
        ":aswb_lib",
        ":integration_test_utils",
        ":unit_test_utils",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//cpp",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//java",
        "//proto:proto_deps",
        "//shared:artifact",
        "@com_google_guava_guava//jar",
        "@gson//jar",
        "@junit//jar",
    ],
)

intellij_integration_test_suite(
    name = "NdkDependenciesTest",
    srcs = ["tests/integrationtests/com/google/idea/blaze/android/plugin/NdkDependenciesTest.java"],
    required_plugins = "com.google.idea.bazel.aswb",
    test_package_root = "com.google.idea.blaze.android",
    runtime_deps = [
        ":aswb_bazel",
        "//cpp",
        "//java",
    ],
    deps = [
        ":aswb_lib",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "//proto:proto_deps",
        "@junit//jar",
    ],
)

intellij_plugin(
    name = "aswb_bazel",
    plugin_xml = ":stamped_plugin_xml",
    tags = [
        "incomplete-deps",  # remove this suppression and add any missing deps, see go/java-import-deps-checking-lsc
    ],
    deps = [
        ":plugin_library",
        "//base:plugin_library",
        "//cpp:plugin_library",
        "//dart:plugin_library",
        "//java:plugin_library",
        "//kotlin:plugin_library",
        "//plugin_dev:plugin_library",
        # BEGIN-EXTERNAL
        "//python:plugin_library",
        # END-EXTERNAL
        "//skylark:plugin_library",
        "//terminal:plugin_library",
    ],
)

repackaged_files(
    name = "plugin_jar",
    srcs = [":aswb_bazel"],
    prefix = "aswb/lib",
)

repackaged_files(
    name = "aspect_directory",
    srcs = ["//aspect:aspect_files"],
    prefix = "aswb/aspect",
    strip_prefix = "/aspect",
    visibility = ASWB_PACKAGES_VISIBILITY,
    java_copts = ["-source", "8", "-target", "8"],
)

repackaged_files(
    name = "fast_build_javac",
    srcs = ["//java:fast_build_javac"],
    prefix = "aswb/lib",
    visibility = ASWB_PACKAGES_VISIBILITY,
    java_copts = ["-source", "8", "-target", "8"],
)

intellij_plugin_debug_target(
    name = "aswb_bazel_dev",
    deps = [
        ":aspect_directory",
        ":fast_build_javac",
        ":plugin_jar",
    ],
)

plugin_deploy_zip(
    name = "aswb_bazel_zip",
    srcs = [
        ":aspect_directory",
        ":fast_build_javac",
        ":plugin_jar",
    ],
    visibility = ["//visibility:public"],
    zip_filename = "aswb_bazel.zip",
)

genrule(
    name = "create_empty_annotations_folder",
    outs = ["tools/adt/idea/android/annotations/empty_file"],
    cmd = "echo \"No contents\" > $@",
)

#
# Description: Builds ASwB for blaze and bazel
#

load("@bazel_skylib//rules:build_test.bzl", "build_test")
load(
    "//:build-visibility.bzl",
    "ASWB_PACKAGES_VISIBILITY",
    "ASWB_PLUGIN_PACKAGES_VISIBILITY",
    "ASWB_SUBPACKAGES_VISIBILITY",
    "DEFAULT_TEST_VISIBILITY",
    "TEST_ASWB_SUBPACKAGES_VISIBILITY",
)
load(
    "//build_defs:build_defs.bzl",
    "intellij_plugin",
    "intellij_plugin_library",
    "optional_plugin_xml",
    "plugin_deploy_zip",
    "repackaged_files",
    "stamped_plugin_xml",
)

# BEGIN-REPO
load(
    "//build_defs:restrictions.bzl",
    "ALLOWED_EXTERNAL_TEST_DEPENDENCIES",
    "EXISTING_EXTERNAL_TEST_VIOLATIONS",
    "validate_test_dependencies",
)

# END-REPO
load(
    "//testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)
load(
    "//intellij_platform_sdk:build_defs.bzl",
    "combine_visibilities",
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
    version_file = ":plugin_version.txt",
)

java_library(
    name = "unit_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/unit/**/*.java"]),
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
    srcs = glob(["tests/utils/integration/**/*.java"]),
    visibility = TEST_ASWB_SUBPACKAGES_VISIBILITY,
    deps = [
        ":aswb_lib",
        "//base",
        "//base:integration_test_utils",
        "//base:unit_test_utils",
        "//cpp",
        "//java",
        "//shared:artifact",
        "//testing:lib",
        "//intellij_platform_sdk:plugin_api",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",  # unuseddeps: keep
        "//intellij_platform_sdk:test_libs",
        "@junit//jar",
        "@com_google_guava_guava//jar",
    ],
)

java_library(
    name = "aswb_lib",
    srcs = glob(
        [
            "src/**/*.java",
            "sdkcompat/as233/**/*.java",
            "src/com/google/idea/blaze/android/run/binary/tasks/*.java",
        ],
    ),
    resources = glob(["resources/**/*"]),
    visibility = combine_visibilities(
        ASWB_SUBPACKAGES_VISIBILITY,
        TEST_ASWB_SUBPACKAGES_VISIBILITY,
    ),
    deps = [
        "//base",
        "//common/experiments",
        "//cpp",
        "//java",
        "//proto:proto_deps",
        "//querysync",
        "//shared",
        "//shared:artifact",
        "//intellij_platform_sdk:plugin_api",
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep
        "//intellij_platform_sdk:kotlin",
        "//third_party/auto_value",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(
        ["tests/unittests/**/*.java"],
        exclude = [
            "tests/unittests/com/google/idea/blaze/android/sync/model/idea/BlazeClassJarProviderTest.java",  # b/145809318
        ],
    ),
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.android",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":aswb_lib",
        ":unit_test_utils",
        "//base",
        "//base:unit_test_utils",
        "//common/experiments",
        "//common/experiments:unit_test_utils",
        "//java",
        "//proto:proto_deps",
        "//shared",
        "//shared:artifact",
        "//testing:lib",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "@junit//jar",
        "@com_google_guava_guava//jar",
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
    ),
    additional_class_rules = [],
    data = [
        "testdata/golden.png",
        "testdata/ic_banner.png",
        "tools/adt/idea/android/annotations/empty_file",
    ],
    required_plugins = "com.google.idea.bazel.aswb,com.android.tools.ndk",
    tags = ["noci:studio-win"],
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
        "//java",
        "//proto:proto_deps",
        "//shared:artifact",
        "//intellij_platform_sdk:plugin_api",
        "//intellij_platform_sdk:jsr305",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
        "@junit//jar",
        "@com_google_guava_guava//jar",
    ],
)

intellij_integration_test_suite(
    name = "NdkDependenciesTest",
    srcs = ["tests/integrationtests/com/google/idea/blaze/android/plugin/NdkDependenciesTest.java"],
    required_plugins = "com.google.idea.bazel.aswb",
    tags = ["noci:studio-win"],
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
        "//proto:proto_deps",
        "//intellij_platform_sdk:plugin_api_for_tests",
        "//intellij_platform_sdk:test_libs",
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
    # Build aspect tools with Java 8 so they can work with any Java version in users workspace.
    java_copts = [
        "-source",
        "8",
        "-target",
        "8",
    ],
    prefix = "aswb/aspect",
    strip_prefix = "/aspect",
    visibility = ASWB_PACKAGES_VISIBILITY,
)

plugin_deploy_zip(
    name = "aswb_bazel_zip",
    srcs = [
        ":aspect_directory",
        ":plugin_jar",
    ],
    visibility = ["//visibility:public"],
    zip_filename = "aswb_bazel.zip",
)

build_test(
    name = "aswb_bazel_build_test",
    targets = [
        ":aswb_bazel_zip",
    ],
)

genrule(
    name = "create_empty_annotations_folder",
    outs = ["tools/adt/idea/android/annotations/empty_file"],
    cmd = "echo \"No contents\" > $@",
)

# BEGIN-REPO
validate_test_dependencies(
    name = "validate_test_dependencies",
    allowed_external_dependencies = ALLOWED_EXTERNAL_TEST_DEPENDENCIES,
    existing_external_violations = EXISTING_EXTERNAL_TEST_VIOLATIONS,
    deps = [
        "//:aswb_tests",
        "//aswb:aswb_bazel_build_test",
        "//common/experiments:unit_tests",
        "//common/ui/properties:unit_tests",
        "//common/util:CommandLineTaskTest",
        "//querysync:cli_tools_build_test",
        "//shared:tests",
    ],
)
# END-REPO

# Dump the plugin version to a file to be used in stamping the plugin xml
genrule(
    name = "plugin_version",
    outs = ["plugin_version.txt"],
    cmd = """
# bazel-out/stable-status.txt is created when stamp = True
# Value of BUILD_EMBED_LABEL key comes from --embed_label on the command line
version="$$(grep BUILD_EMBED_LABEL bazel-out/stable-status.txt | cut -d ' ' -f 2)"
# Set a default version if --embed_label was not specified
if [ -z "$$version" ]; then version="9999"; fi

echo "$$version" > $@
""",
    stamp = True,
)

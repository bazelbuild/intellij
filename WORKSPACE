workspace(name = "intellij_with_bazel")

load("@bazel_tools//tools/build_defs/repo:git.bzl", "new_git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for IntelliJ 2021.2. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2021_2",
    build_file = "@//intellij_platform_sdk:BUILD.idea212",
    sha256 = "aa38bf2f86b570ce9cac14b01f7e3bf8f592d05641384e7ecedde13cbfa6491a",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2021.2.4/ideaIC-2021.2.4.zip",
)

# The plugin api for intellij_ce_2021_3. This is required to build IJwB and run integration tests.
http_archive(
    name = "intellij_ce_2021_3",
    build_file = "@//intellij_platform_sdk:BUILD.idea213",
    sha256 = "7686d43fe0ea621718c1c9816460028146586ec10de1420500fc847edc603bb9",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2021.3.3/ideaIC-2021.3.3.zip",
)

# The plugin api for intellij_ce_2022_1. This is required to build IJwB and run integration tests.
http_archive(
    name = "intellij_ce_2022_1",
    build_file = "@//intellij_platform_sdk:BUILD.idea221",
    sha256 = "1adb1909493140ee5246b6c67cc70d7b8e72abbe924e8201c079b63fc80cc1f4",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2022.1/ideaIC-2022.1.zip",
)

# The plugin api for IntelliJ UE 2021.2. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2021_2",
    build_file = "@//intellij_platform_sdk:BUILD.ue212",
    sha256 = "f5e942e090693c139dda22e798823285e22d7b31aaad5d52c23a370a6e91ec7d",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2021.2.4/ideaIU-2021.2.4.zip",
)

# The plugin api for intellij_ue_2021_3. This is required to run UE-specific integration tests.
http_archive(
    name = "intellij_ue_2021_3",
    build_file = "@//intellij_platform_sdk:BUILD.ue213",
    sha256 = "fc5ce48e614d5c083270a892cd5b38c9300f18aac41e1e0c7d15c518e978e96a",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2021.3.3/ideaIU-2021.3.3.zip",
)

# The plugin api for intellij_ue_2022_1. This is required to run UE-specific integration tests.
http_archive(
    name = "intellij_ue_2022_1",
    build_file = "@//intellij_platform_sdk:BUILD.ue221",
    sha256 = "7a51c48dadd64cf7644210aa46163fb72cf48fc069e6047bdc1dd91a763e1a26",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2022.1/ideaIU-2022.1.zip",
)

# The plugin api for clion_2021_2. This is required to build CLwB, and run integration tests.
http_archive(
    name = "clion_2021_2",
    build_file = "@//intellij_platform_sdk:BUILD.clion212",
    sha256 = "1b9a882aa703303dead8b9459bd8d4f2572bd977d46dce99af96c1647231da2c",
    url = "https://download.jetbrains.com/cpp/CLion-2021.2.4.tar.gz",
)

# The plugin api for clion_2021_3. This is required to build CLwB, and run integration tests.
http_archive(
    name = "clion_2021_3",
    build_file = "@//intellij_platform_sdk:BUILD.clion213",
    sha256 = "f3b0b9e0dd0cd4aebef5d424e7a22868c732daad47d6c94f73630cef449ccf78",
    url = "https://download.jetbrains.com/cpp/CLion-2021.3.4.tar.gz",
)

# The plugin api for clion_2022_1. This is required to build CLwB\, and run integration tests.
http_archive(
    name = "clion_2022_1",
    build_file = "@//intellij_platform_sdk:BUILD.clion221",
    sha256 = "a8ad8db6362d60a5ce60a7552110887dbd12e8420c839c368b55808b68dea38b",
    url = "https://download.jetbrains.com/cpp/CLion-2022.1.tar.gz",
)

_PYTHON_CE_BUILD_FILE = """
java_import(
    name = "python",
    jars = ["python-ce/lib/python-ce.jar"],
    visibility = ["//visibility:public"],
)
"""

#TODO(ymoh): remove with the removal of 2021.1 Python plugin
# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2021_1",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "7d16cc9bf80c9e2c26d55d55564c1c174583a5e6900e6b7f13d5663275b07644",
    url = "https://plugins.jetbrains.com/files/7322/125352/python-ce-211.7628.24.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2021_2",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "ce110ae1a5d3787bc85ae88d67fa2faa2be959a3e8acfc3567f8ed7b64c9151a",
    url = "https://plugins.jetbrains.com/files/7322/151370/python-ce-212.5712.43.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2021_3",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "47df4c32a19354efcc2d8171de85083e8e43b849c066bb979ed313b6309de08b",
    url = "https://plugins.jetbrains.com/files/7322/162748/python-ce-213.7172.26.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2022_1",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "6bed952ec17dd965d75152ea27b1dabd18c56b6f7c9986a316fccc61792238e9",
    url = "https://plugins.jetbrains.com/files/7322/167535/python-ce-221.5080.210.zip",
)

_GO_BUILD_FILE = """
java_import(
    name = "go",
    jars = glob(["go/lib/*.jar"]),
    visibility = ["//visibility:public"],
)
"""

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2021_2",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "5c868f2be8feb552aa4f9edb2a3c48db68193eb49ba50ca0a0976f4b9de82c67",
    url = "https://plugins.jetbrains.com/files/9568/149614/go-212.5712.14.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2021_3",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "12c3acf0e75f8d7fb5655e9400faa26bbc7b314c515c4a3ca9e6bb8c3a130a58",
    url = "https://plugins.jetbrains.com/files/9568/160433/go-213.7172.6.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2022_1",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "50b96a18d3bf95b2742fb01d8d8ae9d7faef7113b882011afea4a912699ac0f9",
    url = "https://plugins.jetbrains.com/files/9568/167522/go-221.5080.210.zip",
)

_SCALA_BUILD_FILE = """
java_import(
    name = "scala",
    jars = glob(["Scala/lib/*.jar"]),
    visibility = ["//visibility:public"],
)
"""

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2021_2",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "8d9c2831920fb69a52898598dc7f78c455001b3ebd1956b972757ffae7c0f056",
    url = "https://plugins.jetbrains.com/files/1347/153522/scala-intellij-bin-2021.2.30.zip",
)

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2021_3",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "c14a15321060260360c3b8d41e9ef4080b5e552d2d0eb30ce6b141da08ee4764",
    url = "https://plugins.jetbrains.com/files/1347/160380/scala-intellij-bin-2021.3.20.zip",
)

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2022_1",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "6a8cfc87c9485f48bd13856b5f4819a020f77c2a90f38d7128e026fc323330a7",
    url = "https://plugins.jetbrains.com/files/1347/167520/scala-intellij-bin-2022.1.13.zip",
)

# The plugin api for android_studio_2021_2. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_2021_2",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio212",
    sha256 = "3a4130a56e8fc1569af672936bc8694624ab22d0bea572f143166ba6056f23f1",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2021.2.1.13/android-studio-2021.2.1.13-linux.tar.gz",
)

# The plugin api for android_studio_2021_3. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_2021_3",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio213",
    sha256 = "cbbd78efebddae9dfb25a97fa3b42861474dcae076d433fe5cb61ee37937a25b",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2021.3.1.9/android-studio-2021.3.1.9-linux.tar.gz",
)

# LICENSE: Common Public License 1.0
jvm_maven_import_external(
    name = "junit",
    artifact = "junit:junit:4.12",
    artifact_sha256 = "59721f0805e223d84b90677887d9ff567dc534d7c502ca903c0c2b17f05c116a",
    licenses = ["notice"],  # Common Public License 1.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "jsr305_annotations",
    artifact = "com.google.code.findbugs:jsr305:3.0.2",
    artifact_sha256 = "766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "truth",
    artifact = "com.google.truth:truth:0.42",
    artifact_sha256 = "dd652bdf0c4427c59848ac0340fd6b6d20c2cbfaa3c569a8366604dbcda5214c",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "truth8",
    artifact = "com.google.truth.extensions:truth-java8-extension:0.42",
    artifact_sha256 = "cf9e095a6763bc33633b8844c3ebadffe3b082c81dd97a4d79b64ad88d305bc1",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "mockito",
    artifact = "org.mockito:mockito-core:3.3.0",
    artifact_sha256 = "fc1a1f2d1d64566bc31ee36d8214059f2adbe303d9109e5cc0e99685741c57c2",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "bytebuddy",
    artifact = "net.bytebuddy:byte-buddy:1.10.5",
    artifact_sha256 = "3c9c603970bb9d68572c1aa29e9ae6b477d602922977a04bfa5f3b5465d7d1f4",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "bytebuddy-agent",
    artifact = "net.bytebuddy:byte-buddy-agent:1.10.5",
    artifact_sha256 = "290c9930965ef5810ddb15baf3b3647ce952f40fa2f0af82d5f669e04ba87e5b",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "auto_value",
    artifact = "com.google.auto.value:auto-value:1.6.2",
    artifact_sha256 = "edbe65a5c53e3d4f5cb10b055d4884ae7705a7cd697be4b2a5d8427761b8ba12",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "auto_value_annotations",
    artifact = "com.google.auto.value:auto-value-annotations:1.6.2",
    artifact_sha256 = "b48b04ddba40e8ac33bf036f06fc43995fc5084bd94bdaace807ce27d3bea3fb",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "error_prone_annotations",
    artifact = "com.google.errorprone:error_prone_annotations:2.13.1",
    artifact_sha256 = "f5ee2aac2ee6443789e1dee0f96e3c35d9f3c78891f54ed83f3cf918a1cde6d1",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

_JARJAR_BUILD_FILE = """
java_binary(
    name = "jarjar_bin",
    srcs = glob(
        ["src/main/**/*.java"],
        exclude = [
            "src/main/com/tonicsystems/jarjar/JarJarMojo.java",
            "src/main/com/tonicsystems/jarjar/util/AntJarProcessor.java",
            "src/main/com/tonicsystems/jarjar/JarJarTask.java",
        ],
    ),
    main_class = "com.tonicsystems.jarjar.Main",
    resources = [":help"],
    use_launcher = False,
    visibility = ["//visibility:public"],
    deps = [":asm"],
)

java_import(
    name = "asm",
    jars = glob(["lib/asm-*.jar"]),
)

genrule(
    name = "help",
    srcs = ["src/main/com/tonicsystems/jarjar/help.txt"],
    outs = ["com/tonicsystems/jarjar/help.txt"],
    cmd = "cp $< $@",
)
"""

new_git_repository(
    name = "jarjar",
    build_file_content = _JARJAR_BUILD_FILE,
    commit = "38ff702d10baec78f30d5f57485ae452f0fe33b5",
    remote = "https://github.com/google/jarjar",
    shallow_since = "1518210648 -0800",
)

http_archive(
    name = "bazel_skylib",
    sha256 = "2ef429f5d7ce7111263289644d233707dba35e39696377ebab8b0bc701f7818e",
    url = "https://github.com/bazelbuild/bazel-skylib/releases/download/0.8.0/bazel-skylib.0.8.0.tar.gz",
)

# specify a minimum version for bazel otherwise users on old versions may see
# unexpressive errors when new features are used
load("@bazel_skylib//lib:versions.bzl", "versions")

versions.check(minimum_bazel_version = "4.0.0")

http_archive(
    name = "build_bazel_integration_testing",
    sha256 = "e055ff971787a27d6942a83ffd182953988c88dfa82e89138ccc83bf410a65d6",
    strip_prefix = "bazel-integration-testing-2a4f6c244312c036e0f3a125ee6086637ee7723b",
    url = "https://github.com/bazelbuild/bazel-integration-testing/archive/2a4f6c244312c036e0f3a125ee6086637ee7723b.zip",
)

load("@build_bazel_integration_testing//tools:bazel_java_integration_test.bzl", "bazel_java_integration_test_deps")

bazel_java_integration_test_deps(versions = [
    "0.28.1",
    "0.27.2",
])

load("@build_bazel_integration_testing//tools:import.bzl", "bazel_external_dependency_archive")

bazel_external_dependency_archive(
    name = "integration_test_deps",
    srcs = {
        # Bazel 0.28.1, 0.27.2
        "cc470e529fafb6165b5be3929ff2d99b38429b386ac100878687416603a67889": [
            "https://mirror.bazel.build/bazel_coverage_output_generator/releases/coverage_output_generator-v1.0.zip",
        ],
        # Bazel 0.28.1
        "96e223094a12c842a66db0bb7bb6866e88e26e678f045842911f9bd6b47161f5": [
            "https://mirror.bazel.build/bazel_java_tools/releases/javac11/v4.0/java_tools_javac11_linux-v4.0.zip",
        ],
        # Bazel 0.27.2
        "074d624fb34441df369afdfd454e75dba821d5d54932fcfee5ba598d17dc1b99": [
            "https://mirror.bazel.build/bazel_java_tools/releases/javac11/v2.0/java_tools_javac11_linux-v2.0.zip",
        ],
    },
)

# Use the latest version of java_tools which contains a fix for the ijar multi-disk issue
# Once this version is used by a Bazel release (it should be there in Bazel 5.2.0),
# we should remove this import and set the minimum_bazel_version to 5.2.0
http_archive(
    name = "remote_java_tools",
    sha256 = "2eede49b2d80135e0ea22180f63df26db2ed4b795c1c041b25cc653d6019fbec",
    urls = [
        "https://mirror.bazel.build/bazel_java_tools/releases/java/v11.7.1/java_tools-v11.7.1.zip",
        "https://github.com/bazelbuild/java_tools/releases/download/java_v11.7.1/java_tools-v11.7.1.zip",
    ],
)

http_archive(
    name = "remote_java_tools_linux",
    sha256 = "f78077f0c043d0d13c82de0ee4a99753e66bb18ec46e3601fa2a10e7f26798a8",
    urls = [
        "https://mirror.bazel.build/bazel_java_tools/releases/java/v11.7.1/java_tools_linux-v11.7.1.zip",
        "https://github.com/bazelbuild/java_tools/releases/download/java_v11.7.1/java_tools_linux-v11.7.1.zip",
    ],
)

http_archive(
    name = "remote_java_tools_windows",
    sha256 = "a7086734866505292ee4c206328c73c6af127e69bd51b98c9c186ae4b9b6d2db",
    urls = [
        "https://mirror.bazel.build/bazel_java_tools/releases/java/v11.7.1/java_tools_windows-v11.7.1.zip",
        "https://github.com/bazelbuild/java_tools/releases/download/java_v11.7.1/java_tools_windows-v11.7.1.zip",
    ],
)

http_archive(
    name = "remote_java_tools_darwin",
    sha256 = "4d6d388b54ad3b9aa35b30dd67af8d71c4c240df8cfb5000bbec67bdd5c53a73",
    urls = [
        "https://mirror.bazel.build/bazel_java_tools/releases/java/v11.7.1/java_tools_darwin-v11.7.1.zip",
        "https://github.com/bazelbuild/java_tools/releases/download/java_v11.7.1/java_tools_darwin-v11.7.1.zip",
    ],
)

# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "rules_proto",
    sha256 = "aa1ee19226f707d44bee44c720915199c20c84a23318bb0597ed4e5c873ccbd5",
    strip_prefix = "rules_proto-40298556293ae502c66579620a7ce867d5f57311",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_proto/archive/40298556293ae502c66579620a7ce867d5f57311.tar.gz",
        "https://github.com/bazelbuild/rules_proto/archive/40298556293ae502c66579620a7ce867d5f57311.tar.gz",
    ],
)

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")

rules_proto_dependencies()

rules_proto_toolchains()

# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "io_bazel_rules_scala",
    sha256 = "ccf19e8f966022eaaca64da559c6140b23409829cb315f2eff5dc3e757fb6ad8",
    strip_prefix = "rules_scala-e4560ac332e9da731c1e50a76af2579c55836a5c",
    urls = ["https://github.com/bazelbuild/rules_scala/archive/e4560ac332e9da731c1e50a76af2579c55836a5c.zip"],
)

load("@io_bazel_rules_scala//:scala_config.bzl", "scala_config")

scala_config()

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")

scala_repositories()

load("@io_bazel_rules_scala//scala:toolchains.bzl", "scala_register_toolchains")

scala_register_toolchains()

load("@io_bazel_rules_scala//testing:scalatest.bzl", "scalatest_repositories", "scalatest_toolchain")

scalatest_repositories()

scalatest_toolchain()

# LICENSE: The Apache Software License, Version 2.0
rules_kotlin_version = "v1.5.0-beta-3"

rules_kotlin_sha = "58edd86f0f3c5b959c54e656b8e7eb0b0becabd412465c37a2078693c2571f7f"

http_archive(
    name = "io_bazel_rules_kotlin",
    sha256 = rules_kotlin_sha,
    urls = ["https://github.com/bazelbuild/rules_kotlin/releases/download/%s/rules_kotlin_release.tgz" % rules_kotlin_version],
)

load("@io_bazel_rules_kotlin//kotlin:repositories.bzl", "kotlin_repositories")
load("@io_bazel_rules_kotlin//kotlin:core.bzl", "kt_register_toolchains")

kotlin_repositories()

kt_register_toolchains()

# Without this dependency, when a test that uses Google truth fails, instead of
# the textual difference we get java.lang.NoClassDefFoundError: difflib/DiffUtils
jvm_maven_import_external(
    name = "diffutils",
    artifact = "com.googlecode.java-diff-utils:diffutils:1.2.1",
    artifact_sha256 = "c98697c3b8dd745353cd0a83b109c1c999fec43b4a5cedb2f579452d8da2c171",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

# Dependency needed for kotlin coroutines library
jvm_maven_import_external(
    name = "kotlinx_coroutines",
    artifact = "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2",
    artifact_sha256 = "4cd24a06b2a253110d8afd250e9eec6c6faafea6463d740824743d637e761f12",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

# Dependency needed for kotlin coroutines test library
jvm_maven_import_external(
    name = "kotlinx_coroutines_test",
    artifact = "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.4.2",
    artifact_sha256 = "2e3091a94b8b822c9b68c4dc92ad6a6b0e39e2245b0fc75862de20f5a7a71e9a",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

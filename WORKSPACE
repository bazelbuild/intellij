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
    sha256 = "bab553d24c5ce82c6ec4ae5e2ec0e3c27a3b869021bd47bb6b6562db972e49c1",
    url = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/idea/ideaIC/221.5080.9-EAP-SNAPSHOT/ideaIC-221.5080.9-EAP-SNAPSHOT.zip",
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
    sha256 = "4c12f78c91a66f00a6f65cd71c67aa0f2b5c6097971a6f4a5da769a6bfff79b2",
    url = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/idea/ideaIU/221.5080.9-EAP-SNAPSHOT/ideaIU-221.5080.9-EAP-SNAPSHOT.zip",
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
    sha256 = "a6cc4810a9d0c5d6b663feae8e97f093c3cb2d486eea0f3edbb36574cdb8e054",
    url = "https://download.jetbrains.com/cpp/CLion-221.4906.7.tar.gz",
)

_PYTHON_CE_BUILD_FILE = """
java_import(
    name = "python",
    jars = ["python-ce/lib/python-ce.jar"],
    visibility = ["//visibility:public"],
)
"""

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2020_3",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "722fb54b503de61989d65bc544f25f03891614467e62f4faef677cefbcd51340",
    url = "https://plugins.jetbrains.com/files/7322/114033/python-ce-203.7717.65.zip",
)

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

http_archive(
    name = "python_2022_1",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "089a270784e15b1ab86e3be9f08d3ce5c196d38f0ee505b382dfcbc9bd324942",
    url = "https://plugins.jetbrains.com/files/7322/164150/python-ce-221.5080.57.zip",
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

http_archive(
    name = "go_2022_1",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "c2faa795bca078c3ae341bda0f269f42d4c1d400428503577fa48216c25f6408",
    url = "https://plugins.jetbrains.com/files/9568/164088/go-221.5080.56.zip",
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

http_archive(
    name = "scala_2022_1",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "0b0ed45f449a5761f91b5b3f7840bbc8762815776fe04d4b3347259ec2b5fe58",
    url = "https://plugins.jetbrains.com/files/1347/164249/scala-intellij-bin-2022.1.8.zip",
)

# The plugin api for Android Studio 2020.3. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_2020_3",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio203",
    sha256 = "344d858235ed5d3095ac25916a4a8f8730069f76e5a5fd0eba02522af88f541b",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2020.3.1.26/android-studio-2020.3.1.26-linux.tar.gz",
)

# The plugin api for android_studio_2021_2. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_2021_2",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio212",
    sha256 = "4dae7d48aaf31fe1904fd96850e9b6fbeddb6e2168aaf2ad86c99f92b6e9c846",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2021.2.1.11/android-studio-2021.2.1.11-linux.tar.gz",
)

# The plugin api for android_studio_2021_3. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_2021_3",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio213",
    sha256 = "8ecc8e3ca13475f5924af2ed871a5588c454a7a9d826c270f4f506cbe39082b5",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2021.3.1.6/android-studio-2021.3.1.6-linux.tar.gz",
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
    artifact = "com.google.errorprone:error_prone_annotations:2.3.0",
    artifact_sha256 = "524b43ea15ca97c68f10d5f417c4068dc88144b620d2203f0910441a769fd42f",
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

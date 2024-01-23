workspace(name = "intellij_with_bazel")

load("@bazel_tools//tools/build_defs/repo:git.bzl", "new_git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for intellij_ce_2022_3 idea. This is required to build IJwB and run integration tests.
http_archive(
    name = "intellij_ce_2022_3",
    build_file = "@//intellij_platform_sdk:BUILD.idea223",
    sha256 = "5fe178e7a52ed3efa72f0761f17cbf065ebfc7e6c91c4366cd2e87d1bb032794",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2022.3.3/ideaIC-2022.3.3.zip",
)

# The plugin api for intellij_ue_2022_3 ue. This is required to run UE-specific integration tests.
http_archive(
    name = "intellij_ue_2022_3",
    build_file = "@//intellij_platform_sdk:BUILD.ue223",
    sha256 = "c4711503aed650d12c2d3014721384d7c223d23591400bae7e94286e511d3220",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2022.3.3/ideaIU-2022.3.3.zip",
)

# The plugin api for clion_2022_3 clion. This is required to build CLwB\, and run integration tests.
http_archive(
    name = "clion_2022_3",
    build_file = "@//intellij_platform_sdk:BUILD.clion223",
    sha256 = "1b46ff0791bcb38ecb39c5f4a99941f99ed73d4f6d924a2042fdb55afc5fc03d",
    url = "https://download.jetbrains.com/cpp/CLion-2022.3.3.tar.gz",
)

_PYTHON_CE_BUILD_FILE = """
java_import(
    name = "python",
    jars = ["python-ce/lib/python-ce.jar"],
    visibility = ["//visibility:public"],
)
filegroup(
  name = "python_helpers",
  srcs = glob(["python-ce/helpers/**/*"]),
  visibility = ["//visibility:public"],
)
"""

http_archive(
    name = "python_2022_3",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "bfbde44928f446b2706df875bfa56cc64ee490875341c79aebba32ff7bb0f9d4",
    url = "https://plugins.jetbrains.com/files/7322/300704/python-ce-223.8836.26.zip",
)

http_archive(
    name = "python_2023_1",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "825c30d2cbcce405fd18fddf356eb1f425607e9c780f8eff95d21ac23f8d90fd",
    url = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/PythonCore/231.8770.65/PythonCore-231.8770.65.zip",
)

http_archive(
    name = "python_2023_2",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "e744349f353568c18a9e11ec5e3a205f62bbdc1b65c9abc96783c479fe2aa51b",
    url = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/PythonCore/232.9921.47/PythonCore-232.9921.47.zip",
)

_GO_BUILD_FILE = """
java_import(
    name = "go",
    jars = glob(["go*/lib/*.jar"]),
    visibility = ["//visibility:public"],
)
"""

http_archive(
    name = "go_2022_3",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "c762d2ff0253b8a996b8225344b345ae30b518dd20b3fd23bc1f417a90756c79",
    url = "https://plugins.jetbrains.com/files/9568/293630/go-plugin-223.8836.7.zip",
)

_SCALA_BUILD_FILE = """
java_import(
    name = "scala",
    jars = glob(["Scala/lib/*.jar"]),
    visibility = ["//visibility:public"],
)
"""

http_archive(
    name = "scala_2022_3",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "e3b49e40153f441a3e0f8bf28aa09fccf7b5bf92b58f53c72aa546d74bfeefbb",
    url = "https://plugins.jetbrains.com/files/1347/301506/scala-intellij-bin-2022.3.20.zip",
)

# The plugin api for android_studio_2022_2 android_studio. This is required to build ASwB and run integration tests
http_archive(
    name = "android_studio_2022_2",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio222",
    sha256 = "cdd852c4499b5f7402df44dfc69e8ca418ffc9a684caab34047476fd2cb24efc",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2022.2.1.18/android-studio-2022.2.1.18-linux.tar.gz",
)

# The plugin api for android_studio_dev android_studio. This is required to build ASwB and run integration tests
http_archive(
    name = "android_studio_dev",
    build_file = "@//intellij_platform_sdk:BUILD.android_studiodev",
    sha256 = "cb3f0494220f92dd85399adfb8655a1a2bd81b238d26e63a8bbd8bde95a0fccf",
    url = "https://android-build",
)

# The plugin api for android_studio_2023_2 android_studio. This is required to build ASwB and run integration tests
http_archive(
    name = "android_studio_2023_2",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio232",
    sha256 = "f2ccc445fb5c87525627ae81725241ab90d9707d577f5732563d3c5a49cba12f",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2023.2.1.14/android-studio-2023.2.1.14-linux.tar.gz",
)

# The plugin api for android_studio_2023_1 android_studio. This is required to build ASwB and run integration tests
http_archive(
    name = "android_studio_2023_1",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio231",
    sha256 = "22889528c5e261fd3ff0a183cbeef780bd99a51f348ac76efb8360565b89d11c",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2023.1.1.21/android-studio-2023.1.1.21-linux.tar.gz",
)

# The plugin api for android_studio_2022_3 android_studio. This is required to build ASwB and run integration tests
http_archive(
    name = "android_studio_2022_3",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio223",
    sha256 = "250625dcab183e0c68ebf12ef8a522af7369527d76f1efc704f93c05b02ffa9e",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2022.3.1.19/android-studio-2022.3.1.19-linux.tar.gz",
)

http_archive(
    name = "rules_java",
    sha256 = "7b0d9ba216c821ee8697dedc0f9d0a705959ace462a3885fe9ba0347ba950111",
    urls = [
        "https://github.com/bazelbuild/rules_java/releases/download/6.5.1/rules_java-6.5.1.tar.gz",
    ],
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

jvm_maven_import_external(
    name = "com_google_guava_guava",
    artifact = "com.google.guava:guava:31.1-jre",
    artifact_sha256 = "a42edc9cab792e39fe39bb94f3fca655ed157ff87a8af78e1d6ba5b07c4a00ab",
    server_urls = [
        "https://repo1.maven.org/maven2",
    ],
)

jvm_maven_import_external(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.9.1",
    artifact_sha256 = "378534e339e6e6d50b1736fb3abb76f1c15d1be3f4c13cec6d536412e23da603",
    server_urls = [
        "https://repo1.maven.org/maven2",
    ],
)

jvm_maven_import_external(
    name = "flogger",
    artifact = "com.google.flogger:flogger:0.7.4",
    artifact_sha256 = "77aac11b3c26e1e184dcfe79c55ac6e27967a6dfe1c04146125176940bc64a55",
    server_urls = [
        "https://repo1.maven.org/maven2",
    ],
)

jvm_maven_import_external(
    name = "flogger_system_backend",
    artifact = "com.google.flogger:flogger-system-backend:0.7.4",
    artifact_sha256 = "fd66f2615a9d8fe1b2274f1b5005a5555a0cd63cdfdab2ca9500e6eb81dc5f63",
    server_urls = [
        "https://repo1.maven.org/maven2",
    ],
)

http_archive(
    name = "build_bazel_rules_android",
    sha256 = "cd06d15dd8bb59926e4d65f9003bfc20f9da4b2519985c27e190cddc8b7a7806",
    strip_prefix = "rules_android-0.1.1",
    urls = ["https://github.com/bazelbuild/rules_android/archive/v0.1.1.zip"],
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
    name = "rules_python",
    sha256 = "ffc7b877c95413c82bfd5482c017edcf759a6250d8b24e82f41f3c8b8d9e287e",
    strip_prefix = "rules_python-0.19.0",
    url = "https://github.com/bazelbuild/rules_python/releases/download/0.19.0/rules_python-0.19.0.tar.gz",
)

load("@rules_python//python:repositories.bzl", "py_repositories")

py_repositories()

http_archive(
    name = "bazel_skylib",
    sha256 = "74d544d96f4a5bb630d465ca8bbcfe231e3594e5aae57e1edbf17a6eb3ca2506",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-skylib/releases/download/1.3.0/bazel-skylib-1.3.0.tar.gz",
        "https://github.com/bazelbuild/bazel-skylib/releases/download/1.3.0/bazel-skylib-1.3.0.tar.gz",
    ],
)

# specify a minimum version for bazel otherwise users on old versions may see
# unexpressive errors when new features are used
load("@bazel_skylib//lib:versions.bzl", "versions")

versions.check(minimum_bazel_version = "5.2.0")

load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")

bazel_skylib_workspace()

http_archive(
    name = "contrib_rules_bazel_integration_test",
    sha256 = "20d670bb614d311a2a0fc8af53760439214731c3d5be2d9b0a197dccc19583f5",
    strip_prefix = "rules_bazel_integration_test-0.9.0",
    urls = [
        "http://github.com/bazel-contrib/rules_bazel_integration_test/archive/v0.9.0.tar.gz",
    ],
)

load("@contrib_rules_bazel_integration_test//bazel_integration_test:deps.bzl", "bazel_integration_test_rules_dependencies")

bazel_integration_test_rules_dependencies()

load("@contrib_rules_bazel_integration_test//bazel_integration_test:defs.bzl", "bazel_binaries")

bazel_binaries(versions = [
    "4.0.0",
    "6.0.0",
])

# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "rules_proto",
    sha256 = "dc3fb206a2cb3441b485eb1e423165b231235a1ea9b031b4433cf7bc1fa460dd",
    strip_prefix = "rules_proto-5.3.0-21.7",
    urls = [
        "https://github.com/bazelbuild/rules_proto/archive/refs/tags/5.3.0-21.7.tar.gz",
    ],
)

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")

rules_proto_dependencies()

rules_proto_toolchains()

# LICENSE: The Apache Software License, Version 2.0
rules_scala_version = "a42f009ded929070d5c412284c50ba08f0f9e8b8"

http_archive(
    name = "io_bazel_rules_scala",
    sha256 = "0074836b631caaf552fd7013d49f18fa5f0a27c86bb1b88bd3fa9371fa36b2c9",
    strip_prefix = "rules_scala-%s" % rules_scala_version,
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version,
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
rules_kotlin_version = "1.7.0-RC-1"

rules_kotlin_sha = "68b910730026921814d3a504ccbe9adaac9938983d940e626523e6e4ecfb0355"

http_archive(
    name = "io_bazel_rules_kotlin",
    sha256 = rules_kotlin_sha,
    urls = ["https://github.com/bazelbuild/rules_kotlin/releases/download/v%s/rules_kotlin_release.tgz" % rules_kotlin_version],
)

load("@io_bazel_rules_kotlin//kotlin:repositories.bzl", "kotlin_repositories")

kotlin_repositories()

load("@io_bazel_rules_kotlin//kotlin:core.bzl", "kt_register_toolchains")

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
    artifact = "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.2",
    artifact_sha256 = "09aac136027678db2d3c2696696202719af9213ba17ae076f4c4421008885bcb",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

# Dependency needed for kotlin coroutines test library
jvm_maven_import_external(
    name = "kotlinx_coroutines_test",
    artifact = "org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.6.2",
    artifact_sha256 = "6eb5c29f60fcacde882b1d393bf9a2fe9535bece1c707396fdbd755559dc043d",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

http_archive(
    name = "io_bazel_rules_go",
    sha256 = "6b65cb7917b4d1709f9410ffe00ecf3e160edf674b78c54a894471320862184f",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.39.0/rules_go-v0.39.0.zip",
        "https://github.com/bazelbuild/rules_go/releases/download/v0.39.0/rules_go-v0.39.0.zip",
    ],
)

# needed for cpp tests
http_archive(
    name = "com_google_absl",
    sha256 = "987ce98f02eefbaf930d6e38ab16aa05737234d7afbab2d5c4ea7adbe50c28ed",
    strip_prefix = "abseil-cpp-20230802.1",
    urls = [
         "https://github.com/abseil/abseil-cpp/archive/refs/tags/20230802.1.tar.gz",
    ],
)

# gRPC Java
http_archive(
    name = "io_grpc_grpc_java",
    sha256 = "3bcf6be49fc7ab8187577a5211421258cb8e6d179f46023cc82e42e3a6188e51",
    strip_prefix = "grpc-java-1.59.0",
    url = "https://github.com/grpc/grpc-java/archive/refs/tags/v1.59.0.tar.gz",
)

jvm_maven_import_external(
    name = "io_netty_netty_common",
    artifact = "io.netty:netty-common:4.1.96.Final",
    artifact_sha256 = "da104e80db830922eaf860eb1c5e957cd1d124068253d02e9c7a7843bc66427a",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "io_netty_netty_transport",
    artifact = "io.netty:netty-transport:4.1.96.Final",
    artifact_sha256 = "8fe3afbe8b094a7b9f1eb27becf1cf017e5572343c1744d2b6040d5f331e84e3",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "io_netty_netty_transport_native_epoll",
    artifact = "io.netty:netty-transport-classes-epoll:4.1.96.Final",
    artifact_sha256 = "1591b3ea061932677dc2bab6cb7d82e8f1837a52b3c781f4daa99984ec87a9cd",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "io_netty_netty_transport_native_unix_common",
    artifact = "io.netty:netty-transport-native-unix-common:4.1.96.Final",
    artifact_sha256 = "4f96297a06a544a4cdb6fe6af8b868640f100fa96969e2196be216bd41adef13",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "io_netty_netty_transport_classes_kqueue",
    artifact = "io.netty:netty-transport-classes-kqueue:4.1.96.Final",
    artifact_sha256 = "f2f1fab3b297aee20a3922c79b548c8b4b72bb10b635375434c108ee05f29430",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

# io_grpc_grpc_java dependencies
load("@io_grpc_grpc_java//:repositories.bzl", "IO_GRPC_GRPC_JAVA_ARTIFACTS", "IO_GRPC_GRPC_JAVA_OVERRIDE_TARGETS", "grpc_java_repositories")

grpc_java_repositories()

# Java Maven-based repositories.
http_archive(
    name = "rules_jvm_external",
    sha256 = "cd1a77b7b02e8e008439ca76fd34f5b07aecb8c752961f9640dea15e9e5ba1ca",
    strip_prefix = "rules_jvm_external-4.2",
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/4.2.zip",
)

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")

rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")

rules_jvm_external_setup()

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = IO_GRPC_GRPC_JAVA_ARTIFACTS,
    generate_compat_repositories = True,
    override_targets = IO_GRPC_GRPC_JAVA_OVERRIDE_TARGETS,
    repositories = [
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
        "https://repository.mulesoft.org/nexus/content/repositories/public",
    ],
)

load("@maven//:compat.bzl", "compat_repositories")

compat_repositories()

# Register custom java 17 toolchain
register_toolchains("//:custom_java_17_toolchain_definition")

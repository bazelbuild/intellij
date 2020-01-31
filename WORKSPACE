workspace(name = "intellij_with_bazel")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for IntelliJ 2019.2. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2019_2",
    build_file = "@//intellij_platform_sdk:BUILD.idea192",
    sha256 = "fed481dfbd44a0717ab544c4c09c8bf5c037c50e39897551abeec81328cda9f7",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2019.2.4/ideaIC-2019.2.4.zip",
)

# The plugin api for IntelliJ 2019.3. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2019_3",
    build_file = "@//intellij_platform_sdk:BUILD.idea193",
    sha256 = "fb347c3c681328d11e87846950e8c5af6ac2c8d6a7e56946d3a10e6121d322f9",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2019.3.2/ideaIC-2019.3.2.zip",
)

# The plugin api for IntelliJ 2020.1. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2020_1",
    build_file = "@//intellij_platform_sdk:BUILD.idea201",
    sha256 = "396f0045a713f224015c082924fc5c71be0e029effa2fd7bd3fa65632a55a18c",
    url = "https://download.jetbrains.com/idea/ideaIC-201.4515.24.tar.gz",
)

# The plugin api for IntelliJ UE 2019.2. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2019_2",
    build_file = "@//intellij_platform_sdk:BUILD.ue192",
    sha256 = "cc6f68dedf6b0bc7b7d8f5be812b216605991ad5ab014147e0cd74cd2b9ea4ae",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2019.2.4/ideaIU-2019.2.4.zip",
)

# The plugin api for IntelliJ UE 2019.3. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2019_3",
    build_file = "@//intellij_platform_sdk:BUILD.ue193",
    sha256 = "d399ecc9357dd3b3ad3bcc1ed046f74b8e535bb862f68fd019b23fdc8ee2ca56",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2019.3.2/ideaIU-2019.3.2.zip",
)

# The plugin api for IntelliJ UE 2020.1. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2020_1",
    build_file = "@//intellij_platform_sdk:BUILD.ue201",
    sha256 = "0944d1b15e6d8d8c13e33ed0bb43773bf7a2cd2100a679c2a668d543a7931de8",
    url = "https://download-cf.jetbrains.com/idea/ideaIU-201.4515.24.tar.gz",
)

# The plugin api for CLion 2019.2. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2019_2",
    build_file = "@//intellij_platform_sdk:BUILD.clion",
    sha256 = "aedec47a538c9a2b654c30719ed7f0111af0a708a2aeb2a9f36a1c0767841a5c",
    url = "https://download.jetbrains.com/cpp/CLion-2019.2.5.tar.gz",
)

# The prerelease plugin api for CLion 2019.3. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2019_3",
    build_file = "@//intellij_platform_sdk:BUILD.clion193",
    sha256 = "3c12a827bedf5ecc0d6c9aaf6822bb849f53798e5fd48e3f7ef975b4aa5976b7",
    url = "https://download.jetbrains.com/cpp/CLion-2019.3.3.tar.gz",
)

# Python plugin for Android Studio 3.5. Required at compile-time for python-specific features.
http_archive(
    name = "python_2019_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "10ccbc5f9ca38f9f9516d48c976d7b1001ff58538865130bcb51ec925c8b22f5",
    url = "https://plugins.jetbrains.com/files/7322/65660/python-ce-2019.1.191.8026.25.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2019_2",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "c0d970d4b8034fbe1a1c705a59e2d6321ec032ae38c65535493dc1ec5c8aeec5",
    url = "https://plugins.jetbrains.com/files/7322/66012/python-ce.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2019_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "82d72e875ce5f127f75af50f14db922a8228c1fcc38ab3dcf2d0011a72e1bf10",
    url = "https://plugins.jetbrains.com/files/7322/76344/python-ce.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2020_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "e6a1ca5936e910fbae27262fb8e9bab98a6cedf178ae1325a7835d89dccc679f",
    url = "https://plugins.jetbrains.com/files/7322/76642/python-ce.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2019_2",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'go',",
        "    jars = glob(['intellij-go/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "c19195a5979a0d5361ab9d1e82beeeb40c6f9eebaae504d07790a9f3afee4478",
    url = "https://plugins.jetbrains.com/files/9568/68228/intellij-go-192.6603.23.335.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2019_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'go',",
        "    jars = glob(['intellij-go/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "9474152d52147dce02bb3b79d76d600afa03373458d6df525d820930a849fc1c",
    url = "https://plugins.jetbrains.com/files/9568/75063/intellij-go-193.5662.53.103.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2020_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'go',",
        "    jars = glob(['intellij-go/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "c63475f156d6757d3a155876308fabd83a3f0380242aff748495796655cb9169",
    url = "https://plugins.jetbrains.com/files/9568/76993/intellij-go-201.4515.17.504.zip",
)

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2019_2",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = glob(['Scala/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "b895f9d46f4cc01bd040e6319574fad85bdc287366d4073e130ef7df49199aaa",
    url = "https://plugins.jetbrains.com/files/1347/66462/scala-intellij-bin-2019.2.15.zip",
)

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2019_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = glob(['Scala/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "94779905396c2d3a2d5693aece5f4845e5675e7a6e0102313e88d1afd5b16ffb",
    url = "https://plugins.jetbrains.com/files/1347/75234/scala-intellij-bin-2019.3.23.zip",
)

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2020_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = glob(['Scala/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "bbc019e7cde3baf21b11f0abde21f42d4606ca5ca9100b3bdace3292f70cceef",
    url = "https://plugins.jetbrains.com/files/1347/76628/scala-intellij-bin-2020.1.7.zip",
)

# The plugin api for Android Studio 3.5. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_3_5",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio",
    sha256 = "94fc392a148480a67299d83c1faaabc56db27188194748433534cf8b5ca4dd29",
    url = "https://dl.google.com/dl/android/studio/ide-zips/3.5.1.0/android-studio-ide-191.5900203-linux.tar.gz",
)

http_archive(
    name = "android_studio_3_6",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio36",
    sha256 = "e58fd42f0502ba93cdb044977aa31c2d4a49265be52d62e3775781f5a1b387b4",
    url = "https://dl.google.com/dl/android/studio/ide-zips/3.6.0.19/android-studio-ide-192.6165589-linux.tar.gz",
)

http_archive(
    name = "android_studio_4_0",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio40",
    sha256 = "1827a9ee3beb724fb4365b1811f7f9e1d7de4f31e91a9300f4544a51ae5cb12d",
    url = "https://dl.google.com/dl/android/studio/ide-zips/4.0.0.8/android-studio-ide-193.6107147-linux.tar.gz",
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
    artifact = "org.mockito:mockito-core:3.2.4",
    artifact_sha256 = "587dcc78914585a7ba7933417237dfcf4d4a1a295d1aa8091d732a76a1d8fe92",
    licenses = ["notice"],  # MIT
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:3.1",
    artifact_sha256 = "cdb3d038c188de6f46ffd5cd930be2d5e5dba59c53b26437995d534e3db2fb80",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "jarjar",
    artifact = "com.googlecode.jarjar:jarjar:1.3",
    artifact_sha256 = "4225c8ee1bf3079c4b07c76fe03c3e28809a22204db6249c9417efa4f804b3a7",
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

http_archive(
    name = "bazel_skylib",
    sha256 = "2ef429f5d7ce7111263289644d233707dba35e39696377ebab8b0bc701f7818e",
    url = "https://github.com/bazelbuild/bazel-skylib/releases/download/0.8.0/bazel-skylib.0.8.0.tar.gz",
)

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
# proto_library rules implicitly depend on @com_google_protobuf//:protoc
http_archive(
    name = "com_google_protobuf",
    sha256 = "d82eb0141ad18e98de47ed7ed415daabead6d5d1bef1b8cccb6aa4d108a9008f",
    strip_prefix = "protobuf-b4f193788c9f0f05d7e0879ea96cd738630e5d51",
    # Commit from 2019-05-15, update to protobuf 3.8 when available.
    url = "https://github.com/protocolbuffers/protobuf/archive/b4f193788c9f0f05d7e0879ea96cd738630e5d51.tar.gz",
)

load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")

protobuf_deps()

# LICENSE: The Apache Software License, Version 2.0
# java_proto_library rules implicitly depend on @com_google_protobuf_java//:java_toolchain
# It's the same repository as above, but there's no way to alias them at the moment (and both are
# required).
http_archive(
    name = "com_google_protobuf_java",
    sha256 = "d82eb0141ad18e98de47ed7ed415daabead6d5d1bef1b8cccb6aa4d108a9008f",
    strip_prefix = "protobuf-b4f193788c9f0f05d7e0879ea96cd738630e5d51",
    url = "https://github.com/protocolbuffers/protobuf/archive/b4f193788c9f0f05d7e0879ea96cd738630e5d51.tar.gz",
)

# BEGIN-EXTERNAL-SCALA
# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "io_bazel_rules_scala",
    sha256 = "b8b18d0fe3f6c3401b4f83f78f536b24c7fb8b92c593c1dcbcd01cc2b3e85c9a",
    strip_prefix = "rules_scala-a676633dc14d8239569affb2acafbef255df3480",
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/a676633dc14d8239569affb2acafbef255df3480.zip",
)

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")

scala_repositories()

load("@io_bazel_rules_scala//scala:toolchains.bzl", "scala_register_toolchains")

scala_register_toolchains()
# END-EXTERNAL-SCALA

# BEGIN-EXTERNAL-KOTLIN
# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "io_bazel_rules_kotlin",
    sha256 = "f1293902a15397a10957e866f133dcd027a0a6d21eae8c4fb7557f010add8a09",
    strip_prefix = "rules_kotlin-cab5eaffc2012dfe46260c03d6419c0d2fa10be0",
    type = "zip",
    url = "https://github.com/bazelbuild/rules_kotlin/archive/cab5eaffc2012dfe46260c03d6419c0d2fa10be0.zip",
)

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kotlin_repositories")

kotlin_repositories()
# END-EXTERNAL-KOTLIN

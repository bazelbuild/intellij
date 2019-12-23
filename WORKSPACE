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
    sha256 = "5bd69fc7588f0845facc7a0cf6c6b55d63217b19d8687e3cc6d7405e24a0db79",
    strip_prefix = "idea-IC-193.4386.10",
    url = "https://download.jetbrains.com/idea/ideaIC-193.4386.10.tar.gz",
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
    sha256 = "5bbaeaa580ae38622b8c6e9188551726f7d82fccde8ef1743cb141d902235e66",
    strip_prefix = "idea-IU-193.4386.10",
    url = "https://download.jetbrains.com/idea/ideaIU-193.4386.10.tar.gz",
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
    sha256 = "1c9bdeb55dda997a6cfce84ce2dbe2951f117709f9d37a3b65cfc2a68a359ecd",
    url = "https://download.jetbrains.com/cpp/CLion-2019.3.1.tar.gz",
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

# Python plugin for IntelliJ CE 2019.2. Required at compile-time for python-specific features.
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

# Python plugin for IntelliJ CE 2019.3. Required at compile-time for python-specific features.
http_archive(
    name = "python_2019_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "21b2bd88c594bc58d8e8062c845be3bee965fc4dff2da9521158a6da3ab5b825",
    url = "https://plugins.jetbrains.com/files/7322/70397/python-ce.zip",
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
    sha256 = "b80126de5f2011e506943cbc1959f2af14d206a000812ee67ffef3c2d59380ef",
    url = "https://plugins.jetbrains.com/files/9568/70301/intellij-go-193.4386.1.538.zip",
)

# Scala plugin for IntelliJ CE 2019.2. Required at compile-time for scala-specific features.
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

# Scala plugin for IntelliJ CE 2019.3. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2019_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = glob(['Scala/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "b945bcb8bf4a029c42230893b41587d408101370a663e050302275919cf015f3",
    url = "https://plugins.jetbrains.com/files/1347/70287/scala-intellij-bin-2019.3.7.zip",
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
    sha256 = "a91527184c2261931792f951c4d23a96b00899502a449b6b41a538a38e6b9962",
    url = "https://dl.google.com/dl/android/studio/ide-zips/3.6.0.17/android-studio-ide-192.6018865-linux.tar.gz",
)

http_archive(
    name = "android_studio_4_0",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio40",
    sha256 = "1272b2007d065dd4951c72e3c902613ffec9040175ecb1665355432f20df616e",
    url = "https://dl.google.com/dl/android/studio/ide-zips/4.0.0.7/android-studio-ide-193.6085562-linux.tar.gz",
)

# LICENSE: Common Public License 1.0
jvm_maven_import_external(
    name = "junit",
    artifact = "junit:junit:4.12",
    artifact_sha256 = "59721f0805e223d84b90677887d9ff567dc534d7c502ca903c0c2b17f05c116a",
    licenses = ["notice"],  # Common Public License 1.0
    server_urls = ["http://central.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "jsr305_annotations",
    artifact = "com.google.code.findbugs:jsr305:3.0.2",
    artifact_sha256 = "766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["http://central.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "truth",
    artifact = "com.google.truth:truth:0.42",
    artifact_sha256 = "dd652bdf0c4427c59848ac0340fd6b6d20c2cbfaa3c569a8366604dbcda5214c",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["http://central.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "truth8",
    artifact = "com.google.truth.extensions:truth-java8-extension:0.42",
    artifact_sha256 = "cf9e095a6763bc33633b8844c3ebadffe3b082c81dd97a4d79b64ad88d305bc1",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["http://central.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "mockito",
    artifact = "org.mockito:mockito-core:1.10.19",
    artifact_sha256 = "d5831ee4f71055800821a34a3051cf1ed5b3702f295ffebd50f65fb5d81a71b8",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["http://central.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:1.3",
    artifact_sha256 = "dd4ef3d3091063a4fec578cbb2bbe6c1f921c00091ba2993dcd9afd25ff9444a",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["http://central.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "jarjar",
    artifact = "com.googlecode.jarjar:jarjar:1.3",
    artifact_sha256 = "4225c8ee1bf3079c4b07c76fe03c3e28809a22204db6249c9417efa4f804b3a7",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["http://central.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "auto_value",
    artifact = "com.google.auto.value:auto-value:1.6.2",
    artifact_sha256 = "edbe65a5c53e3d4f5cb10b055d4884ae7705a7cd697be4b2a5d8427761b8ba12",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["http://central.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "auto_value_annotations",
    artifact = "com.google.auto.value:auto-value-annotations:1.6.2",
    artifact_sha256 = "b48b04ddba40e8ac33bf036f06fc43995fc5084bd94bdaace807ce27d3bea3fb",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["http://central.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "error_prone_annotations",
    artifact = "com.google.errorprone:error_prone_annotations:2.3.0",
    artifact_sha256 = "524b43ea15ca97c68f10d5f417c4068dc88144b620d2203f0910441a769fd42f",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["http://central.maven.org/maven2"],
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
    sha256 = "ccd4693c5f5a302b8c145ce87792c6fca1cb956b94c15b569ddd0809f5f617d1",
    strip_prefix = "rules_scala-96176ae9e0be8582918a5212a7d4b44d885db8f6",
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/96176ae9e0be8582918a5212a7d4b44d885db8f6.zip",
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

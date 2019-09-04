workspace(name = "intellij_with_bazel")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for IntelliJ 2019.1. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2019_1",
    build_file = "@//intellij_platform_sdk:BUILD.idea",
    sha256 = "e045751adabe2837203798270e1dc173128fe3e607e3025d4f8110c7ed4cc499",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2019.1.2/ideaIC-2019.1.2.zip",
)

# The plugin api for IntelliJ 2019.2. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2019_2",
    build_file = "@//intellij_platform_sdk:BUILD.idea192",
    sha256 = "9567f2a88c9d4c4a0495208914f07bd2dace78dad0fee31fb9f8a4adab3cc437",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2019.2/ideaIC-2019.2.zip",
)

# The plugin api for IntelliJ UE 2019.1. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2019_1",
    build_file = "@//intellij_platform_sdk:BUILD.ue",
    sha256 = "df6a1e6fbf77578b47163b96c83bc90a05bf043847c6e7c0bf285fe2e77d71e4",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2019.1.2/ideaIU-2019.1.2.zip",
)

# The plugin api for IntelliJ UE 2019.2. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2019_2",
    build_file = "@//intellij_platform_sdk:BUILD.ue192",
    sha256 = "c1a980c6eeb528ee731ed52a5821981466b9205713926748051ff08a4ce8cfaf",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2019.2/ideaIU-2019.2.zip",
)

# The plugin api for CLion 2019.1. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2019_1",
    build_file = "@//intellij_platform_sdk:BUILD.clion",
    sha256 = "6453a526fc832b3493338d5c53d976022daa6d20fb2c7e2012b440f1a8e7d313",
    url = "https://download.jetbrains.com/cpp/CLion-2019.1.3.tar.gz",
)

# The plugin api for CLion 2019.2. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2019_2",
    build_file = "@//intellij_platform_sdk:BUILD.clion",
    sha256 = "978a13bc4e9d5ca35379a1eed8493890e3365873d7aad358fa9c4efaf760c28f",
    url = "https://download.jetbrains.com/cpp/CLion-2019.2.1.tar.gz",
)

# Python plugin for IntelliJ CE 2019.1. Required at compile-time for python-specific features.
http_archive(
    name = "python_2019_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "378002fa79623341a31bd3ac003506f04ac950d43313c8d413c6f0763826eadd",
    url = "https://plugins.jetbrains.com/files/7322/60398/python-ce-2019.1.191.6707.7.zip",
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

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2019_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'go',",
        "    jars = glob(['intellij-go/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "815f59dcd5f7db019e224cdb85e67db99c8d5deb99721a73a36f710bda64be49",
    url = "https://plugins.jetbrains.com/files/9568/62411/intellij-go-191.7141.44.205.zip",
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
    sha256 = "704593665da45a6ad1dcd86f53d3959c6b0803dc0b0773b1b23864e48c9af289",
    url = "https://plugins.jetbrains.com/files/9568/66084/intellij-go-192.5728.86.268.zip",
)

# Scala plugin for IntelliJ CE 2019.1. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2019_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = glob(['Scala/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "e26f00ff697f30defb1d1968805cc3c95a31d0b1bef428cfc202ebd3e0c1076c",
    url = "https://plugins.jetbrains.com/files/1347/59108/scala-intellij-bin-2019.1.2.zip",
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

# The plugin api for Android Studio 3.5. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_3_5",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio",
    sha256 = "5794fd6edca6e4daa31f5ed710670d0b425534549a6c4aa2e3e9753ae116736f",
    url = "https://dl.google.com/dl/android/studio/ide-zips/3.5.0.21/android-studio-ide-191.5791312-linux.tar.gz",
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
    sha256 = "490554b98da4ce6e3e1e074e01b81e8440b760d4f086fccf50085a25528bf5cd",
    strip_prefix = "bazel-integration-testing-922d2b04bfb9721ab14ff6d26d4a8a6ab847aa07",
    url = "https://github.com/bazelbuild/bazel-integration-testing/archive/922d2b04bfb9721ab14ff6d26d4a8a6ab847aa07.zip",
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

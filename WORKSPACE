workspace(name = "intellij_with_bazel")

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for IntelliJ 2018.3. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2018_3",
    build_file = "@//intellij_platform_sdk:BUILD.idea",
    sha256 = "b2390d64269ca4d4a7fcd856a29e5b2d1a81f40451f135b092f9f97f046b8f35",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2018.3.6/ideaIC-2018.3.6.zip",
)

# The plugin api for IntelliJ 2019.1. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2019_1",
    build_file = "@//intellij_platform_sdk:BUILD.idea",
    sha256 = "d05abcfd50673c542558bcbd2c4853386e1e45b72759b8ed9bd374b87dc3a391",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2019.1.1/ideaIC-2019.1.1.zip",
)

# The plugin api for IntelliJ UE 2018.3. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2018_3",
    build_file = "@//intellij_platform_sdk:BUILD.ue",
    sha256 = "727882a57673636bc4333e670e7b964869775fffc2275de60001d691fe63e1c0",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2018.3.6/ideaIU-2018.3.6.zip",
)

# The plugin api for IntelliJ UE 2019.1. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2019_1",
    build_file = "@//intellij_platform_sdk:BUILD.ue",
    sha256 = "ef1d578bdbcc0a61607c096057822bd2d1a00319daa5ef4550934c7ca7f6ba4d",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2019.1.1/ideaIU-2019.1.1.zip",
)

# The plugin api for CLion 2018.3. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2018_3",
    build_file = "@//intellij_platform_sdk:BUILD.clion",
    sha256 = "963fb343272e5903ac7dc944cc64ea9541ab4c150cc4ea796dcb0fb613bff4fd",
    url = "https://download.jetbrains.com/cpp/CLion-2018.3.4.tar.gz",
)

# The plugin api for CLion 2019.1. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2019_1",
    build_file = "@//intellij_platform_sdk:BUILD.clion",
    sha256 = "f2779475f33ad922019673d66824ba9c6adbe349ed8711350a8f9e65b6b598e6",
    url = "https://download.jetbrains.com/cpp/CLion-2019.1.tar.gz",
)

# Python plugin for IntelliJ CE 2018.3. Required at compile-time for python-specific features.
http_archive(
    name = "python_2018_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "095a2258f1707a8a1cd3c77f7c249d30f06cca2ca2738edba6c8befd92c0f763",
    url = "https://plugins.jetbrains.com/files/7322/58209/python-ce-2018.3.183.5912.2.zip",
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

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2018_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'go',",
        "    jars = glob(['intellij-go/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "c34730f35c51563a1cf057615a75350b09e38bd8889bc06d9c71599aa4d7e0c1",
    url = "https://plugins.jetbrains.com/files/9568/59090/intellij-go-183.5912.21.1625.zip",
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
    sha256 = "6d80ede63ad301121a72b8b4ef93157ec3f546d23146234660587c9699eb0bf4",
    url = "https://plugins.jetbrains.com/files/9568/59092/intellij-go-191.6014.8.104.zip",
)

# Scala plugin for IntelliJ CE 2018.3. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2018_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = glob(['Scala/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "18fb1241944744d7c0f68f2b2bf1b963494a72b4ec686eb9cdeba3965b48a524",
    url = "https://plugins.jetbrains.com/files/1347/57615/scala-intellij-bin-2018.3.6.zip",
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

http_archive(
    name = "android_studio_3_4",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio",
    sha256 = "ad2bd4be87a55cfaeee6f28d40d925691314c11862b303d411f9776b76fa1c45",
    url = "https://dl.google.com/dl/android/studio/ide-zips/3.4.0.18/android-studio-ide-183.5452501-linux.tar.gz",
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
    artifact = "com.google.truth:truth:0.30",
    artifact_sha256 = "f4a4c5e69c4994b750ce3ee80adbb2b7150fe39f057d7dff89832c8ca3af512e",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["http://central.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "mockito",
    artifact = "org.mockito:mockito-all:1.9.5",
    artifact_sha256 = "b2a63307d1dce3aa1623fdaacb2327a4cd7795b0066f31bf542b1e8f2683239e",
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

# LICENSE: The Apache Software License, Version 2.0
# proto_library rules implicitly depend on @com_google_protobuf//:protoc
http_archive(
    name = "com_google_protobuf",
    sha256 = "9510dd2afc29e7245e9e884336f848c8a6600a14ae726adb6befdb4f786f0be2",
    strip_prefix = "protobuf-3.6.1.3",
    urls = ["https://github.com/protocolbuffers/protobuf/archive/v3.6.1.3.zip"],
)

# LICENSE: The Apache Software License, Version 2.0
# java_proto_library rules implicitly depend on @com_google_protobuf_java//:java_toolchain
# It's the same repository as above, but there's no way to alias them at the moment (and both are
# required).
http_archive(
    name = "com_google_protobuf_java",
    sha256 = "9510dd2afc29e7245e9e884336f848c8a6600a14ae726adb6befdb4f786f0be2",
    strip_prefix = "protobuf-3.6.1.3",
    urls = ["https://github.com/protocolbuffers/protobuf/archive/v3.6.1.3.zip"],
)

# BEGIN-EXTERNAL-SCALA
# LICENSE: The Apache Software License, Version 2.0
git_repository(
    name = "io_bazel_rules_scala",
    commit = "7bc18d07001cbfd425c6761c8384c4e982d25a2b",
    remote = "https://github.com/bazelbuild/rules_scala.git",
)

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")

scala_repositories()

load("@io_bazel_rules_scala//scala:toolchains.bzl", "scala_register_toolchains")

scala_register_toolchains()
# END-EXTERNAL-SCALA

# BEGIN-EXTERNAL-KOTLIN
# LICENSE: The Apache Software License, Version 2.0
git_repository(
    name = "io_bazel_rules_kotlin",
    commit = "cab5eaffc2012dfe46260c03d6419c0d2fa10be0",
    remote = "https://github.com/bazelbuild/rules_kotlin.git",
)

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kotlin_repositories")

kotlin_repositories()
# END-EXTERNAL-KOTLIN

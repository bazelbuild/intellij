workspace(name = "intellij_with_bazel")

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for IntelliJ 2018.1. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2018_1",
    build_file = "@//intellij_platform_sdk:BUILD.idea",
    sha256 = "e9b1d9175e25fbfc98c4dc89d8864cd1b447fc62f8302bd64a5d221d152b4da8",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2018.1.6/ideaIC-2018.1.6.zip",
)

# The plugin api for IntelliJ 2018.2. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2018_2",
    build_file = "@//intellij_platform_sdk:BUILD.idea",
    sha256 = "0f96916341103b5e6522ff1050cea376f6d4b594c7c8caff0f4c8e3ed636678d",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2018.2.5/ideaIC-2018.2.5.zip",
)

# The plugin api for IntelliJ 2018.3. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2018_3",
    build_file = "@//intellij_platform_sdk:BUILD.idea",
    sha256 = "7dd93fbc5e3d0e6e78583315f7b917580e87e4cca65de085561239c7be4bd01d",
    url = "https://download-cf.jetbrains.com/idea/ideaIC-183.3975.18.tar.gz",
)

# The plugin api for IntelliJ UE 2018.1. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2018_1",
    build_file = "@//intellij_platform_sdk:BUILD.ue",
    sha256 = "aef4ef3a96405d67f1f87ccf3de93e1ac7dd38111ee7b3fa3ae1dd22e5e9e750",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2018.1.6/ideaIU-2018.1.6.zip",
)

# The plugin api for IntelliJ UE 2018.2. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2018_2",
    build_file = "@//intellij_platform_sdk:BUILD.ue",
    sha256 = "3e2a596775ed4de33da93bd930d7cd00f44fa3a2de6beeaec278146769295632",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2018.2.5/ideaIU-2018.2.5.zip",
)

# The plugin api for IntelliJ UE 2018.3. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2018_3",
    build_file = "@//intellij_platform_sdk:BUILD.ue",
    sha256 = "a0c7dd9e1807646ba42b3e36eb6b8897e94fecb23a4645ace973518022468201",
    url = "https://download-cf.jetbrains.com/idea/ideaIU-183.3975.18.tar.gz",
)

# The plugin api for CLion 2018.1. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2018_1",
    build_file = "@//intellij_platform_sdk:BUILD.clion",
    sha256 = "f861409f421728e06280808208a06f79bdda8a437c91e576db0d7ed1e83ac7f5",
    url = "https://download.jetbrains.com/cpp/CLion-2018.1.6.tar.gz",
)

# The plugin api for CLion 2018.2. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2018_2",
    build_file = "@//intellij_platform_sdk:BUILD.clion",
    sha256 = "d284345ae11224c4c29ab8dfcb516cf6e43958a5cf9c902dea110d28fde32b2f",
    url = "https://download.jetbrains.com/cpp/CLion-2018.2.5.tar.gz",
)

# The plugin api for Android Studio 3.2. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_3_2",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio",
    sha256 = "b9ec0d44f2feaafe1e3fbd1ed696bf325f9e05cfb6c1ace84dbf87ae249efa84",
    url = "https://dl.google.com/android/studio/ide-zips/3.2.1.0/android-studio-ide-181.5056338-linux.zip",
)

# Python plugin for IntelliJ CE 2018.1. Required at compile-time for python-specific features.
http_archive(
    name = "python_2018_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "17f070bb346675e1f743ba6311e6bb426ad61ff693b9bd77fbded357f65234ba",
    url = "https://plugins.jetbrains.com/files/7322/44945/python-ce-2018.1.181.4445.78.zip",
)

# Python plugin for IntelliJ CE 2018.2. Required at compile-time for python-specific features.
http_archive(
    name = "python_2018_2",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "863d8da8a6e1d2589178ed2ff657d935ed2536d26bde5ebd7785ca16ce0b3093",
    url = "https://plugins.jetbrains.com/files/7322/48707/python-ce-2018.2.182.3911.36.zip",
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
    sha256 = "346898238e3cab9d062407d4837a84315ddeb8a6ee981af7678571e77118cf37",
    url = "https://plugins.jetbrains.com/files/7322/50178/python-ce-2018.3.183.2635.13.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2018_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'go',",
        "    jars = glob(['intellij-go/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "de69ac9f8b81119c4a136860b66730d1e0dd89f713ac182bd8d5fdce801a60e5",
    url = "https://plugins.jetbrains.com/files/9568/44888/intellij-go-181.4445.53.182.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2018_2",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'go',",
        "    jars = glob(['intellij-go/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "7e974ae50372dd81e3fae3f5cb5256fedee158502ab785dd77882807b56d2bda",
    url = "https://plugins.jetbrains.com/files/9568/48153/intellij-go-182.3684.111.849.zip",
)

# Scala plugin for IntelliJ CE 2018.1. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2018_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = glob(['Scala/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "76b052473be73a68ab6ea80b81ba47dcab0412da3f213a9d22e703e49ff7a39a",
    url = "https://plugins.jetbrains.com/files/1347/44474/scala-intellij-bin-2018.1.8.zip",
)

# Scala plugin for IntelliJ CE 2018.2. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2018_2",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = glob(['Scala/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "fc8faf74c6bf63303ab1b62bade4aae43b1d77ad5bc1d4a578aeae33c23d7b78",
    url = "https://plugins.jetbrains.com/files/1347/48884/scala-intellij-bin-2018.2.10.zip",
)

http_archive(
    name = "android_studio_3_3",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio",
    sha256 = "16ee55dca426fd0aaee802561ed25656446aad9ea8001aa8b373f5ef10285c82",
    url = "https://dl.google.com/android/studio/ide-zips/3.3.0.14/android-studio-ide-182.5078385-linux.zip",
)

# LICENSE: Common Public License 1.0
maven_jar(
    name = "junit",
    artifact = "junit:junit:4.12",
    sha1 = "2973d150c0dc1fefe998f834810d68f278ea58ec",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "jsr305_annotations",
    artifact = "com.google.code.findbugs:jsr305:3.0.2",
    sha1 = "25ea2e8b0c338a877313bd4672d3fe056ea78f0d",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "truth",
    artifact = "com.google.truth:truth:0.30",
    sha1 = "9d591b5a66eda81f0b88cf1c748ab8853d99b18b",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "mockito",
    artifact = "org.mockito:mockito-all:1.9.5",
    sha1 = "79a8984096fc6591c1e3690e07d41be506356fa5",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:1.3",
    sha1 = "dc13ae4faca6df981fc7aeb5a522d9db446d5d50",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "jarjar",
    artifact = "com.googlecode.jarjar:jarjar:1.3",
    sha1 = "b81c2719c63fa8e6f3eca5b11b8e9b5ad79463db",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "auto_value",
    artifact = "com.google.auto.value:auto-value:1.6",
    sha1 = "a3b1b1404f8acaa88594a017185e013cd342c9a8",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "auto_value_annotations",
    artifact = "com.google.auto.value:auto-value-annotations:1.6",
    sha1 = "da725083ee79fdcd86d9f3d8a76e38174a01892a",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "error_prone_annotations",
    artifact = "com.google.errorprone:error_prone_annotations:2.3.0",
    sha1 = "dc72efd247e1c8489df04af8a5451237698e6380",
)

# LICENSE: The Apache Software License, Version 2.0
# proto_library rules implicitly depend on @com_google_protobuf//:protoc
http_archive(
    name = "com_google_protobuf",
    sha256 = "e514c2e613dc47c062ea8df480efeec368ffbef98af0437ac00cdaadcb0d80d2",
    strip_prefix = "protobuf-3.6.0",
    urls = ["https://github.com/google/protobuf/archive/v3.6.0.zip"],
)

# LICENSE: The Apache Software License, Version 2.0
# java_proto_library rules implicitly depend on @com_google_protobuf_java//:java_toolchain
# It's the same repository as above, but there's no way to alias them at the moment (and both are
# required).
http_archive(
    name = "com_google_protobuf_java",
    sha256 = "e514c2e613dc47c062ea8df480efeec368ffbef98af0437ac00cdaadcb0d80d2",
    strip_prefix = "protobuf-3.6.0",
    urls = ["https://github.com/google/protobuf/archive/v3.6.0.zip"],
)

# BEGIN-EXTERNAL-SCALA
# LICENSE: The Apache Software License, Version 2.0
git_repository(
    name = "io_bazel_rules_scala",
    commit = "8359fc6781cf3102e918c84cb1638a1b1e050ce0",
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
    commit = "6d8dcd4d6000d0cf3321eb8580d8fc67f8731f8e",
    remote = "https://github.com/bazelbuild/rules_kotlin.git",
)

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kotlin_repositories")

kotlin_repositories()
# END-EXTERNAL-KOTLIN

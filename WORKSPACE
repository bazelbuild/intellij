workspace(name = "intellij_with_bazel")

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for IntelliJ 2018.2. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2018_2",
    build_file = "@//intellij_platform_sdk:BUILD.idea",
    sha256 = "c05166d1766f368a942239efee8f275fa68e3ecc462f0bda1e0635d47bc31e32",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2018.2.6/ideaIC-2018.2.6.zip",
)

# The plugin api for IntelliJ 2018.3. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2018_3",
    build_file = "@//intellij_platform_sdk:BUILD.idea",
    sha256 = "d7c8e0c9cd858cc9a09e7046e2c6283f01955d4d8f46a9edfa0c495059888e4f",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2018.3/ideaIC-2018.3.zip",
)

# The plugin api for IntelliJ UE 2018.2. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2018_2",
    build_file = "@//intellij_platform_sdk:BUILD.ue",
    sha256 = "96a440e11fa617b8ed26322265c2c77edaef94e7838a6e4a51be95c00b69c042",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2018.2.6/ideaIU-2018.2.6.zip",
)

# The plugin api for IntelliJ UE 2018.3. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2018_3",
    build_file = "@//intellij_platform_sdk:BUILD.ue",
    sha256 = "219159cf37a5f9d7b808007c92c55fd8383fa76bea1a83d8c0c6b63d03d6b93d",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2018.3/ideaIU-2018.3.zip",
)

# The plugin api for CLion 2018.2. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2018_2",
    build_file = "@//intellij_platform_sdk:BUILD.clion",
    sha256 = "2e1742c6769cceb806acedaffeaf764cdf5990d7dbd0165741400e788d1af5d5",
    url = "https://download.jetbrains.com/cpp/CLion-2018.2.6.tar.gz",
)

# The plugin api for CLion 2018.3. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2018_3",
    build_file = "@//intellij_platform_sdk:BUILD.clion",
    sha256 = "111fa549c11468c663d123761c7e44f7726b2d5fe16619f6c6ab5e9578add966",
    url = "https://download.jetbrains.com/cpp/CLion-183.3975.20.tar.gz",
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
    sha256 = "1dc6b34e20ca8bc18107162564d72347b4fb57e20f82eda45a9deefa98b9b533",
    url = "https://plugins.jetbrains.com/files/9568/51834/intellij-go-183.4284.36.1532.zip",
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
    sha256 = "37df62f82f3673950a6175232c9161d39b04ccfcd70ba651afddf0c5a1a3c935",
    url = "https://plugins.jetbrains.com/files/1347/50892/scala-intellij-bin-2018.3.2.zip",
)

http_archive(
    name = "android_studio_3_3",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio",
    sha256 = "670936864a2a3337879c287bda7a36823f513b260d14f9dce6933f428d29ec2a",
    url = "https://dl.google.com/android/studio/ide-zips/3.3.0.17/android-studio-ide-182.5138683-linux.zip",
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

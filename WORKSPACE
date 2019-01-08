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
    sha256 = "76b3f12c5810ec791c49e018ace4f3d350856dc400b8dfdb708ca5c74ddaba2d",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2018.3.2/ideaIC-2018.3.2.zip",
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
    sha256 = "b5be15746578158b0996996e259694ea80e900da77d3d5e6a49c41974b8e19f0",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2018.3.2/ideaIU-2018.3.2.zip",
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
    sha256 = "b42557e2b09383121a4347fc74c1f903fe08607ef0f3ceb279dd20c519e583e5",
    url = "https://download.jetbrains.com/cpp/CLion-2018.3.2.tar.gz",
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
    sha256 = "15969495651d8fcb6e3ae7ea33ef1b15ed62a7a929b016f6ec389a217fdad1d6",
    url = "https://plugins.jetbrains.com/files/7322/52274/python-ce-2018.3.183.4284.148.zip",
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
    sha256 = "3bcab5174d20363cd3f91302b32d0e9d3114533397d70ed03084e5ac8dbe5d66",
    url = "https://plugins.jetbrains.com/files/9568/52280/intellij-go-183.4284.148.1556.zip",
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
    sha256 = "1e62d69c5e9e97c9ff04af5c0edabf9d95126b155ceb8f1a9398b028fb2414e7",
    url = "https://dl.google.com/android/studio/ide-zips/3.3.0.19/android-studio-ide-182.5183351-linux.zip",
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
    artifact = "com.google.auto.value:auto-value:1.6.2",
    sha1 = "e7eae562942315a983eea3e191b72d755c153620",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "auto_value_annotations",
    artifact = "com.google.auto.value:auto-value-annotations:1.6.2",
    sha1 = "ed193d86e0af90cc2342aedbe73c5d86b03fa09b",
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
    commit = "326b4ce252c36aeff2232e241ff4bfd8d6f6e071",
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

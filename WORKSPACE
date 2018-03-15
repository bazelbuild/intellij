workspace(name = "intellij_with_bazel")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for IntelliJ 2017.3. This is required to build IJwB,
# and run integration tests.
new_http_archive(
    name = "intellij_ce_2017_3",
    build_file = "intellij_platform_sdk/BUILD.idea",
    sha256 = "98e09417ff7363e415f8f54d682ecd121a7225bf6efb1d3cbea691c1dd6fb614",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2017.3.4/ideaIC-2017.3.4.zip",
)

# The plugin api for IntelliJ 2018.1. This is required to build IJwB,
# and run integration tests.
new_http_archive(
    name = "intellij_ce_2018_1",
    build_file = "intellij_platform_sdk/BUILD.idea",
    sha256 = "c1a4274b1e2eb139efaf9304e876e72045c4fd0b0823d6441de072b9c774decd",
    url = "https://download.jetbrains.com/idea/ideaIC-181.3986.9.tar.gz",
)

# The plugin api for IntelliJ UE 2017.3. This is required to run UE-specific
# integration tests.
new_http_archive(
    name = "intellij_ue_2017_3",
    build_file = "intellij_platform_sdk/BUILD.idea",
    sha256 = "8e4152943630002da70125e87fd027e5a243d8183e9265d91a8695cfe916a691",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2017.3.4/ideaIU-2017.3.4.zip",
)

# The plugin api for IntelliJ UE 2018.1. This is required to run UE-specific
# integration tests.
new_http_archive(
    name = "intellij_ue_2018_1",
    build_file = "intellij_platform_sdk/BUILD.idea",
    sha256 = "9c24420c50a397e45c1d7d754464d4b7a0a73b38bb4df68f82829a7481f71305",
    url = "https://download.jetbrains.com/idea/ideaIU-181.3986.9.tar.gz",
)

# The plugin api for CLion 2017.3. This is required to build CLwB,
# and run integration tests.
new_http_archive(
    name = "clion_2017_3",
    build_file = "intellij_platform_sdk/BUILD.clion",
    sha256 = "6d807282a36f25e922580f1c8b2155705d4a20aa8d4e4c06bf17193f0c020948",
    url = "https://download.jetbrains.com/cpp/CLion-2017.3.3.tar.gz",
)

# The plugin api for CLion 2018.1. This is required to build CLwB,
# and run integration tests.
new_http_archive(
    name = "clion_2018_1",
    build_file = "intellij_platform_sdk/BUILD.clion",
    sha256 = "bd372766e42644c8edf0c97306f079bd642527e45041efbeddec7977e844f56c",
    url = "https://download.jetbrains.com/cpp/CLion-181.3986.16.tar.gz",
)

# The plugin api for Android Studio 3.0. This is required to build ASwB,
# and run integration tests.
new_http_archive(
    name = "android_studio_3_0",
    build_file = "intellij_platform_sdk/BUILD.android_studio",
    sha256 = "ad7110ed2ffc662b7a13efa5064390c8e8e74815d8c688351bd8829331852acf",
    url = "https://dl.google.com/dl/android/studio/ide-zips/3.0.1.0/android-studio-ide-171.4443003-linux.zip",
)

# Python plugin for Android Studio 3.0. Required at compile-time for python-specific features.
new_http_archive(
    name = "python_2017_1_4249",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "2192e2248297e85995b647024a66a75b25c27de023b118c51e3d1ea2025a4b32",
    url = "https://plugins.jetbrains.com/files/7322/34430/python-ce-2017.1.171.4249.28.zip",
)

# Python plugin for IntelliJ CE 2017.3. Required at compile-time for python-specific features.
new_http_archive(
    name = "python_2017_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "406c47b5a9f97e5f7ab7e94e62e463beea8cc56da803b56c00801f026b0a559b",
    url = "https://plugins.jetbrains.com/files/7322/41063/python-ce-2017.3.173.3727.131.zip",
)

# Python plugin for IntelliJ CE 2018.1. Required at compile-time for python-specific features.
new_http_archive(
    name = "python_2018_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")"]),
    sha256 = "d35e73214fd8ea32c5093b5ce1b039fa49fae63960294d61d66261a138080d9f",
    url = "https://plugins.jetbrains.com/files/7322/43725/python-ce-2018.1.181.3986.9.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
new_http_archive(
    name = "go_2017_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'go',",
        "    jars = glob(['intellij-go/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "faeed37b9b78d3276e6c66a579fd7ef7f8e9c3e3b62cf1a4e6b8fcc25a447f77",
    url = "https://plugins.jetbrains.com/files/9568/41097/intellij-go-173.3727.144.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
new_http_archive(
    name = "go_2018_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'go',",
        "    jars = glob(['intellij-go/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")"]),
    sha256 = "75b6ffaa114f04e16451bb794eca232835b4dff73ccb4aaab31c65778f702969",
    url = "https://plugins.jetbrains.com/files/9568/43712/intellij-go-181.3986.10.240.zip",
)

# Scala plugin for IntelliJ CE 2017.3. Required at compile-time for scala-specific features.
new_http_archive(
    name = "scala_2017_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = glob(['Scala/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "8e387d459216500ed7f908b66e63dae629a7872bc72eafaa0cd8fb339da00730",
    url = "https://plugins.jetbrains.com/files/1347/40959/scala-intellij-bin-2017.3.9.zip",
)

# Scala plugin for IntelliJ CE 2018.1. Required at compile-time for scala-specific features.
new_http_archive(
    name = "scala_2018_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = glob(['Scala/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")"]),
    sha256 = "c0548598a623a3557d540ff2745ae7daa86a51d86e1428baebefcf4451f61e69",
    url = "https://plugins.jetbrains.com/files/1347/43871/scala-intellij-bin-2018.1.4.zip",
)

new_http_archive(
    name = "android_studio_3_1",
    build_file = "intellij_platform_sdk/BUILD.android_studio",
    sha256 = "e2780a02bc50f9e9fa824bf7262ae72b7277ede776379f636ef36c0ecbdbe066",
    url = "https://dl.google.com/dl/android/studio/ide-zips/3.1.0.12/android-studio-ide-173.4615496-linux.zip",
)

new_http_archive(
    name = "android_studio_3_2",
    build_file = "intellij_platform_sdk/BUILD.android_studio",
    sha256 = "84cd417969170586e65f077f87d51d28c317c06429cc7bd32c3435416ab963de",
    url = "https://dl.google.com/dl/android/studio/ide-zips/3.2.0.3/android-studio-ide-173.4615518-linux.zip",
)

# LICENSE: Common Public License 1.0
maven_jar(
    name = "junit",
    artifact = "junit:junit:4.11",
    sha1 = "4e031bb61df09069aeb2bffb4019e7a5034a4ee0",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "jsr305_annotations",
    artifact = "com.google.code.findbugs:jsr305:3.0.1",
    sha1 = "f7be08ec23c21485b9b5a1cf1654c2ec8c58168d",
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
    artifact = "com.google.auto.value:auto-value:1.3",
    sha1 = "4961194f62915eb45e21940537d60ac53912c57d",
)

# LICENSE: The Apache Software License, Version 2.0
maven_jar(
    name = "error_prone_annotations",
    artifact = "com.google.errorprone:error_prone_annotations:2.0.15",
    sha1 = "822652ed7196d119b35d2e22eb9cd4ffda11e640",
)

# LICENSE: The Apache Software License, Version 2.0
# proto_library rules implicitly depend on @com_google_protobuf//:protoc
http_archive(
    name = "com_google_protobuf",
    strip_prefix = "protobuf-master",
    urls = ["https://github.com/google/protobuf/archive/master.zip"],
)

# LICENSE: The Apache Software License, Version 2.0
# java_proto_library rules implicitly depend on @com_google_protobuf_java//:java_toolchain
# It's the same repository as above, but there's no way to alias them at the moment (and both are
# required).
http_archive(
    name = "com_google_protobuf_java",
    strip_prefix = "protobuf-master",
    urls = ["https://github.com/google/protobuf/archive/master.zip"],
)

# LICENSE: The Apache Software License, Version 2.0
git_repository(
    name = "io_bazel_rules_scala",
    commit = "85308acbd316477f3072e033e7744debcba4f054",
    remote = "https://github.com/bazelbuild/rules_scala.git",
)

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")
scala_repositories()

# LICENSE: The Apache Software License, Version 2.0
git_repository(
    name = "io_bazel_rules_kotlin",
    commit = "6d8dcd4d6000d0cf3321eb8580d8fc67f8731f8e",
    remote = "https://github.com/bazelbuild/rules_kotlin.git",
)

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kotlin_repositories")

kotlin_repositories()

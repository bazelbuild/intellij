workspace(name = "intellij_with_bazel")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for IntelliJ 2017.2. This is required to build IJwB,
# and run integration tests.
new_http_archive(
    name = "intellij_ce_2017_2_3",
    build_file = "intellij_platform_sdk/BUILD.idea",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2017.2.3/ideaIC-2017.2.3.zip",
    sha256 = "423ae4ae94da7e37807afb859928416965f03c397278b43a251c95212f8a552e",
)

# The plugin api for IntelliJ 2017.1. This is required to build IJwB,
# and run integration tests.
new_http_archive(
    name = "intellij_ce_2017_1_5",
    build_file = "intellij_platform_sdk/BUILD.idea",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2017.1.5/ideaIC-2017.1.5.zip",
    sha256 = "d11beb116f500ecbf75b0a1098dfaad696bc8a15edceae163f53bc511ab79445"
)

# The plugin api for IntelliJ UE 2017.2. This is required to run UE-specific
# integration tests.
new_http_archive(
    name = "intellij_ue_2017_2_3",
    build_file = "intellij_platform_sdk/BUILD.idea",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2017.2.3/ideaIU-2017.2.3.zip",
    sha256 = "080be11393fc8b64766dc6ecacdf8a3eaa507e55c01b1f63734db441a003f6ea",
)

# The plugin api for IntelliJ UE 2017.1. This is required to run UE-specific
# integration tests.
new_http_archive(
    name = "intellij_ue_2017_1_5",
    build_file = "intellij_platform_sdk/BUILD.idea",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2017.1.5/ideaIU-2017.1.5.zip",
    sha256 = "6d887f79ca7853923060499cac7778b30fc82414f112deb8245d59179892bbad"
)

# The plugin api for CLion 2017.1.1. This is required to build CLwB,
# and run integration tests.
new_http_archive(
    name = "clion_2017_1_1",
    build_file = "intellij_platform_sdk/BUILD.clion",
    url = "https://download.jetbrains.com/cpp/CLion-2017.1.1.tar.gz",
    sha256 = "9abd6bd38801ae6cf29db2cd133c700e8da11841093de872312fe33ed51309ae",
)

# The plugin api for CLion 2017.2.0. This is required to build CLwB,
# and run integration tests.
new_http_archive(
    name = "clion_2017_2_2",
    build_file = "intellij_platform_sdk/BUILD.clion",
    url = "https://download.jetbrains.com/cpp/CLion-2017.2.2.tar.gz",
    sha256 = "a019cd2469ecda7d93f3cd7ad3b8e349f374425783f6b4a54181907f6264d6e6",
)

# The plugin api for Android Studio 2.3.1. This is required to build ASwB,
# and run integration tests.
new_http_archive(
    name = "android_studio_2_3_1_0",
    build_file = "intellij_platform_sdk/BUILD.android_studio",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2.3.1.0/android-studio-ide-162.3871768-linux.zip",
    sha256 = "36520f21678f80298b5df5fe5956db17a5984576f895fdcaa36ab0dbfb408433",
)

# The plugin api for Android Studio 3.0 Beta 5. This is required to build ASwB,
# and run integration tests.
new_http_archive(
    name = "android_studio_3_0_0_13",
    build_file = "intellij_platform_sdk/BUILD.android_studio",
    url = "https://dl.google.com/dl/android/studio/ide-zips/3.0.0.13/android-studio-ide-171.4316950-linux.zip",
    sha256 = "accab8a8270bcc8c273ff980d26195dad3ea77839741e4dcd01cad5bd8ac462a",
)

# Python plugin for IntelliJ CE 2017.1. Required at compile-time for python-specific features.
new_http_archive(
    name = "python_2017_1_4694",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")"]),
    url = "https://download.plugins.jetbrains.com/7322/35756/python-ce-2017.1.171.4694.26.zip",
    sha256 = "640d116ff01bcc2f87c75d20da642f17fcd86ed69929e4e0247ffc0df8aa780f",
)

# Python plugin for Android Studio 3.0. Required at compile-time for python-specific features.
new_http_archive(
    name = "python_2017_1_4249",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")"]),
    url = "https://download.plugins.jetbrains.com/7322/34430/python-ce-2017.1.171.4249.28.zip",
    sha256 = "2192e2248297e85995b647024a66a75b25c27de023b118c51e3d1ea2025a4b32",
)

# Python plugin for IntelliJ CE 2017.2. Required at compile-time for python-specific features.
new_http_archive(
    name = "python_2017_2",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")"]),
    url = "https://download.plugins.jetbrains.com/7322/37356/python-ce-2017.2.172.3544.31.zip",
    sha256 = "c7ee48c0bafb29f4a18eaac804b113c4dcdfeaaae174d9003c9ad96e44df6fe0",
)

# Go plugin for IntelliJ UE and CLion. Required at compile-time for Bazel integration.
new_http_archive(
    name = "go_2017_2",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'go',",
        "    jars = glob(['intellij-go/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")"]),
    url = "https://download.plugins.jetbrains.com/9568/37740/intellij-go-172.3757.46.zip",
    sha256 = "3e5eb5415a05e6c30e79c263135c2937cc05e310e553889bd69eefa819705f9c",
)

# Go plugin for IntelliJ UE and CLion 2017.1.5. Required at compile-time for Bazel integration.
new_http_archive(
    name = "go_2017_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'go',",
        "    jars = glob(['intellij-go/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")"]),
    url = "https://download.plugins.jetbrains.com/9568/36389/intellij-go-171.4694.61.zip",
    sha256 = "16bf70045360c1c2a056c9ae540626dffa3680b8283cb89febaff3a9499b7101",
)

# Scala plugin for IntelliJ CE 2017.2 EAP. Required at compile-time for scala-specific features.
new_http_archive(
    name = "scala_2017_2",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = [",
        "        'Scala/lib/scala-plugin.jar',",
        "        'Scala/lib/compiler-settings.jar',",
        "        'Scala/lib/scala-library.jar',",
        "        'Scala/lib/scalameta120.jar',",
        "        'Scala/lib/scalatest-finders-patched.jar',",
        "    ],",
        "    visibility = ['//visibility:public'],",
        ")"]),
   url = "https://download.plugins.jetbrains.com/1347/35283/scala-intellij-bin-2017.2.2.zip",
    sha256 = "1f0eef98da44dbc3f4f22b399a9175897aca448fd80405eca77fd61bd5fb7219",
)

# Scala plugin for IntelliJ CE 2017.1. Required at compile-time for scala-specific features.
new_http_archive(
    name = "scala_2017_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = [",
        "        'Scala/lib/scala-plugin.jar',",
        "        'Scala/lib/compiler-settings.jar',",
        "        'Scala/lib/scala-library.jar',",
        "        'Scala/lib/scalameta120.jar',",
        "        'Scala/lib/scalatest-finders-patched.jar',",
        "    ],",
        "    visibility = ['//visibility:public'],",
        ")"]),
    url = "https://plugins.jetbrains.com/files/1347/33637/scala-intellij-bin-2017.1.15.zip",
    sha256 = "b58670a3b52584effc6dd3d014e77fe80e2795b5e5e58716548ecc1452eca6cf",
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

rules_scala_version="a72dc8bb2033fed96deec39420b99331a2119a4e"

# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "io_bazel_rules_scala",
    strip_prefix= "rules_scala-%s" % rules_scala_version,
    urls = ["https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version],
)

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")
scala_repositories()

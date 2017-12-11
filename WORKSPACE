workspace(name = "intellij_with_bazel")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for IntelliJ 2017.2. This is required to build IJwB,
# and run integration tests.
new_http_archive(
    name = "intellij_ce_2017_2_6",
    build_file = "intellij_platform_sdk/BUILD.idea",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2017.2.6/ideaIC-2017.2.6.zip",
    sha256 = "4d1c873c0bcc10ec12a1a13580003846e94b328ad1246601fd41d147340bde6f",
)

# The plugin api for IntelliJ 2017.3. This is required to build IJwB,
# and run integration tests.
new_http_archive(
    name = "intellij_ce_2017_3_0",
    build_file = "intellij_platform_sdk/BUILD.idea",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2017.3/ideaIC-2017.3.zip",
    sha256 = "cb2a420ad5aeeb6a9240b810686bcf44712241e16534425ee815950f8bae660a",
)

# The plugin api for IntelliJ UE 2017.2. This is required to run UE-specific
# integration tests.
new_http_archive(
    name = "intellij_ue_2017_2_6",
    build_file = "intellij_platform_sdk/BUILD.idea",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2017.2.6/ideaIU-2017.2.6.zip",
    sha256 = "b5876759195be367822e39ba63b07e579713492fbd902137641dec7707602cc0",
)

# The plugin api for IntelliJ UE 2017.3. This is required to run UE-specific
# integration tests.
new_http_archive(
    name = "intellij_ue_2017_3_0",
    build_file = "intellij_platform_sdk/BUILD.idea",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2017.3/ideaIU-2017.3.zip",
    sha256 = "e3ab0b6763a4ecd67db8733165f7fdedc1e4e275e405cd617b495b7bdf270c17",
)

# The plugin api for CLion 2017.2.0. This is required to build CLwB,
# and run integration tests.
new_http_archive(
    name = "clion_2017_2_3",
    build_file = "intellij_platform_sdk/BUILD.clion",
    url = "https://download.jetbrains.com/cpp/CLion-2017.2.3.tar.gz",
    sha256 = "dd1979947371803a1e11f5bdaf04e3ef2d013b90b56e84495c6e67e67cb31e0a",
)

# The plugin api for CLion 2017.2.0. This is required to build CLwB,
# and run integration tests.
new_http_archive(
    name = "clion_2017_3_0",
    build_file = "intellij_platform_sdk/BUILD.clion",
    url = "https://download.jetbrains.com/cpp/CLion-2017.3.tar.gz",
    sha256 = "ce5e9acfae6b885f0204ba53a965a00530dbb986b800a04b97112ee2719e693f",
)

# The plugin api for Android Studio 3.0. This is required to build ASwB,
# and run integration tests.
new_http_archive(
    name = "android_studio_3_0_0_18",
    build_file = "intellij_platform_sdk/BUILD.android_studio",
    url = "https://dl.google.com/dl/android/studio/ide-zips/3.0.0.18/android-studio-ide-171.4408382-linux.zip",
    sha256 = "7991f95ea1b6c55645a3fc48f1534d4135501a07b9d92dd83672f936d9a9d7a2",
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

# Python plugin for IntelliJ CE 2017.3. Required at compile-time for python-specific features.
new_http_archive(
    name = "python_2017_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")"]),
    url = "https://download.plugins.jetbrains.com/7322/41063/python-ce-2017.3.173.3727.131.zip",
    sha256 = "406c47b5a9f97e5f7ab7e94e62e463beea8cc56da803b56c00801f026b0a559b",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
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

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
new_http_archive(
    name = "go_2017_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'go',",
        "    jars = glob(['intellij-go/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")"]),
    url = "https://download.plugins.jetbrains.com/9568/40858/intellij-go-173.3727.96.zip",
    sha256 = "be3e07d8db9867145f5aa924b2cac06eadd863d1493bff2c62ffc74bb54729e3",
)

# Scala plugin for IntelliJ CE 2017.2. Required at compile-time for scala-specific features.
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

# Scala plugin for IntelliJ CE 2017.3. Required at compile-time for scala-specific features.
new_http_archive(
    name = "scala_2017_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = glob(['Scala/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")"]),
    url = "https://download.plugins.jetbrains.com/1347/40959/scala-intellij-bin-2017.3.9.zip",
    sha256 = "8e387d459216500ed7f908b66e63dae629a7872bc72eafaa0cd8fb339da00730",
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

rules_scala_version="85308acbd316477f3072e033e7744debcba4f054"

# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "io_bazel_rules_scala",
    strip_prefix= "rules_scala-%s" % rules_scala_version,
    urls = ["https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version],
)

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")
scala_repositories()

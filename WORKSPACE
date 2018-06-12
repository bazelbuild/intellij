workspace(name = "intellij_with_bazel")

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for IntelliJ 2017.3. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2017_3",
    build_file = "@//intellij_platform_sdk:BUILD.idea",
    sha256 = "fcc571a869808776c7e336e301246e2db0f6adcbfaf7244422cb97f8be0e06d1",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2017.3.5/ideaIC-2017.3.5.zip",
)

# The plugin api for IntelliJ 2018.1. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2018_1",
    build_file = "@//intellij_platform_sdk:BUILD.idea",
    sha256 = "2fbbc2ad29082ccdda0422fa082556c180eb80f0d7238865604a49855e5bbb68",
    # TODO: remove this and change BUILD.idea to glob idea-*/ like we do in
    # BUILD.clion once 2017.3 goes away.
    strip_prefix = "idea-IC-181.4668.68",
    url = "https://download.jetbrains.com/idea/ideaIC-2018.1.2.tar.gz",
)

# The plugin api for IntelliJ 2018.2. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2018_2",
    build_file = "@//intellij_platform_sdk:BUILD.idea",
    sha256 = "bbfa4ee0c6fb237a91c96f533d591218556d0a9046dd0bd747d714c409d32ba9",
    # TODO: remove this and change BUILD.idea to glob idea-*/ like we do in
    # BUILD.clion once 2017.3 goes away.
    strip_prefix = "idea-IC-182.2949.4",
    url = "https://download.jetbrains.com/idea/ideaIC-182.2949.4.tar.gz",
)

# The plugin api for IntelliJ UE 2017.3. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2017_3",
    build_file = "@//intellij_platform_sdk:BUILD.idea",
    sha256 = "5514e9b84f942621205a1f0588e4f8346362d6e7f81355e4622bb7c1bbce9459",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2017.3.5/ideaIU-2017.3.5.zip",
)

# The plugin api for IntelliJ UE 2018.1. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2018_1",
    build_file = "@//intellij_platform_sdk:BUILD.idea",
    sha256 = "c0a8f0fdd9c80bec62320fc26bdf3546ee513f51d990e0cf6d66b3d998e23a10",
    # TODO: see intellij_ce_2018_1
    strip_prefix = "idea-IU-181.4668.68",
    url = "https://download.jetbrains.com/idea/ideaIU-2018.1.1.tar.gz",
)

# The plugin api for IntelliJ UE 2018.2. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2018_2",
    build_file = "@//intellij_platform_sdk:BUILD.idea",
    sha256 = "3ebf71ff818f907aef182636adad970378baee3c82d252ba4990180dc8d6f88e",
    # TODO: see intellij_ce_2018_1
    strip_prefix = "idea-IU-182.2949.4",
    url = "https://download.jetbrains.com/idea/ideaIU-182.2949.4.tar.gz",
)

# The plugin api for CLion 2017.3. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2017_3",
    build_file = "@//intellij_platform_sdk:BUILD.clion",
    sha256 = "8f7d946a71e061b88061e6a00763a026e2b50ec4b4fc05df3b73d684aedeb2fb",
    url = "https://download.jetbrains.com/cpp/CLion-2017.3.4.tar.gz",
)

# The plugin api for CLion 2018.1. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2018_1",
    build_file = "@//intellij_platform_sdk:BUILD.clion",
    sha256 = "85df4a1c2ed095cbf706fa73d30a87cad400a4ff5ef40acc7a7da676140c5235",
    url = "https://download.jetbrains.com/cpp/CLion-2018.1.3.tar.gz",
)

# The plugin api for Android Studio 3.0. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_3_0",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio",
    sha256 = "ad7110ed2ffc662b7a13efa5064390c8e8e74815d8c688351bd8829331852acf",
    url = "https://dl.google.com/dl/android/studio/ide-zips/3.0.1.0/android-studio-ide-171.4443003-linux.zip",
)

# Python plugin for Android Studio 3.0. Required at compile-time for python-specific features.
http_archive(
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
http_archive(
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

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
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

# Scala plugin for IntelliJ CE 2017.3. Required at compile-time for scala-specific features.
http_archive(
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

http_archive(
    name = "android_studio_3_1",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio",
    sha256 = "e2780a02bc50f9e9fa824bf7262ae72b7277ede776379f636ef36c0ecbdbe066",
    url = "https://dl.google.com/dl/android/studio/ide-zips/3.1.0.12/android-studio-ide-173.4615496-linux.zip",
)

http_archive(
    name = "android_studio_3_2",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio",
    sha256 = "8c98f9018cf59faa0e1d2faf39bdad187166088b0d4a5506bd6088964a3527ad",
    url = "https://dl.google.com/dl/android/studio/ide-zips/3.2.0.11/android-studio-ide-181.4729833-linux.zip",
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
    commit = "40151843d9be877048f187fd2f627c1eccfb3b5c",
    remote = "https://github.com/bazelbuild/rules_scala.git",
)

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")
scala_repositories()
load("@io_bazel_rules_scala//scala:toolchains.bzl", "scala_register_toolchains")
scala_register_toolchains()

# LICENSE: The Apache Software License, Version 2.0
git_repository(
    name = "io_bazel_rules_kotlin",
    commit = "6d8dcd4d6000d0cf3321eb8580d8fc67f8731f8e",
    remote = "https://github.com/bazelbuild/rules_kotlin.git",
)

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kotlin_repositories")

kotlin_repositories()

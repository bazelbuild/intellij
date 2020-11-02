workspace(name = "intellij_with_bazel")

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for IntelliJ 2020.1. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2020_1",
    build_file = "@//intellij_platform_sdk:BUILD.idea201",
    sha256 = "fd90c5af5cfa248dce54b025df78ec4f0b5f49dd048a4d67493bc00868837de4",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2020.1.4/ideaIC-2020.1.4.zip",
)

# The plugin api for IntelliJ 2020.1. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2020_2",
    build_file = "@//intellij_platform_sdk:BUILD.idea202",
    sha256 = "4fbaa21efdfbfcf75074d8e70509cb94dbc1c3181e921f0920582ce5fa469908",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2020.2.2/ideaIC-2020.2.2.zip",
)

# The plugin api for IntelliJ UE 2020.1. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2020_1",
    build_file = "@//intellij_platform_sdk:BUILD.ue201",
    sha256 = "2500339706e2951ae63a3c6e82e31a1da26adeee41a79724079420c2f29e18bb",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2020.1.4/ideaIU-2020.1.4.zip",
)

# The plugin api for IntelliJ UE 2020.1. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2020_2",
    build_file = "@//intellij_platform_sdk:BUILD.ue202",
    sha256 = "434adfaa3e98e7eac8ab3d292086958c9f453b6ab2390e8d5c0a53e945e3f857",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2020.2.2/ideaIU-2020.2.2.zip",
)

# The plugin api for CLion 2019.3. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2019_3",
    build_file = "@//intellij_platform_sdk:BUILD.clion193",
    sha256 = "4034d47ec4bd3c8123099d4b9614031c975f8cd37977b8df31bf77b6e85caf06",
    url = "https://download.jetbrains.com/cpp/CLion-2019.3.6.tar.gz",
)

# The plugin api for CLion 2019.3. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2020_1",
    build_file = "@//intellij_platform_sdk:BUILD.clion201",
    sha256 = "a95c27cf367f7b698b4bb1f5649f8399c29f4031bcf6b133336d0d75169f4b97",
    url = "https://download.jetbrains.com/cpp/CLion-2020.1.3.tar.gz",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2019_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "718750acf836c9e9c85fe4d342a619afb26932bc0d77e7adfa25be1facb8a23f",
    url = "https://plugins.jetbrains.com/files/7322/85764/python-ce.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2020_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "a3d7217794619dfa0432d922218dd875f49658939c6e5a54c01f99b333d35e12",
    url = "https://plugins.jetbrains.com/files/7322/88054/python-ce-201.7846.93.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2020_2",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "fa9524db2f5b6e42be559ffdab73a963ca06ca057e27a290f5665d38e581764a",
    url = "https://plugins.jetbrains.com/files/7322/97141/python-ce-202.7319.64.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2020_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'go',",
        "    jars = glob(['intellij-go/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "af4ad5070d27788bece5bb9acb66d90acf42008febbcdac5d794c06f78621b19",
    url = "https://plugins.jetbrains.com/files/9568/87978/intellij-go-201.7846.76.189.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2020_2",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'go',",
        "    jars = glob(['intellij-go/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "c020553701939692d165d3607dd94532f97c48d81435f1cb6d927a6723888327",
    url = "https://plugins.jetbrains.com/files/9568/97011/go-202.7319.50.zip",
)

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2020_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = glob(['Scala/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "bbc019e7cde3baf21b11f0abde21f42d4606ca5ca9100b3bdace3292f70cceef",
    url = "https://plugins.jetbrains.com/files/1347/76628/scala-intellij-bin-2020.1.7.zip",
)

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2020_2",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala',",
        "    jars = glob(['Scala/lib/*.jar']),",
        "    visibility = ['//visibility:public'],",
        ")",
    ]),
    sha256 = "52004387981df909afaeabb91b032aee51d1860acf7b434d111f261a9e1ed07d",
    url = "https://plugins.jetbrains.com/files/1347/97067/scala-intellij-bin-2020.2.27.zip",
)

# The plugin api for Android Studio 4.1. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_4_1",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio41",
    sha256 = "4b9521fc4a6313ad65ff3e14b0f3fb50427d464fe14227555565f46e0ffad202",
    url = "https://dl.google.com/dl/android/studio/ide-zips/4.1.0.19/android-studio-ide-201.6858069-linux.tar.gz",
)

# The plugin api for Android Studio 4.2. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_4_2",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio42",
    sha256 = "df9e33c751b9e7227168a2a87a2062c72fec06cd85c6054fc626950f5a363ab1",
    url = "https://dl.google.com/dl/android/studio/ide-zips/4.2.0.7/android-studio-ide-201.6720134-linux.tar.gz",
)

# LICENSE: Common Public License 1.0
jvm_maven_import_external(
    name = "junit",
    artifact = "junit:junit:4.12",
    artifact_sha256 = "59721f0805e223d84b90677887d9ff567dc534d7c502ca903c0c2b17f05c116a",
    licenses = ["notice"],  # Common Public License 1.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "jsr305_annotations",
    artifact = "com.google.code.findbugs:jsr305:3.0.2",
    artifact_sha256 = "766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "truth",
    artifact = "com.google.truth:truth:0.42",
    artifact_sha256 = "dd652bdf0c4427c59848ac0340fd6b6d20c2cbfaa3c569a8366604dbcda5214c",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "truth8",
    artifact = "com.google.truth.extensions:truth-java8-extension:0.42",
    artifact_sha256 = "cf9e095a6763bc33633b8844c3ebadffe3b082c81dd97a4d79b64ad88d305bc1",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "mockito",
    artifact = "org.mockito:mockito-core:3.3.0",
    artifact_sha256 = "fc1a1f2d1d64566bc31ee36d8214059f2adbe303d9109e5cc0e99685741c57c2",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "bytebuddy",
    artifact = "net.bytebuddy:byte-buddy:1.10.5",
    artifact_sha256 = "3c9c603970bb9d68572c1aa29e9ae6b477d602922977a04bfa5f3b5465d7d1f4",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "bytebuddy-agent",
    artifact = "net.bytebuddy:byte-buddy-agent:1.10.5",
    artifact_sha256 = "290c9930965ef5810ddb15baf3b3647ce952f40fa2f0af82d5f669e04ba87e5b",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "jarjar",
    artifact = "com.googlecode.jarjar:jarjar:1.3",
    artifact_sha256 = "4225c8ee1bf3079c4b07c76fe03c3e28809a22204db6249c9417efa4f804b3a7",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "auto_value",
    artifact = "com.google.auto.value:auto-value:1.6.2",
    artifact_sha256 = "edbe65a5c53e3d4f5cb10b055d4884ae7705a7cd697be4b2a5d8427761b8ba12",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "auto_value_annotations",
    artifact = "com.google.auto.value:auto-value-annotations:1.6.2",
    artifact_sha256 = "b48b04ddba40e8ac33bf036f06fc43995fc5084bd94bdaace807ce27d3bea3fb",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "error_prone_annotations",
    artifact = "com.google.errorprone:error_prone_annotations:2.3.0",
    artifact_sha256 = "524b43ea15ca97c68f10d5f417c4068dc88144b620d2203f0910441a769fd42f",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

git_repository(
    name = "bazel",
    branch = "master",
    remote = "https://github.com/bazelbuild/bazel",
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
http_archive(
    name = "rules_proto",
    sha256 = "4d421d51f9ecfe9bf96ab23b55c6f2b809cbaf0eea24952683e397decfbd0dd0",
    strip_prefix = "rules_proto-f6b8d89b90a7956f6782a4a3609b2f0eee3ce965",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_proto/archive/f6b8d89b90a7956f6782a4a3609b2f0eee3ce965.tar.gz",
        "https://github.com/bazelbuild/rules_proto/archive/f6b8d89b90a7956f6782a4a3609b2f0eee3ce965.tar.gz",
    ],
)

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")

rules_proto_dependencies()

rules_proto_toolchains()

# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "io_bazel_rules_scala",
    sha256 = "b8b18d0fe3f6c3401b4f83f78f536b24c7fb8b92c593c1dcbcd01cc2b3e85c9a",
    strip_prefix = "rules_scala-a676633dc14d8239569affb2acafbef255df3480",
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/a676633dc14d8239569affb2acafbef255df3480.zip",
)

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")

scala_repositories()

load("@io_bazel_rules_scala//scala:toolchains.bzl", "scala_register_toolchains")

scala_register_toolchains()

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

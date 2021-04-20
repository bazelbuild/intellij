workspace(name = "intellij_with_bazel")

load("@bazel_tools//tools/build_defs/repo:git.bzl", "new_git_repository")
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

# The plugin api for IntelliJ 2020.2. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2020_2",
    build_file = "@//intellij_platform_sdk:BUILD.idea202",
    sha256 = "4fbaa21efdfbfcf75074d8e70509cb94dbc1c3181e921f0920582ce5fa469908",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2020.2.2/ideaIC-2020.2.2.zip",
)

# The plugin api for IntelliJ 2020.3. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2020_3",
    build_file = "@//intellij_platform_sdk:BUILD.idea203",
    sha256 = "fce0df34d30c8495667ab2c2df0dbb374caeb0707e5efd7fc3dcf27f1c54902d",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2020.3.3/ideaIC-2020.3.3.zip",
)

# The plugin api for IntelliJ UE 2020.1. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2020_1",
    build_file = "@//intellij_platform_sdk:BUILD.ue201",
    sha256 = "2500339706e2951ae63a3c6e82e31a1da26adeee41a79724079420c2f29e18bb",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2020.1.4/ideaIU-2020.1.4.zip",
)

# The plugin api for IntelliJ UE 2020.2. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2020_2",
    build_file = "@//intellij_platform_sdk:BUILD.ue202",
    sha256 = "434adfaa3e98e7eac8ab3d292086958c9f453b6ab2390e8d5c0a53e945e3f857",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2020.2.2/ideaIU-2020.2.2.zip",
)

# The plugin api for IntelliJ UE 2020.3. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2020_3",
    build_file = "@//intellij_platform_sdk:BUILD.ue203",
    sha256 = "330aa9a89e2277de52d02c1c18696ff5137bd9ee5a47706562199aa3be02eeac",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2020.3.3/ideaIU-2020.3.3.zip",
)

# The plugin api for CLion 2020.1. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2020_1",
    build_file = "@//intellij_platform_sdk:BUILD.clion201",
    sha256 = "a95c27cf367f7b698b4bb1f5649f8399c29f4031bcf6b133336d0d75169f4b97",
    url = "https://download.jetbrains.com/cpp/CLion-2020.1.3.tar.gz",
)

# The plugin api for CLion 2020.2. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2020_2",
    build_file = "@//intellij_platform_sdk:BUILD.clion202",
    sha256 = "3f7b37574ec4106fb92377722c12200a759d12487409e14214166acc11ecef48",
    url = "https://download.jetbrains.com/cpp/CLion-2020.2.5.tar.gz",
)

# The plugin api for CLion 2020.3. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2020_3",
    build_file = "@//intellij_platform_sdk:BUILD.clion203",
    sha256 = "5d49bd88b6457271464687453ff65880a4a38974575bb76f969036c692072280",
    url = "https://download.jetbrains.com/cpp/CLion-2020.3.2.tar.gz",
)

_PYTHON_CE_BUILD_FILE = """
java_import(
    name = "python",
    jars = ["python-ce/lib/python-ce.jar"],
    visibility = ["//visibility:public"],
)
"""

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2020_1",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "648438d26e85072f90a62f9f5b9f9b983a49f3cb752e87d01f915cb6a03644b9",
    url = "https://plugins.jetbrains.com/files/7322/88054/python-ce-201.7846.93.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2020_2",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "6712a9726e9b37ebe39c7b62d2a0835b97f1a366a1d8fcfbd5e81fd6bd414d9e",
    url = "https://plugins.jetbrains.com/files/7322/97141/python-ce-202.7319.64.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2020_3",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "722fb54b503de61989d65bc544f25f03891614467e62f4faef677cefbcd51340",
    url = "https://plugins.jetbrains.com/files/7322/114033/python-ce-203.7717.65.zip",
)

_GO_201_BUILD_FILE = """
java_import(
    name = "go",
    jars = glob(["intellij-go/lib/*.jar"]),
    visibility = ["//visibility:public"],
)
"""

_GO_BUILD_FILE = """
java_import(
    name = "go",
    jars = glob(["go/lib/*.jar"]),
    visibility = ["//visibility:public"],
)
"""

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2020_1",
    build_file_content = _GO_201_BUILD_FILE,
    sha256 = "1f7c47c2a3f6799f921d077da1d056414c9f67bea568a2053dd0dbdc526933a3",
    url = "https://plugins.jetbrains.com/files/9568/87978/intellij-go-201.7846.76.189.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2020_2",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "4df0c9ddd51f08176a4b37194ecce1c1b37a3d9bbd484b3129a754a22c7dc79d",
    url = "https://plugins.jetbrains.com/files/9568/97011/go-202.7319.50.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2020_3",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "996231c0bdeedfe4ea2ae66f72e9687818004fc363a415f814cb58f4563579c3",
    url = "https://plugins.jetbrains.com/files/9568/112071/go-203.7717.11.zip",
)

_SCALA_BUILD_FILE = """
java_import(
    name = "scala",
    jars = glob(["Scala/lib/*.jar"]),
    visibility = ["//visibility:public"],
)
"""

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2020_1",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "626b9c9bc2f90d64498524bd7138558edacf50bbc72a02f882401118a1fc1403",
    url = "https://plugins.jetbrains.com/files/1347/76628/scala-intellij-bin-2020.1.7.zip",
)

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2020_2",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "6c29fe5cf0ea2b242adfa2265c8c6e409358640302b6666afc8e023eca237010",
    url = "https://plugins.jetbrains.com/files/1347/97067/scala-intellij-bin-2020.2.27.zip",
)

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2020_3",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "d6411ae778eea6b04d8e27365925448851dc83852a9ed52317094d3442c84d7e",
    url = "https://plugins.jetbrains.com/files/1347/113954/scala-intellij-bin-2020.3.23.zip",
)

# The plugin api for Android Studio 4.2. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_4_2",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio42",
    sha256 = "2ac04177a96161b2ece526404ee40f12aa3afba49c580ca92b257c6b84f7dd69",
    url = "https://dl.google.com/dl/android/studio/ide-zips/4.2.0.23/android-studio-ide-202.7231092-linux.tar.gz",
)

# The plugin api for Android Studio 2020.3. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_2020_3",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio203",
    sha256 = "740d2e2275738b9c6de62c6cb2a8294a0fab3f1b2f97769cee9e39afb02203a9",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2020.3.1.14/android-studio-2020.3.1.14-linux.tar.gz",
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

_JARJAR_BUILD_FILE = """
java_binary(
    name = "jarjar_bin",
    srcs = glob(
        ["src/main/**/*.java"],
        exclude = [
            "src/main/com/tonicsystems/jarjar/JarJarMojo.java",
            "src/main/com/tonicsystems/jarjar/util/AntJarProcessor.java",
            "src/main/com/tonicsystems/jarjar/JarJarTask.java",
        ],
    ),
    main_class = "com.tonicsystems.jarjar.Main",
    resources = [":help"],
    use_launcher = False,
    visibility = ["//visibility:public"],
    deps = [":asm"],
)

java_import(
    name = "asm",
    jars = glob(["lib/asm-*.jar"]),
)

genrule(
    name = "help",
    srcs = ["src/main/com/tonicsystems/jarjar/help.txt"],
    outs = ["com/tonicsystems/jarjar/help.txt"],
    cmd = "cp $< $@",
)
"""

new_git_repository(
    name = "jarjar",
    build_file_content = _JARJAR_BUILD_FILE,
    commit = "38ff702d10baec78f30d5f57485ae452f0fe33b5",
    remote = "https://github.com/google/jarjar",
    shallow_since = "1518210648 -0800",
)

http_archive(
    name = "bazel_skylib",
    sha256 = "2ef429f5d7ce7111263289644d233707dba35e39696377ebab8b0bc701f7818e",
    url = "https://github.com/bazelbuild/bazel-skylib/releases/download/0.8.0/bazel-skylib.0.8.0.tar.gz",
)

# specify a minimum version for bazel otherwise users on old versions may see
# unexpressive errors when new features are used
load("@bazel_skylib//lib:versions.bzl", "versions")
versions.check(minimum_bazel_version = "4.0.0")

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
    sha256 = "aa1ee19226f707d44bee44c720915199c20c84a23318bb0597ed4e5c873ccbd5",
    strip_prefix = "rules_proto-40298556293ae502c66579620a7ce867d5f57311",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_proto/archive/40298556293ae502c66579620a7ce867d5f57311.tar.gz",
        "https://github.com/bazelbuild/rules_proto/archive/40298556293ae502c66579620a7ce867d5f57311.tar.gz",
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

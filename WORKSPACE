workspace(name = "intellij_with_bazel")

load("@bazel_tools//tools/build_defs/repo:git.bzl", "new_git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for IntelliJ 2020.3. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2020_3",
    build_file = "@//intellij_platform_sdk:BUILD.idea203",
    sha256 = "a0733cb1c3266e7b15d9c47a3ea62ca35cca0ef009edf63847b76f1663448de9",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2020.3.4/ideaIC-2020.3.4.zip",
)

# The plugin api for IntelliJ 2021.1. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2021_1",
    build_file = "@//intellij_platform_sdk:BUILD.idea211",
    sha256 = "ec6aee9fde6e8d9fd5eb730309d63399878d8760107fbd1ddbe402f56eed9d86",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2021.1.3/ideaIC-2021.1.3.zip",
)

# The plugin api for IntelliJ 2021.2. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2021_2",
    build_file = "@//intellij_platform_sdk:BUILD.idea212",
    sha256 = "8f55f3bbe6ae78160301820b0ed52e226139754958247b6a3d8924c8f778d2b8",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2021.2.3/ideaIC-2021.2.3.zip",
)

# The plugin api for intellij_ce_2021_3. This is required to build IJwB and run integration tests.
http_archive(
    name = "intellij_ce_2021_3",
    build_file = "@//intellij_platform_sdk:BUILD.idea213",
    sha256 = "7dea1bc8473974d8c49412b7c2c79cb4360025a9114b59145aef957afefe1111",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2021.3/ideaIC-2021.3.zip",
)

# The plugin api for IntelliJ UE 2020.3. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2020_3",
    build_file = "@//intellij_platform_sdk:BUILD.ue203",
    sha256 = "5da5ad759c31b8f264da87430186f7651feade039e00f4b0ab945215cb38e4e5",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2020.3.4/ideaIU-2020.3.4.zip",
)

# The plugin api for IntelliJ UE 2021.1. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2021_1",
    build_file = "@//intellij_platform_sdk:BUILD.ue211",
    sha256 = "da25231d007afea92879e3fdd10b85b2ec69772ade47601d05c67e0b3e5f5d7d",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2021.1.3/ideaIU-2021.1.3.zip",
)

# The plugin api for IntelliJ UE 2021.2. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2021_2",
    build_file = "@//intellij_platform_sdk:BUILD.ue212",
    sha256 = "89ad86c940ab1cc7dc13882cd705919830ccfb02814789c0f9389fff26af1ad1",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2021.2.3/ideaIU-2021.2.3.zip",
)

# The plugin api for intellij_ue_2021_3. This is required to run UE-specific integration tests.
http_archive(
    name = "intellij_ue_2021_3",
    build_file = "@//intellij_platform_sdk:BUILD.ue213",
    sha256 = "a85b6cf3fc9d62af4fd829c2068e062c9ccaddb0b8fd55ab43bfe921df3582f3",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2021.3/ideaIU-2021.3.zip",
)

# The plugin api for CLion 2020.3. This is required to build CLwB,
# and run integration tests.
http_archive(
    name = "clion_2020_3",
    build_file = "@//intellij_platform_sdk:BUILD.clion203",
    sha256 = "458ae7c8fcae9553ff9f53c0b49734c7c00eb2c10beeac479486984f21d17557",
    url = "https://download.jetbrains.com/cpp/CLion-2020.3.4.tar.gz",
)

# The plugin api for clion_2021_1. This is required to build CLwB, and run integration tests.
http_archive(
    name = "clion_2021_1",
    build_file = "@//intellij_platform_sdk:BUILD.clion211",
    sha256 = "bf2f627bab06fa94b32f205f15a67659a7bb38e078847cb6e3f811098dc13897",
    url = "https://download.jetbrains.com/cpp/CLion-2021.1.3.tar.gz",
)

# The plugin api for clion_2021_2. This is required to build CLwB, and run integration tests.
http_archive(
    name = "clion_2021_2",
    build_file = "@//intellij_platform_sdk:BUILD.clion212",
    sha256 = "25c3968c72976b2de5e3e8f39b242702c4ec25b32268022cd16590e9fbfc0b27",
    url = "https://download.jetbrains.com/cpp/CLion-2021.2.3.tar.gz",
)

# The plugin api for clion_2021_3. This is required to build CLwB, and run integration tests.
http_archive(
    name = "clion_2021_3",
    build_file = "@//intellij_platform_sdk:BUILD.clion213",
    sha256 = "4968657ad64d55da794bcf9b878fdd36ed233014152930058f761296d2047867",
    url = "https://download.jetbrains.com/cpp/CLion-2021.3.tar.gz",
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
    name = "python_2020_3",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "722fb54b503de61989d65bc544f25f03891614467e62f4faef677cefbcd51340",
    url = "https://plugins.jetbrains.com/files/7322/114033/python-ce-203.7717.65.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2021_1",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "7d16cc9bf80c9e2c26d55d55564c1c174583a5e6900e6b7f13d5663275b07644",
    url = "https://plugins.jetbrains.com/files/7322/125352/python-ce-211.7628.24.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2021_2",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "e094cce16a4ca6062f0aa9974217299aa3d4fcec8130581e2d83c76bea10841a",
    url = "https://plugins.jetbrains.com/files/7322/142192/python-ce-212.5457.59.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2021_3",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "3cf2870231e0ac25300317b7c9cd748123e17bb20741728bc4bb2d6f7f6f0869",
    url = "https://plugins.jetbrains.com/files/7322/148132/python-ce-213.5744.248.zip",
)

_GO_BUILD_FILE = """
java_import(
    name = "go",
    jars = glob(["go/lib/*.jar"]),
    visibility = ["//visibility:public"],
)
"""

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2020_3",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "41e5ca13cc8bfb033963ff890d9c51d24cd9595a7a41046416e61a5fc8f0e2a4",
    url = "https://plugins.jetbrains.com/files/9568/117680/go-203.8084.17.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2021_1",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "da95dd911e98e1ca04107794ea0e8732105e227b0ae3ea593240aca72a1785ca",
    url = "https://plugins.jetbrains.com/files/9568/122859/go-211.7628.1.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2021_2",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "c60cdbce31a4aa66648db6e69de4c6eeb590a030a80ad54b1e3ab6225a59c957",
    url = "https://plugins.jetbrains.com/files/9568/139071/go-212.5457.20.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2021_3",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "80513699dae2902907af3ecf3d0b6a5d5d0c522f9853a7be2d6f6187bfc5fe8f",
    url = "https://plugins.jetbrains.com/files/9568/147799/go-213.5744.223.zip",
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
    name = "scala_2020_3",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "d6411ae778eea6b04d8e27365925448851dc83852a9ed52317094d3442c84d7e",
    url = "https://plugins.jetbrains.com/files/1347/113954/scala-intellij-bin-2020.3.23.zip",
)

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2021_1",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "38323791765fc3738a1fedf126d98819a5309e758c0f9c9d1811526886c7593a",
    url = "https://plugins.jetbrains.com/files/1347/120940/scala-intellij-bin-2021.1.21.zip",
)

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2021_2",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "b67c9221a66a3851c10096149ffcb8ea22a04472c62b131cfbf7facfd0a64d4a",
    url = "https://plugins.jetbrains.com/files/1347/150857/scala-intellij-bin-2021.2.29.zip",
)

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2021_3",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "025a27c713eb7e1e5295807eca07b5ef23878438cdb35c1da5e032a3ea410f07",
    url = "https://plugins.jetbrains.com/files/1347/147751/scala-intellij-bin-2021.3.14.zip",
)

# The plugin api for Android Studio 2020.3. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_2020_3",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio203",
    sha256 = "344d858235ed5d3095ac25916a4a8f8730069f76e5a5fd0eba02522af88f541b",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2020.3.1.26/android-studio-2020.3.1.26-linux.tar.gz",
)

# The plugin api for android_studio_2021_1. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_2021_1",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio211",
    sha256 = "dc2334357c5026ba7afee7a9ac61c97c127e3d01d1c13e5d125df4df4ac3b4c0",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2021.1.1.19/android-studio-2021.1.1.19-linux.tar.gz",
)

# The plugin api for android_studio_2021_2. This is required to build ASwB,
# and run integration tests.
http_archive(
    name = "android_studio_2021_2",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio212",
    sha256 = "08a4b75158b5c100578afebf20090cc68c4d0bdcf0223c85cc524d0c4f46debb",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2021.2.1.6/android-studio-2021.2.1.6-linux.tar.gz",
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

jvm_maven_import_external(
    name = "night_config_core",
    artifact = "com.electronwill.night-config:core:3.6.5",
    artifact_sha256 = "d47fd261e4c20c6f800a0f354644b5e83840347dde17d46537a9cb1c5a4207ba",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "night_config_toml",
    artifact = "com.electronwill.night-config:toml:3.6.5",
    artifact_sha256 = "12774bf07aeb893044fba8c38d80419f6f3adc0a28cc3b88325018dcf12e8bdf",
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
    sha256 = "ccf19e8f966022eaaca64da559c6140b23409829cb315f2eff5dc3e757fb6ad8",
    strip_prefix = "rules_scala-e4560ac332e9da731c1e50a76af2579c55836a5c",
    urls = ["https://github.com/bazelbuild/rules_scala/archive/e4560ac332e9da731c1e50a76af2579c55836a5c.zip"],
)

load("@io_bazel_rules_scala//:scala_config.bzl", "scala_config")

scala_config()

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")

scala_repositories()

load("@io_bazel_rules_scala//scala:toolchains.bzl", "scala_register_toolchains")

scala_register_toolchains()

load("@io_bazel_rules_scala//testing:scalatest.bzl", "scalatest_repositories", "scalatest_toolchain")

scalatest_repositories()

scalatest_toolchain()

# LICENSE: The Apache Software License, Version 2.0
rules_kotlin_version = "v1.5.0-beta-3"

rules_kotlin_sha = "58edd86f0f3c5b959c54e656b8e7eb0b0becabd412465c37a2078693c2571f7f"

http_archive(
    name = "io_bazel_rules_kotlin",
    sha256 = rules_kotlin_sha,
    urls = ["https://github.com/bazelbuild/rules_kotlin/releases/download/%s/rules_kotlin_release.tgz" % rules_kotlin_version],
)

load("@io_bazel_rules_kotlin//kotlin:repositories.bzl", "kotlin_repositories")
load("@io_bazel_rules_kotlin//kotlin:core.bzl", "kt_register_toolchains")

kotlin_repositories()

kt_register_toolchains()

# Without this dependency, when a test that uses Google truth fails, instead of
# the textual difference we get java.lang.NoClassDefFoundError: difflib/DiffUtils
jvm_maven_import_external(
    name = "diffutils",
    artifact = "com.googlecode.java-diff-utils:diffutils:1.2.1",
    artifact_sha256 = "c98697c3b8dd745353cd0a83b109c1c999fec43b4a5cedb2f579452d8da2c171",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

# Dependency needed for kotlin coroutines library
jvm_maven_import_external(
    name = "kotlinx_coroutines",
    artifact = "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2",
    artifact_sha256 = "4cd24a06b2a253110d8afd250e9eec6c6faafea6463d740824743d637e761f12",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

# Dependency needed for kotlin coroutines test library
jvm_maven_import_external(
    name = "kotlinx_coroutines_test",
    artifact = "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.4.2",
    artifact_sha256 = "2e3091a94b8b822c9b68c4dc92ad6a6b0e39e2245b0fc75862de20f5a7a71e9a",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

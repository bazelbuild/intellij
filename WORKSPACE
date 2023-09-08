workspace(name = "intellij_with_bazel")

load("@bazel_tools//tools/build_defs/repo:git.bzl", "new_git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for IntelliJ 2021.2. This is required to build IJwB,
# and run integration tests.
http_archive(
    name = "intellij_ce_2021_2",
    build_file = "@//intellij_platform_sdk:BUILD.idea212",
    sha256 = "aa38bf2f86b570ce9cac14b01f7e3bf8f592d05641384e7ecedde13cbfa6491a",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2021.2.4/ideaIC-2021.2.4.zip",
)

# The plugin api for intellij_ce_2021_3. This is required to build IJwB and run integration tests.
http_archive(
    name = "intellij_ce_2021_3",
    build_file = "@//intellij_platform_sdk:BUILD.idea213",
    sha256 = "7686d43fe0ea621718c1c9816460028146586ec10de1420500fc847edc603bb9",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2021.3.3/ideaIC-2021.3.3.zip",
)

# The plugin api for intellij_ce_2022_1. This is required to build IJwB and run integration tests.
http_archive(
    name = "intellij_ce_2022_1",
    build_file = "@//intellij_platform_sdk:BUILD.idea221",
    sha256 = "dc45e4c689a76c3022191a96fc3461333f177d62ab8d3e57e2cb2cc916ed9080",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2022.1.3/ideaIC-2022.1.3.zip",
)

# The plugin api for intellij_ce_2022_2. This is required to build IJwB and run integration tests.
http_archive(
    name = "intellij_ce_2022_2",
    build_file = "@//intellij_platform_sdk:BUILD.idea222",
    sha256 = "19cf087718400dbc5a90c6423aa71ebfbfe1c504e8fc399034b864cb6d2e7275",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2022.2.5/ideaIC-2022.2.5.zip",
)

# The plugin api for intellij_ce_2022_3. This is required to build IJwB and run integration tests.
http_archive(
    name = "intellij_ce_2022_3",
    build_file = "@//intellij_platform_sdk:BUILD.idea223",
    sha256 = "f6ea9aee6dec73b55ea405b37402394095be3c658d1c2707a8f30ac848974eac",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2022.3/ideaIC-2022.3.zip",
)

# The plugin api for intellij_ce_2023_1. This is required to build IJwB and run integration tests.
IC_231_SHA = "453980e0f2f56cf799df30e4378c65f3645ab1870276d13b1b7b5ebfbfb3e6e0"

IC_231_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2023.1.5/ideaIC-2023.1.5.zip"

http_archive(
    name = "intellij_ce_2023_1",
    build_file = "@//intellij_platform_sdk:BUILD.idea231",
    sha256 = IC_231_SHA,
    url = IC_231_URL,
)

# The plugin api for intellij_ce_2023_2. This is required to build IJwB and run integration tests.
IC_232_SHA = "c9fd992d9c1510627bb07c5f1dc73cbe15b903fff8d8bcf71ef895580b3acdae"

IC_232_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2023.2/ideaIC-2023.2.zip"

http_archive(
    name = "intellij_ce_2023_2",
    build_file = "@//intellij_platform_sdk:BUILD.idea232",
    sha256 = IC_232_SHA,
    url = IC_232_URL,
)

# The plugin api for IntelliJ UE 2021.2. This is required to run UE-specific
# integration tests.
http_archive(
    name = "intellij_ue_2021_2",
    build_file = "@//intellij_platform_sdk:BUILD.ue212",
    sha256 = "f5e942e090693c139dda22e798823285e22d7b31aaad5d52c23a370a6e91ec7d",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2021.2.4/ideaIU-2021.2.4.zip",
)

# The plugin api for intellij_ue_2021_3. This is required to run UE-specific integration tests.
http_archive(
    name = "intellij_ue_2021_3",
    build_file = "@//intellij_platform_sdk:BUILD.ue213",
    sha256 = "fc5ce48e614d5c083270a892cd5b38c9300f18aac41e1e0c7d15c518e978e96a",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2021.3.3/ideaIU-2021.3.3.zip",
)

# The plugin api for intellij_ue_2022_1. This is required to run UE-specific integration tests.
http_archive(
    name = "intellij_ue_2022_1",
    build_file = "@//intellij_platform_sdk:BUILD.ue221",
    sha256 = "598e085c98283c3206d9b755e6ef5f3321a3a11b1e5affa740276e9e3b0bd1f0",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2022.1.3/ideaIU-2022.1.3.zip",
)

# The plugin api for intellij_ue_2022_2. This is required to run UE-specific integration tests.
http_archive(
    name = "intellij_ue_2022_2",
    build_file = "@//intellij_platform_sdk:BUILD.ue222",
    sha256 = "557eb6ddab79894ea3b96f072b7ab797b7733329c0ae03b3701fb098e0ebb63a",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2022.2.5/ideaIU-2022.2.5.zip",
)

# The plugin api for intellij_ue_2022_3. This is required to run UE-specific integration tests.
http_archive(
    name = "intellij_ue_2022_3",
    build_file = "@//intellij_platform_sdk:BUILD.ue223",
    sha256 = "0b17ea16e70290d912b6be246460907643c23f33ae2c22331084818025c2b297",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2022.3/ideaIU-2022.3.zip",
)

# The plugin api for intellij_ue_2023_1. This is required to run UE-specific integration tests.
IU_231_SHA = "c6142e5b060a1f48257b3bdec840367e889d5ae80f8cf355dccd69524484ee6f"

IU_231_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2023.1.5/ideaIU-2023.1.5.zip"

http_archive(
    name = "intellij_ue_2023_1",
    build_file = "@//intellij_platform_sdk:BUILD.ue231",
    sha256 = IU_231_SHA,
    url = IU_231_URL,
)

# The plugin api for intellij_ue_2023_2. This is required to run UE-specific integration tests.
IU_232_SHA = "e8587ec0344e6369ae3e986446cda6e9e738776a3b3becabea4cc81f633dffc2"

IU_232_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2023.2/ideaIU-2023.2.zip"

http_archive(
    name = "intellij_ue_2023_2",
    build_file = "@//intellij_platform_sdk:BUILD.ue232",
    sha256 = IU_232_SHA,
    url = IU_232_URL,
)

# The plugin api for clion_2021_2. This is required to build CLwB, and run integration tests.
http_archive(
    name = "clion_2021_2",
    build_file = "@//intellij_platform_sdk:BUILD.clion212",
    sha256 = "1b9a882aa703303dead8b9459bd8d4f2572bd977d46dce99af96c1647231da2c",
    url = "https://download.jetbrains.com/cpp/CLion-2021.2.4.tar.gz",
)

# The plugin api for clion_2021_3. This is required to build CLwB, and run integration tests.
http_archive(
    name = "clion_2021_3",
    build_file = "@//intellij_platform_sdk:BUILD.clion213",
    sha256 = "f3b0b9e0dd0cd4aebef5d424e7a22868c732daad47d6c94f73630cef449ccf78",
    url = "https://download.jetbrains.com/cpp/CLion-2021.3.4.tar.gz",
)

# The plugin api for clion_2022_1. This is required to build CLwB\, and run integration tests.
http_archive(
    name = "clion_2022_1",
    build_file = "@//intellij_platform_sdk:BUILD.clion221",
    sha256 = "6f0234d41c4ca1cf8eaa4ea5585ba4cfc17d86c16c78edc59501e0ca05a80d56",
    url = "https://download.jetbrains.com/cpp/CLion-2022.1.3.tar.gz",
)

# The plugin api for clion_2022_2. This is required to build CLwB\, and run integration tests.
http_archive(
    name = "clion_2022_2",
    build_file = "@//intellij_platform_sdk:BUILD.clion222",
    sha256 = "94ffbdf82606f2f90618c1fdb89432d627e7f24ae158b36a591da2c303047436",
    url = "https://download.jetbrains.com/cpp/CLion-2022.2.tar.gz",
)

# The plugin api for clion_2022_3. This is required to build CLwB\, and run integration tests.
http_archive(
    name = "clion_2022_3",
    build_file = "@//intellij_platform_sdk:BUILD.clion223",
    sha256 = "5c248465a99f7286e7863ccc4fbd6772af890b57d71a02690e20031aa16d7957",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/clion/clion/2022.3/clion-2022.3.zip",
)

# The plugin api for clion_2023_1. This is required to build CLwB\, and run integration tests.
CLION_231_SHA = "acc45ab155940ed75a8f302772a46eb1c979aa8329ae7ccbd8151d4585a14a43"

CLION_231_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/clion/clion/2023.1.5/clion-2023.1.5.zip"

http_archive(
    name = "clion_2023_1",
    build_file = "@//intellij_platform_sdk:BUILD.clion231",
    sha256 = CLION_231_SHA,
    url = CLION_231_URL,
)

# The plugin api for clion_2023_2. This is required to build CLwB\, and run integration tests.
CLION_232_SHA = "152c7814cf16c4ffda16edfc6c0827cd6a031e6c381f034260ccc806a406b3e3"

CLION_232_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/clion/clion/2023.2/clion-2023.2.zip"

http_archive(
    name = "clion_2023_2",
    build_file = "@//intellij_platform_sdk:BUILD.clion232",
    sha256 = CLION_232_SHA,
    url = CLION_232_URL,
)

_PYTHON_CE_BUILD_FILE = """
java_import(
    name = "python",
    jars = ["python-ce/lib/python-ce.jar"],
    visibility = ["//visibility:public"],
)
filegroup(
  name = "python_helpers",
  srcs = glob(["python-ce/helpers/**/*"]),
  visibility = ["//visibility:public"],
)
"""

#TODO(ymoh): remove with the removal of 2021.1 Python plugin
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
    sha256 = "ce110ae1a5d3787bc85ae88d67fa2faa2be959a3e8acfc3567f8ed7b64c9151a",
    url = "https://plugins.jetbrains.com/files/7322/151370/python-ce-212.5712.43.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2021_3",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "47df4c32a19354efcc2d8171de85083e8e43b849c066bb979ed313b6309de08b",
    url = "https://plugins.jetbrains.com/files/7322/162748/python-ce-213.7172.26.zip",
)

# Python plugin for IntelliJ CE. Required at compile-time for python-specific features.
http_archive(
    name = "python_2022_1",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "1b0fb6824a7db252dfe3fd4eb638470bb96db4712bf1347560acee20eac1e8bc",
    url = "https://plugins.jetbrains.com/files/7322/187811/python-ce-221.5921.27.zip",
)

http_archive(
    name = "python_2022_2",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "aaae5ea44b5ad18793f8de63c00dce0371d91c14f7381260d19c4adaf4f9c9bf",
    url = "https://plugins.jetbrains.com/files/7322/305491/python-ce-222.4554.5.zip",
)

http_archive(
    name = "python_2022_3",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "65db7c364a3f1756cf07fb161ff4eb67fd8f8612a8c3da812b2f9ba5b2d6e13d",
    url = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/PythonCore/223.7571.182/PythonCore-223.7571.182.zip",
)

PYTHON_PLUGIN_231_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/PythonCore/231.9225.4/PythonCore-231.9225.4.zip"

PYTHON_PLUGIN_231_SHA = "bb9fe55fc483b4da1f6062c764ebd076d0de9f913c924db295f2bd2f05353777"

http_archive(
    name = "python_2023_1",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = PYTHON_PLUGIN_231_SHA,
    url = PYTHON_PLUGIN_231_URL,
)

PYTHON_PLUGIN_232_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/PythonCore/232.8660.185/PythonCore-232.8660.185.zip"

PYTHON_PLUGIN_232_SHA = "ac69daca683885b62b8cd3d36308fc96363860b227d5d0e764243020fe6979dd"

http_archive(
    name = "python_2023_2",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = PYTHON_PLUGIN_232_SHA,
    url = PYTHON_PLUGIN_232_URL,
)

http_archive(
    name = "python_2023_1",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "825c30d2cbcce405fd18fddf356eb1f425607e9c780f8eff95d21ac23f8d90fd",
    url = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/PythonCore/231.8770.65/PythonCore-231.8770.65.zip",
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
    name = "go_2021_2",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "5c868f2be8feb552aa4f9edb2a3c48db68193eb49ba50ca0a0976f4b9de82c67",
    url = "https://plugins.jetbrains.com/files/9568/149614/go-212.5712.14.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2021_3",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "12c3acf0e75f8d7fb5655e9400faa26bbc7b314c515c4a3ca9e6bb8c3a130a58",
    url = "https://plugins.jetbrains.com/files/9568/160433/go-213.7172.6.zip",
)

# Go plugin for IntelliJ UE. Required at compile-time for Bazel integration.
http_archive(
    name = "go_2022_1",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "4219a3b76c985ad1066d4ff99f516422bcbbfda2feba6a950e8bb6c5e544e3ea",
    url = "https://plugins.jetbrains.com/files/9568/185980/go-221.5921.16.zip",
)

http_archive(
    name = "go_2022_2",
    build_file_content = _GO_BUILD_FILE,
    sha256 = "cc19cc418b512420643c8c94eaf2cf1775de3183b1a8d0c2703959fcc4275afd",
    url = "https://plugins.jetbrains.com/files/9568/256314/go-222.4459.24.zip",
)

_GO_BUILD_FILE_223 = """
java_import(
    name = "go",
    jars = glob(["go-plugin/lib/*.jar"]),
    visibility = ["//visibility:public"],
)
"""

http_archive(
    name = "go_2022_3",
    build_file_content = _GO_BUILD_FILE_223,
    sha256 = "11d30e00aa21fc8c7aa47df3743c0180058556cbb73292c712e151a0c3d59908",
    url = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.jetbrains.plugins.go/223.7571.182/org.jetbrains.plugins.go-223.7571.182.zip",
)

GO_PLUGIN_231_SHA = "a8d277125ec1f6a2ba0190a7c456d6c39057e563596874ec432a8f278b3d6976"

GO_PLUGIN_231_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.jetbrains.plugins.go/231.9392.1/org.jetbrains.plugins.go-231.9392.1.zip"

http_archive(
    name = "go_2023_1",
    build_file_content = _GO_BUILD_FILE_223,
    sha256 = GO_PLUGIN_231_SHA,
    url = GO_PLUGIN_231_URL,
)

GO_PLUGIN_232_SHA = "62deced01d20e59112e9ee6bbae05682c85a97ed4ff52b27688f7f10efd3c8d4"

GO_PLUGIN_232_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.jetbrains.plugins.go/232.8660.142/org.jetbrains.plugins.go-232.8660.142.zip"

http_archive(
    name = "go_2023_2",
    build_file_content = _GO_BUILD_FILE_223,
    sha256 = GO_PLUGIN_232_SHA,
    url = GO_PLUGIN_232_URL,
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
    name = "scala_2021_2",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "8d9c2831920fb69a52898598dc7f78c455001b3ebd1956b972757ffae7c0f056",
    url = "https://plugins.jetbrains.com/files/1347/153522/scala-intellij-bin-2021.2.30.zip",
)

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2021_3",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "c14a15321060260360c3b8d41e9ef4080b5e552d2d0eb30ce6b141da08ee4764",
    url = "https://plugins.jetbrains.com/files/1347/160380/scala-intellij-bin-2021.3.20.zip",
)

# Scala plugin for IntelliJ CE. Required at compile-time for scala-specific features.
http_archive(
    name = "scala_2022_1",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "27d2ce5c1cddf497c685d30bcbc13b7e0d6691704fbfcc01fb8f4d832f0be9a1",
    url = "https://plugins.jetbrains.com/files/1347/182909/scala-intellij-bin-2022.1.16.zip",
)

http_archive(
    name = "scala_2022_2",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "67e0634b4a1c9431fde6f804da9714c935382c1442f541000e7dcd598d74bde7",
    url = "https://plugins.jetbrains.com/files/1347/202220/scala-intellij-bin-2022.2.659.zip",
)

http_archive(
    name = "scala_2022_3",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = "f028ac7263433c8692d9d4c92aaba9e114fc75f6299d4d86817db371409f74f3",
    url = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.intellij.scala/2022.3.13/org.intellij.scala-2022.3.13.zip",
)

SCALA_PLUGIN_231_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.intellij.scala/2023.1.19/org.intellij.scala-2023.1.19.zip"

SCALA_PLUGIN_231_SHA = "5aec9578129a6a2eedc8e1836f7b7d772d2e78598357d632426e2be930fbe178"

http_archive(
    name = "scala_2023_1",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = SCALA_PLUGIN_231_SHA,
    url = SCALA_PLUGIN_231_URL,
)

SCALA_PLUGIN_232_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.intellij.scala/2023.2.17/org.intellij.scala-2023.2.17.zip"

SCALA_PLUGIN_232_SHA = "069751b0add6dc9acfa60eaffae784ca645efb4514fcd9e5f5029f9509a3c585"

http_archive(
    name = "scala_2023_2",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = SCALA_PLUGIN_232_SHA,
    url = SCALA_PLUGIN_232_URL,
)

# The plugin api for android_studio_2022_2 android_studio. This is required to build ASwB and run integration tests
http_archive(
    name = "android_studio_2022_2",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio222",
    sha256 = "cdd852c4499b5f7402df44dfc69e8ca418ffc9a684caab34047476fd2cb24efc",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2022.2.1.18/android-studio-2022.2.1.18-linux.tar.gz",
)

# The plugin api for android_studio_dev android_studio. This is required to build ASwB and run integration tests
http_archive(
    name = "android_studio_dev",
    build_file = "@//intellij_platform_sdk:BUILD.android_studiodev",
    sha256 = "cb3f0494220f92dd85399adfb8655a1a2bd81b238d26e63a8bbd8bde95a0fccf",
    url = "https://android-build",
)

# The plugin api for android_studio_2023_1 android_studio. This is required to build ASwB and run integration tests
http_archive(
    name = "android_studio_2023_1",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio231",
    sha256 = "8bc1cda24e696bc60dad4426afd08d46643d3a815e75f2ebe6133482beff3df0",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2023.1.1.16/android-studio-2023.1.1.16-linux.tar.gz",
)

# The plugin api for android_studio_2022_3 android_studio. This is required to build ASwB and run integration tests
http_archive(
    name = "android_studio_2022_3",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio223",
    sha256 = "250625dcab183e0c68ebf12ef8a522af7369527d76f1efc704f93c05b02ffa9e",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2022.3.1.19/android-studio-2022.3.1.19-linux.tar.gz",
)

http_archive(
    name = "rules_java",
    urls = [
        "https://github.com/bazelbuild/rules_java/releases/download/6.5.1/rules_java-6.5.1.tar.gz",
    ],
    sha256 = "7b0d9ba216c821ee8697dedc0f9d0a705959ace462a3885fe9ba0347ba950111",
)

JUNIT_ARTIFACT = "junit:junit:4.13.2"

JUNIT_SHA = "8e495b634469d64fb8acfa3495a065cbacc8a0fff55ce1e31007be4c16dc57d3"

# LICENSE: Common Public License 1.0
jvm_maven_import_external(
    name = "junit",
    artifact = JUNIT_ARTIFACT,
    artifact_sha256 = JUNIT_SHA,
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

# objenesis is a dependency of mockito https://mvnrepository.com/artifact/org.mockito/mockito-core/3.3.0
# Before 2023.2 it was delivered with IntelliJ bundle in lib/app.jar, but this no longer happens so we need
# to download it from Maven Central.
jvm_maven_import_external(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:3.3",
    artifact_sha256 = "02dfd0b0439a5591e35b708ed2f5474eb0948f53abf74637e959b8e4ef69bfeb",
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
    artifact = "com.google.errorprone:error_prone_annotations:2.13.1",
    artifact_sha256 = "f5ee2aac2ee6443789e1dee0f96e3c35d9f3c78891f54ed83f3cf918a1cde6d1",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "com_google_guava_guava",
    artifact = "com.google.guava:guava:31.1-jre",
    artifact_sha256 = "a42edc9cab792e39fe39bb94f3fca655ed157ff87a8af78e1d6ba5b07c4a00ab",
    server_urls = [
        "https://repo1.maven.org/maven2",
    ],
)

jvm_maven_import_external(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.9.1",
    artifact_sha256 = "378534e339e6e6d50b1736fb3abb76f1c15d1be3f4c13cec6d536412e23da603",
    server_urls = [
        "https://repo1.maven.org/maven2",
    ],
)

jvm_maven_import_external(
    name = "flogger",
    artifact = "com.google.flogger:flogger:0.7.4",
    artifact_sha256 = "77aac11b3c26e1e184dcfe79c55ac6e27967a6dfe1c04146125176940bc64a55",
    server_urls = [
        "https://repo1.maven.org/maven2",
    ],
)

jvm_maven_import_external(
    name = "flogger_system_backend",
    artifact = "com.google.flogger:flogger-system-backend:0.7.4",
    artifact_sha256 = "fd66f2615a9d8fe1b2274f1b5005a5555a0cd63cdfdab2ca9500e6eb81dc5f63",
    server_urls = [
        "https://repo1.maven.org/maven2",
    ],
)

http_archive(
    name = "build_bazel_rules_android",
    sha256 = "cd06d15dd8bb59926e4d65f9003bfc20f9da4b2519985c27e190cddc8b7a7806",
    strip_prefix = "rules_android-0.1.1",
    urls = ["https://github.com/bazelbuild/rules_android/archive/v0.1.1.zip"],
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
    name = "rules_python",
    sha256 = "ffc7b877c95413c82bfd5482c017edcf759a6250d8b24e82f41f3c8b8d9e287e",
    strip_prefix = "rules_python-0.19.0",
    url = "https://github.com/bazelbuild/rules_python/releases/download/0.19.0/rules_python-0.19.0.tar.gz",
)

load("@rules_python//python:repositories.bzl", "py_repositories")

py_repositories()

http_archive(
    name = "bazel_skylib",
    sha256 = "74d544d96f4a5bb630d465ca8bbcfe231e3594e5aae57e1edbf17a6eb3ca2506",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-skylib/releases/download/1.3.0/bazel-skylib-1.3.0.tar.gz",
        "https://github.com/bazelbuild/bazel-skylib/releases/download/1.3.0/bazel-skylib-1.3.0.tar.gz",
    ],
)

# specify a minimum version for bazel otherwise users on old versions may see
# unexpressive errors when new features are used
load("@bazel_skylib//lib:versions.bzl", "versions")

versions.check(minimum_bazel_version = "5.2.0")

load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")

bazel_skylib_workspace()

http_archive(
    name = "contrib_rules_bazel_integration_test",
    sha256 = "20d670bb614d311a2a0fc8af53760439214731c3d5be2d9b0a197dccc19583f5",
    strip_prefix = "rules_bazel_integration_test-0.9.0",
    urls = [
        "http://github.com/bazel-contrib/rules_bazel_integration_test/archive/v0.9.0.tar.gz",
    ],
)

load("@contrib_rules_bazel_integration_test//bazel_integration_test:deps.bzl", "bazel_integration_test_rules_dependencies")

bazel_integration_test_rules_dependencies()

load("@contrib_rules_bazel_integration_test//bazel_integration_test:defs.bzl", "bazel_binaries")

bazel_binaries(versions = [
    "4.0.0",
    "6.0.0",
])

# LICENSE: The Apache Software License, Version 2.0
http_archive(
    name = "rules_proto",
    sha256 = "dc3fb206a2cb3441b485eb1e423165b231235a1ea9b031b4433cf7bc1fa460dd",
    strip_prefix = "rules_proto-5.3.0-21.7",
    urls = [
        "https://github.com/bazelbuild/rules_proto/archive/refs/tags/5.3.0-21.7.tar.gz",
    ],
)

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")

rules_proto_dependencies()

rules_proto_toolchains()

# LICENSE: The Apache Software License, Version 2.0
rules_scala_version = "a42f009ded929070d5c412284c50ba08f0f9e8b8"

http_archive(
    name = "io_bazel_rules_scala",
    sha256 = "0074836b631caaf552fd7013d49f18fa5f0a27c86bb1b88bd3fa9371fa36b2c9",
    strip_prefix = "rules_scala-%s" % rules_scala_version,
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version,
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
rules_kotlin_version = "1.7.0-RC-1"

rules_kotlin_sha = "68b910730026921814d3a504ccbe9adaac9938983d940e626523e6e4ecfb0355"

http_archive(
    name = "io_bazel_rules_kotlin",
    sha256 = rules_kotlin_sha,
    urls = ["https://github.com/bazelbuild/rules_kotlin/releases/download/v%s/rules_kotlin_release.tgz" % rules_kotlin_version],
)

load("@io_bazel_rules_kotlin//kotlin:repositories.bzl", "kotlin_repositories")

kotlin_repositories()

load("@io_bazel_rules_kotlin//kotlin:core.bzl", "kt_register_toolchains")

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
    artifact = "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.2",
    artifact_sha256 = "09aac136027678db2d3c2696696202719af9213ba17ae076f4c4421008885bcb",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

# Dependency needed for kotlin coroutines test library
jvm_maven_import_external(
    name = "kotlinx_coroutines_test",
    artifact = "org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.6.2",
    artifact_sha256 = "6eb5c29f60fcacde882b1d393bf9a2fe9535bece1c707396fdbd755559dc043d",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

http_archive(
    name = "io_bazel_rules_go",
    sha256 = "6b65cb7917b4d1709f9410ffe00ecf3e160edf674b78c54a894471320862184f",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.39.0/rules_go-v0.39.0.zip",
        "https://github.com/bazelbuild/rules_go/releases/download/v0.39.0/rules_go-v0.39.0.zip",
    ],
)

# gRPC Java
http_archive(
    name = "io_grpc_grpc_java",
    sha256 = "4a021ea9ebb96f5841a135c168209cf2413587a0f8ce71a2bf37b3aad847b0d0",
    strip_prefix = "grpc-java-1.57.1",
    url = "https://github.com/grpc/grpc-java/archive/v1.57.1.tar.gz",
)

jvm_maven_import_external(
    name = "io_netty_netty_common",
    artifact = "io.netty:netty-common:4.1.96.Final",
    artifact_sha256 = "da104e80db830922eaf860eb1c5e957cd1d124068253d02e9c7a7843bc66427a",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "io_netty_netty_transport",
    artifact = "io.netty:netty-transport:4.1.96.Final",
    artifact_sha256 = "8fe3afbe8b094a7b9f1eb27becf1cf017e5572343c1744d2b6040d5f331e84e3",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "io_netty_netty_transport_native_epoll",
    artifact = "io.netty:netty-transport-classes-epoll:4.1.96.Final",
    artifact_sha256 = "1591b3ea061932677dc2bab6cb7d82e8f1837a52b3c781f4daa99984ec87a9cd",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "io_netty_netty_transport_native_unix_common",
    artifact = "io.netty:netty-transport-native-unix-common:4.1.96.Final",
    artifact_sha256 = "4f96297a06a544a4cdb6fe6af8b868640f100fa96969e2196be216bd41adef13",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "io_netty_netty_transport_classes_kqueue",
    artifact = "io.netty:netty-transport-classes-kqueue:4.1.96.Final",
    artifact_sha256 = "f2f1fab3b297aee20a3922c79b548c8b4b72bb10b635375434c108ee05f29430",
    licenses = ["notice"],  # Apache 2.0
    server_urls = ["https://repo1.maven.org/maven2"],
)

# io_grpc_grpc_java dependencies
load("@io_grpc_grpc_java//:repositories.bzl", "IO_GRPC_GRPC_JAVA_ARTIFACTS", "IO_GRPC_GRPC_JAVA_OVERRIDE_TARGETS", "grpc_java_repositories")

grpc_java_repositories()

# Java Maven-based repositories.
http_archive(
    name = "rules_jvm_external",
    sha256 = "cd1a77b7b02e8e008439ca76fd34f5b07aecb8c752961f9640dea15e9e5ba1ca",
    strip_prefix = "rules_jvm_external-4.2",
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/4.2.zip",
)

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")

rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")

rules_jvm_external_setup()

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = IO_GRPC_GRPC_JAVA_ARTIFACTS,
    generate_compat_repositories = True,
    override_targets = IO_GRPC_GRPC_JAVA_OVERRIDE_TARGETS,
    repositories = [
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
        "https://repository.mulesoft.org/nexus/content/repositories/public",
    ],
)

load("@maven//:compat.bzl", "compat_repositories")

compat_repositories()

# Register custom java 17 toolchain
register_toolchains("//:custom_java_17_toolchain_definition")

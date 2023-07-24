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
IC_231_SHA = "ad17f138a87789c9e281e774a4138b9131717e59b2f21ede17f49f9ffee10c11"

IC_231_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2023.1.4/ideaIC-2023.1.4.zip"

http_archive(
    name = "intellij_ce_2023_1",
    build_file = "@//intellij_platform_sdk:BUILD.idea231",
    sha256 = IC_231_SHA,
    url = IC_231_URL,
)

# The plugin api for intellij_ce_2023_2. This is required to build IJwB and run integration tests.
IC_232_SHA = "9feac2dc4f613dfc7051e96e9fab4fbbfe52737bc09376336f9641583886be1a"

IC_232_URL = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/idea/ideaIC/232.8660.142-EAP-SNAPSHOT/ideaIC-232.8660.142-EAP-SNAPSHOT.zip"

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
IU_231_SHA = "8dced0ba2410bf18af5f8fdb5cff151581a36b562eade7e67793ae73fdef4b28"

IU_231_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2023.1.4/ideaIU-2023.1.4.zip"

http_archive(
    name = "intellij_ue_2023_1",
    build_file = "@//intellij_platform_sdk:BUILD.ue231",
    sha256 = IU_231_SHA,
    url = IU_231_URL,
)

# The plugin api for intellij_ue_2023_2. This is required to run UE-specific integration tests.
IU_232_SHA = "544183a9cd9537a117784b3a60e9ffbc6f30d60c5da11357e56043125ddf1458"

IU_232_URL = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/idea/ideaIU/232.8660.142-EAP-SNAPSHOT/ideaIU-232.8660.142-EAP-SNAPSHOT.zip"

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
CLION_232_SHA = "9f33385561c5e136235b75041535e3f13e36d844dc7a3ebaf9009a256079b86f"

CLION_232_URL = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/clion/clion/232.8660.139-EAP-SNAPSHOT/clion-232.8660.139-EAP-SNAPSHOT.zip"

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

PYTHON_PLUGIN_232_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/PythonCore/232.8660.142/PythonCore-232.8660.142.zip"

PYTHON_PLUGIN_232_SHA = "09e95e7e2873b8f1692161fa62481bd6c49ef7225efaec99e28967ab13ae9bd5"

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

GO_PLUGIN_231_SHA = "20753c77b5b7ce8bf1ffc421efd648fb51adb7bd2bae40a0fce442726d92d6e0"

GO_PLUGIN_231_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.jetbrains.plugins.go/231.9225.16/org.jetbrains.plugins.go-231.9225.16.zip"

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

SCALA_PLUGIN_232_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.intellij.scala/2023.2.14/org.intellij.scala-2023.2.14.zip"

SCALA_PLUGIN_232_SHA = "b1c22b708936211ae9a259c1f92bc6637236aee770d8f4c96e4e2db7eb6089b4"

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
    sha256 = "9c670055da4f934cf4181df0813feb53818503f242f964fddb180fd1ced121b3",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2023.1.1.11/android-studio-2023.1.1.11-linux.tar.gz",
)

# The plugin api for android_studio_2022_3 android_studio. This is required to build ASwB and run integration tests
http_archive(
    name = "android_studio_2022_3",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio223",
    sha256 = "5b2e6289fb1c2f52a8ed9227c7fb80bf2272338712e8b7d3616c5d25b664ea9a",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2022.3.1.12/android-studio-2022.3.1.12-linux.tar.gz",
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
rules_scala_version = "30f8fbe042b835cc2173a474a6a2b360cd4d440f"

http_archive(
    name = "io_bazel_rules_scala",
    sha256 = "8ecb882997ebbaea341981c63c538674815cabb5b50a0a331ff770c597578715",
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

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
IC_232_SHA = "0267e8c5e1fed3d82ccfab973a57ad7e551cc0022d1198d87b1c5afaabc9d4ba"

IC_232_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2023.2.5/ideaIC-2023.2.5.zip"

http_archive(
    name = "intellij_ce_2023_2",
    build_file = "@//intellij_platform_sdk:BUILD.idea232",
    sha256 = IC_232_SHA,
    url = IC_232_URL,
)

# The plugin api for intellij_ce_2023_2. This is required to build IJwB and run integration tests.
IC_233_SHA = "15c589956355e1570d2710f4927663464c58767b352aa35f4ab96994a3a1d4b9"

IC_233_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2023.3.3/ideaIC-2023.3.3.zip"

http_archive(
    name = "intellij_ce_2023_3",
    build_file = "@//intellij_platform_sdk:BUILD.idea233",
    sha256 = IC_233_SHA,
    url = IC_233_URL,
)

# The plugin api for intellij_ce_2024_1. This is required to build IJwB and run integration tests.
IC_241_SHA = "a9d97b1260cf79194ea6b80dadaf60b7d50617902b433c809f3b66635c9a3fc8"

IC_241_URL = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/idea/ideaIC/241.11761.10-EAP-SNAPSHOT/ideaIC-241.11761.10-EAP-SNAPSHOT.zip"

http_archive(
    name = "intellij_ce_2024_1",
    build_file = "@//intellij_platform_sdk:BUILD.idea241",
    sha256 = IC_241_SHA,
    url = IC_241_URL,
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
IU_232_SHA = "334dade187a1f15a001fc968863ae857638d17e249db7db0416c6e659c48be81"

IU_232_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2023.2.5/ideaIU-2023.2.5.zip"

http_archive(
    name = "intellij_ue_2023_2",
    build_file = "@//intellij_platform_sdk:BUILD.ue232",
    sha256 = IU_232_SHA,
    url = IU_232_URL,
)

IU_233_SHA = "1cea8ace7717b32b44d12269afbf5db07c02ebb153a6fc08777a471b56dd8443"

IU_233_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIU/2023.3.3/ideaIU-2023.3.3.zip"

http_archive(
    name = "intellij_ue_2023_3",
    build_file = "@//intellij_platform_sdk:BUILD.ue233",
    sha256 = IU_233_SHA,
    url = IU_233_URL,
)

IU_241_SHA = "25aafaa194561ae41a707eefe602868ef962832f8f71d2f50a7192598c793252"

IU_241_URL = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/idea/ideaIU/241.11761.10-EAP-SNAPSHOT/ideaIU-241.11761.10-EAP-SNAPSHOT.zip"

http_archive(
    name = "intellij_ue_2024_1",
    build_file = "@//intellij_platform_sdk:BUILD.ue241",
    sha256 = IU_241_SHA,
    url = IU_241_URL,
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
CLION_232_SHA = "b756a2425fad72564f68a0cdfc0a6e7b74e0fe78e5f4b10456746d29d7dee978"

CLION_232_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/clion/clion/2023.2.2/clion-2023.2.2.zip"

http_archive(
    name = "clion_2023_2",
    build_file = "@//intellij_platform_sdk:BUILD.clion232",
    sha256 = CLION_232_SHA,
    url = CLION_232_URL,
)

CLION_233_SHA = "2672688ce6d5293d7342a0fce7889a34cd79302128a0ac8dd56dd297bb191ee2"

CLION_233_URL = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/clion/clion/2023.3.3/clion-2023.3.3.zip"

http_archive(
    name = "clion_2023_3",
    build_file = "@//intellij_platform_sdk:BUILD.clion233",
    sha256 = CLION_233_SHA,
    url = CLION_233_URL,
)

CLION_241_SHA = "a50325f3d67c071fb50bd7daffb840f527baf9ad70eb311ed00c2c0ce3199f1f"

CLION_241_URL = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/clion/clion/241.11761.23-EAP-SNAPSHOT/clion-241.11761.23-EAP-SNAPSHOT.zip"

http_archive(
    name = "clion_2024_1",
    build_file = "@//intellij_platform_sdk:BUILD.clion241",
    sha256 = CLION_241_SHA,
    url = CLION_241_URL,
)

DEVKIT_BUILD_FILE = """
java_import(
    name = "devkit",
    jars = ["devkit/lib/devkit.jar"],
    visibility = ["//visibility:public"],
)
"""

DEVKIT_233_SHA = "47e0226a58234c370276e1bbcbcb21fd5a2c3c6653b6c1adeedaf989648793fe"

DEVKIT_233_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/DevKit/233.14475.2/DevKit-233.14475.2.zip"

http_archive(
    name = "devkit_2023_3",
    build_file_content = DEVKIT_BUILD_FILE,
    sha256 = DEVKIT_233_SHA,
    url = DEVKIT_233_URL,
)

DEVKIT_241_SHA = "b14accea9fdecdf1235f96bdef3781e89ecaa94489a063fefd9eeb0775c28ed8"

DEVKIT_241_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/DevKit/241.11761.29/DevKit-241.11761.29.zip"

http_archive(
    name = "devkit_2024_1",
    build_file_content = DEVKIT_BUILD_FILE,
    sha256 = DEVKIT_241_SHA,
    url = DEVKIT_241_URL,
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

PYTHON_PLUGIN_232_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/PythonCore/232.10203.2/PythonCore-232.10203.2.zip"

PYTHON_PLUGIN_232_SHA = "027bc9e2322f21cb3093b3254035cfeaac587552f9db2f664b28280f172acfed"

http_archive(
    name = "python_2023_2",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = PYTHON_PLUGIN_232_SHA,
    url = PYTHON_PLUGIN_232_URL,
)

PYTHON_PLUGIN_233_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/PythonCore/233.14475.9/PythonCore-233.14475.9.zip"

PYTHON_PLUGIN_233_SHA = "3c473ca028ed5bb352fda2d9142671008ac4a77a4ccd29a2aac1e037df1daf55"

http_archive(
    name = "python_2023_3",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = PYTHON_PLUGIN_233_SHA,
    url = PYTHON_PLUGIN_233_URL,
)

http_archive(
    name = "python_2023_1",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "825c30d2cbcce405fd18fddf356eb1f425607e9c780f8eff95d21ac23f8d90fd",
    url = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/PythonCore/231.8770.65/PythonCore-231.8770.65.zip",
)

http_archive(
    name = "python_2023_2",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = "e744349f353568c18a9e11ec5e3a205f62bbdc1b65c9abc96783c479fe2aa51b",
    url = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/PythonCore/232.9921.47/PythonCore-232.9921.47.zip",
)

PYTHON_PLUGIN_241_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/PythonCore/241.11761.10/PythonCore-241.11761.10.zip"

PYTHON_PLUGIN_241_SHA = "c11f23d4aecb7c77e219f9e2e8c99c7e9fb2cd2bf45de7a770f984d2011243fb"

http_archive(
    name = "python_2024_1",
    build_file_content = _PYTHON_CE_BUILD_FILE,
    sha256 = PYTHON_PLUGIN_241_SHA,
    url = PYTHON_PLUGIN_241_URL,
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

GO_PLUGIN_232_SHA = "6682325b13d66b716fc9bc719821f3f7bad16f21ac4504cc4656265fee74ceb4"

GO_PLUGIN_232_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.jetbrains.plugins.go/232.10203.2/org.jetbrains.plugins.go-232.10203.2.zip"

http_archive(
    name = "go_2023_2",
    build_file_content = _GO_BUILD_FILE_223,
    sha256 = GO_PLUGIN_232_SHA,
    url = GO_PLUGIN_232_URL,
)

GO_PLUGIN_233_SHA = "5f03f7e88a816ed679051a1a2052b487aff3bc75c13575b7078e71a6e8e3a2c2"

GO_PLUGIN_233_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.jetbrains.plugins.go/233.14475.9/org.jetbrains.plugins.go-233.14475.9.zip"

http_archive(
    name = "go_2023_3",
    build_file_content = _GO_BUILD_FILE_223,
    sha256 = GO_PLUGIN_233_SHA,
    url = GO_PLUGIN_233_URL,
)

GO_PLUGIN_241_SHA = "76f0aeee55ad0dcd3a02f48a41ce78b0ef3a15ca6a59cb32fcdd079a8da2ba63"

GO_PLUGIN_241_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.jetbrains.plugins.go/241.11761.10/org.jetbrains.plugins.go-241.11761.10.zip"

http_archive(
    name = "go_2024_1",
    build_file_content = _GO_BUILD_FILE_223,
    sha256 = GO_PLUGIN_241_SHA,
    url = GO_PLUGIN_241_URL,
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

SCALA_PLUGIN_231_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.intellij.scala/2023.1.25/org.intellij.scala-2023.1.25.zip"

SCALA_PLUGIN_231_SHA = "b45dbb95ed5a0001f11638e7f03dd858d9e243994ca554a64e1df6b7290b2b81"

http_archive(
    name = "scala_2023_1",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = SCALA_PLUGIN_231_SHA,
    url = SCALA_PLUGIN_231_URL,
)

SCALA_PLUGIN_232_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.intellij.scala/2023.2.29/org.intellij.scala-2023.2.29.zip"

SCALA_PLUGIN_232_SHA = "6d189804f40ff604b5dea0175c90a2fed86d89d5de43bdaa1da748a793240632"

http_archive(
    name = "scala_2023_2",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = SCALA_PLUGIN_232_SHA,
    url = SCALA_PLUGIN_232_URL,
)

SCALA_PLUGIN_233_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.intellij.scala/2023.3.21/org.intellij.scala-2023.3.21.zip"

SCALA_PLUGIN_233_SHA = "18c33e5fba795010a27c74c87508544bd41e4b67bce36cc7ec20735092b581c5"

http_archive(
    name = "scala_2023_3",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = SCALA_PLUGIN_233_SHA,
    url = SCALA_PLUGIN_233_URL,
)

SCALA_PLUGIN_241_URL = "https://plugins.jetbrains.com/maven/com/jetbrains/plugins/org.intellij.scala/2024.1.4/org.intellij.scala-2024.1.4.zip"

SCALA_PLUGIN_241_SHA = "579d2c8d5ae9d250c97618775b48c4284ff401ff76d1ee7565d2cee6ebdb1936"

http_archive(
    name = "scala_2024_1",
    build_file_content = _SCALA_BUILD_FILE,
    sha256 = SCALA_PLUGIN_241_SHA,
    url = SCALA_PLUGIN_241_URL,
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

# The plugin api for android_studio_2023_2 android_studio. This is required to build ASwB and run integration tests
http_archive(
    name = "android_studio_2023_2",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio232",
    sha256 = "f2ccc445fb5c87525627ae81725241ab90d9707d577f5732563d3c5a49cba12f",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2023.2.1.14/android-studio-2023.2.1.14-linux.tar.gz",
)

# The plugin api for android_studio_2023_1 android_studio. This is required to build ASwB and run integration tests
http_archive(
    name = "android_studio_2023_1",
    build_file = "@//intellij_platform_sdk:BUILD.android_studio231",
    sha256 = "139d0dbb4909353b68fbf55c09b6d31a34512044a9d4f02ce0f1a9accca128f9",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2023.1.1.28/android-studio-2023.1.1.28-linux.tar.gz",
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
    sha256 = "7b0d9ba216c821ee8697dedc0f9d0a705959ace462a3885fe9ba0347ba950111",
    urls = [
        "https://github.com/bazelbuild/rules_java/releases/download/6.5.1/rules_java-6.5.1.tar.gz",
    ],
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
    artifact = "com.google.errorprone:error_prone_annotations:2.24.1",
    artifact_sha256 = "19fe2f7155d20ea093168527999da98108103ee546d1e8b726bc4b27c31a3c30",
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
http_archive(
    name = "io_bazel_rules_kotlin",
    sha256 = "5766f1e599acf551aa56f49dab9ab9108269b03c557496c54acaf41f98e2b8d6",
    url = "https://github.com/bazelbuild/rules_kotlin/releases/download/v1.9.0/rules_kotlin-v1.9.0.tar.gz",
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

# needed for cpp tests
http_archive(
    name = "com_google_absl",
    sha256 = "987ce98f02eefbaf930d6e38ab16aa05737234d7afbab2d5c4ea7adbe50c28ed",
    strip_prefix = "abseil-cpp-20230802.1",
    urls = [
         "https://github.com/abseil/abseil-cpp/archive/refs/tags/20230802.1.tar.gz",
    ],
)

# gRPC Java
http_archive(
    name = "io_grpc_grpc_java",
    sha256 = "3bcf6be49fc7ab8187577a5211421258cb8e6d179f46023cc82e42e3a6188e51",
    strip_prefix = "grpc-java-1.59.0",
    url = "https://github.com/grpc/grpc-java/archive/refs/tags/v1.59.0.tar.gz",
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

# Usually, we'd get this from the JetBrains SDK, but the bundled one not aware of Bazel platforms,
# so it fails on certain setups.
jvm_maven_import_external(
    name = "jna",
    artifact = "net.java.dev.jna:jna:5.13.0",
    artifact_sha256 = "66d4f819a062a51a1d5627bffc23fac55d1677f0e0a1feba144aabdd670a64bb",
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

workspace(name = "intellij_with_bazel")

# The plugin api for IntelliJ 2016.1.3. This is required to build IJwB,
# and run integration tests.
new_http_archive(
    name = "intellij_latest",
    build_file = "remote_platform_sdks/BUILD.idea",
    sha256 = "d1cd3f9fd650c00ba85181da6d66b4b80b8e48ce5f4f15b5f4dc67453e96a179",
    url = "https://download.jetbrains.com/idea/ideaIC-2016.1.3.tar.gz",
)

# The plugin api for CLion 2016.1.3. This is required to build CLwB,
# and run integration tests.
new_http_archive(
    name = "clion_latest",
    build_file = "remote_platform_sdks/BUILD.clion",
    sha256 = "470063f1bb65ba03c6e1aba354cb81e2c04bd280d9b8da98622be1ba6b0a9c88",
    url = "https://download.jetbrains.com/cpp/CLion-2016.1.3.tar.gz",
)

# The plugin api for Android Studio 2.2. preview 4. This is required to build ASwB,
# and run integration tests.
new_http_archive(
    name = "android_studio_latest",
    build_file = "remote_platform_sdks/BUILD.android_studio",
    sha256 = "530b630914b42f9ad9f5442a36b421214838443429a4a1b96194d45a5d586f17",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2.2.0.3/android-studio-ide-145.3001415-linux.zip",
)

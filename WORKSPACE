workspace(name = "intellij_with_bazel")

# The plugin api for IntelliJ 2016.1.3. This is required to build IJwB,
# and run integration tests.
new_http_archive(
    name = "intellij_latest",
    build_file = "remote_platform_sdks/BUILD.idea",
    sha256 = "b64767d87dbaae7e1511666fc032cf356e326770090e3a141969909d09670345",
    url = "https://download.jetbrains.com/idea/ideaIC-2016.1.4.tar.gz",
)

# The plugin api for CLion 2016.1.3. This is required to build CLwB,
# and run integration tests.
new_http_archive(
    name = "clion_latest",
    build_file = "remote_platform_sdks/BUILD.clion",
    sha256 = "7ca06c23805632849855679434742f575e0475f726b8f9b345d71bc4e50d486d",
    url = "http://download.jetbrains.com/cpp/CLion-2016.1.2b.tar.gz",
)

# The plugin api for Android Studio 2.2. preview 4. This is required to build ASwB,
# and run integration tests.
new_http_archive(
    name = "android_studio_latest",
    build_file = "remote_platform_sdks/BUILD.android_studio",
    sha256 = "530b630914b42f9ad9f5442a36b421214838443429a4a1b96194d45a5d586f17",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2.2.0.3/android-studio-ide-145.3001415-linux.zip",
)

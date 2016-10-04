workspace(name = "intellij_with_bazel")

# The plugin api for IntelliJ 2016.1.3. This is required to build IJwB,
# and run integration tests.
new_http_archive(
    name = "intellij_latest",
    build_file = "intellij_platform_sdk/BUILD.idea",
    url = "https://download.jetbrains.com/idea/ideaIC-2016.2.1.tar.gz",
)

# The plugin api for CLion 2016.1.3. This is required to build CLwB,
# and run integration tests.
new_http_archive(
    name = "clion_latest",
    build_file = "intellij_platform_sdk/BUILD.clion",
    url = "https://download.jetbrains.com/cpp/CLion-2016.2.1.tar.gz",
)

# The plugin api for Android Studio 2.2 stable. This is required to build ASwB,
# and run integration tests.
new_http_archive(
    name = "android_studio_latest",
    build_file = "intellij_platform_sdk/BUILD.android_studio",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2.2.0.12/android-studio-ide-145.3276617-linux.zip",
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

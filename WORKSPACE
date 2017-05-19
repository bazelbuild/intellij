workspace(name = "intellij_with_bazel")

# Long-lived download links available at: https://www.jetbrains.com/intellij-repository/releases

# The plugin api for IntelliJ 2017.1.1. This is required to build IJwB,
# and run integration tests.
new_http_archive(
    name = "intellij_ce_2017_1_1",
    build_file = "intellij_platform_sdk/BUILD.idea",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2017.1.1/ideaIC-2017.1.1.zip",
)

# The plugin api for IntelliJ 2016.3.1. This is required to build IJwB,
# and run integration tests.
new_http_archive(
    name = "intellij_ce_2016_3_1",
    build_file = "intellij_platform_sdk/BUILD.idea",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2016.3.1/ideaIC-2016.3.1.zip",
)

# The plugin api for IntelliJ 2016.2.4. This is required to build IJwB,
# and run integration tests.
new_http_archive(
    name = "IC_162_2032_8",
    build_file = "intellij_platform_sdk/BUILD.idea",
    url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/2016.2.4/ideaIC-2016.2.4.zip",
)

# The plugin api for CLion 2016.2.2. This is required to build CLwB,
# and run integration tests.
new_http_archive(
    name = "CL_162_1967_7",
    build_file = "intellij_platform_sdk/BUILD.clion",
    url = "https://download.jetbrains.com/cpp/CLion-2016.2.2.tar.gz",
)

# The plugin api for CLion 2016.3.2. This is required to build CLwB,
# and run integration tests.
new_http_archive(
    name = "clion_2016_3_2",
    build_file = "intellij_platform_sdk/BUILD.clion",
    url = "https://download.jetbrains.com/cpp/CLion-2016.3.2.tar.gz",
)

# The plugin api for CLion 2017.1.1. This is required to build CLwB,
# and run integration tests.
new_http_archive(
    name = "clion_2017_1_1",
    build_file = "intellij_platform_sdk/BUILD.clion",
    url = "https://download.jetbrains.com/cpp/CLion-2017.1.1.tar.gz",
)

# The plugin api for Android Studio 2.3.1. This is required to build ASwB,
# and run integration tests.
new_http_archive(
    name = "android_studio_2_3_1_0",
    build_file = "intellij_platform_sdk/BUILD.android_studio",
    url = "https://dl.google.com/dl/android/studio/ide-zips/2.3.1.0/android-studio-ide-162.3871768-linux.zip",
)

# Python plugin for IntelliJ CE 2016.3. Required at compile-time for python-specific features.
new_http_archive(
    name = "python_2016_3",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python/lib/python.jar'],",
        "    visibility = ['//visibility:public'],",
        ")"]),
    url = "https://plugins.jetbrains.com/files/7322/32326/python-community-163.298.zip",
)

# Python plugin for IntelliJ CE 2017.1. Required at compile-time for python-specific features.
new_http_archive(
    name = "python_2017_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'python',",
        "    jars = ['python-ce/lib/python-ce.jar'],",
        "    visibility = ['//visibility:public'],",
        ")"]),
    url = "https://plugins.jetbrains.com/files/7322/33704/python-ce-2017.1.171.3780.116.zip",
)

# Scala plugin for IntelliJ CE 2017.1. Required at compile-time for scala-specific features.
new_http_archive(
    name = "scala_2017_1",
    build_file_content = "\n".join([
        "java_import(",
        "    name = 'scala-library',",
        "    jars = ['Scala/lib/scala-library.jar'],",
        ")",
        "",
        "java_import(",
        "    name = 'scala',",
        "    jars = ['Scala/lib/scala-plugin.jar'],",
        "    runtime_deps = [':scala-library'],",
        "    visibility = ['//visibility:public'],",
        ")"]),
    url = "https://plugins.jetbrains.com/files/1347/33637/scala-intellij-bin-2017.1.15.zip",
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

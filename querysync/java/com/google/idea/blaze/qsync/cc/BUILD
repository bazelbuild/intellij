package(default_visibility = [
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    "//querysync:__subpackages__",
])

java_library(
    name = "cc",
    srcs = glob(["*.java"]),
    deps = [
        "//proto:cc_compilation_info_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//shared",
        "//third_party/auto_value",
        "@com_google_guava_guava//jar",
    ],
)

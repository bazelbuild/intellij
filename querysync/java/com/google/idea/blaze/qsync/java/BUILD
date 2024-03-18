package(default_visibility = [
    "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    "//querysync:__subpackages__",
])

proto_library(
    name = "java_target_info",
    srcs = ["java_target_info.proto"],
    visibility = [
        "//proto:__pkg__",
    ],
)

java_proto_library(
    name = "java_target_info_java_proto",
    deps = [":java_target_info"],
)

java_library(
    name = "java",
    srcs = glob(["*.java"]),
    deps = [
        ":java_target_info_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//shared",
        "//third_party/auto_value",
        "@com_google_guava_guava//jar",
    ],
)

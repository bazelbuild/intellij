java_library(
    name = "build",
    srcs = glob(["*.java"]),
    visibility = [
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
        "//querysync:__subpackages__",
    ],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/cc:cc_compilation_info_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/java:java_target_info_java_proto",
        "//shared",
        "//shared:artifact",
        "//shared:vcs",
        "//third_party/auto_value",
        "@com_google_guava_guava//jar",
    ],
)
#

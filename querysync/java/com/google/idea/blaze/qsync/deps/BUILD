java_library(
    name = "deps",
    srcs = glob(["*.java"]),
    visibility = [
        "//javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
        "//querysync:__subpackages__",
    ],
    deps = [
        "//querysync/java/com/google/idea/blaze/qsync/build_output",
        "//shared",
        "@com_google_guava_guava//jar",
    ],
)

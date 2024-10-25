load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = [
    ":__subpackages__",
    "//aswb:__subpackages__",
    "//base:__subpackages__",
    "//cpp:__subpackages__",
    "//java:__subpackages__",
    "//java/com/google/devtools/intellij/blaze/plugin/base:__subpackages__",
    "//java/com/google/devtools/intellij/blaze/plugin/cpp:__subpackages__",
    "//java/com/google/devtools/intellij/blaze/plugin/querysync:__subpackages__",
    "//python:__subpackages__",
    "//querysync/javatests:__subpackages__",
])

# A build rule to expose individual targets under java/ to users
java_library(
    name = "querysync",
    exports = [
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//querysync/java/com/google/idea/blaze/qsync/cc",
        "//querysync/java/com/google/idea/blaze/qsync/cc:cc_compilation_info_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/deps",
        "//querysync/java/com/google/idea/blaze/qsync/java",
        "//querysync/java/com/google/idea/blaze/qsync/java:java_target_info_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/project",
        "//querysync/java/com/google/idea/blaze/qsync/project:language_class_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//querysync/java/com/google/idea/blaze/qsync/query",
        "//querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
    ],
)

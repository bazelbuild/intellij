package(
    default_visibility = [
        "//base:__subpackages__",
        "//querysync/javatests:__subpackages__",
    ],
)

# A build rule to expose individual targets under java/ to users
java_library(
    name = "querysync",
    exports = [
        "//querysync/java/com/google/idea/blaze/qsync",
        "//querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
    ],
)

test_suite(
    name = "querysync_tests",
    tests = [
        "//querysync/javatests/com/google/idea/blaze/qsync:BuildGraphTest",
        "//querysync/javatests/com/google/idea/blaze/qsync:GraphToProjectConverterTest",
    ],
)

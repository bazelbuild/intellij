load("@contrib_rules_jvm//docs:stardoc-input.bzl", "java_junit5_test")
load("@contrib_rules_jvm//java:defs.bzl", "junit5_deps")
load("@rules_java//java:java_library.bzl", "java_library")
load("@rules_java//java:java_test.bzl", "java_test")
load("@rules_jvm_external//:defs.bzl", "artifact")

EXTRA_JUNIT_DEPS = [
    artifact("org.junit.jupiter:junit-jupiter-api"),
    artifact("org.junit.jupiter:junit-jupiter-params"),
]

java_library(
    name = "greeting_lib",
    srcs = ["src/com/example/Greeting.java"],
    visibility = ["//visibility:public"],
)

java_test(
    name = "greeting_test_junit4",
    srcs = [
        "tests/com/example/junit4/GreetingTest.java",
    ],
    test_class = "com.example.junit4.GreetingTest",
    deps = [
        ":greeting_lib",
        artifact("junit:junit"),
    ],
)

java_test(
    name = "parameterized_greeting_test_junit4",
    srcs = [
        "tests/com/example/junit4/ParameterizedGreetingTest.java",
    ],
    test_class = "com.example.junit4.ParameterizedGreetingTest",
    deps = [
        ":greeting_lib",
        artifact("junit:junit"),
    ],
)

java_junit5_test(
    name = "greeting_test_junit5",
    size = "small",
    srcs = [
        "tests/com/example/junit5/GreetingTest.java",
    ],
    test_class = "com.example.junit5.GreetingTest",
    deps = [
        ":greeting_lib",
    ] + junit5_deps() + EXTRA_JUNIT_DEPS,
)

java_junit5_test(
    name = "parameterized_greeting_test_junit5",
    size = "small",
    srcs = [
        "tests/com/example/junit5/ParameterizedGreetingTest.java",
    ],
    test_class = "com.example.junit5.ParameterizedGreetingTest",
    deps = [
        ":greeting_lib",
    ] + junit5_deps() + EXTRA_JUNIT_DEPS,
)

java_test(
    name = "env_vars_test",
    srcs = ["tests/com/example/EnvVarsTest.java"],
    test_class = "com.example.EnvVarsTest",
    deps = [
        artifact("junit:junit"),
    ],
)

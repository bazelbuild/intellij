java_library(
    name = "greeting_lib",
    srcs = ["src/com/example/Greeting.java"],
    visibility = ["//visibility:public"],
)

java_test(
    name = "greeting_test",
    srcs = ["tests/com/example/GreetingTest.java"],
    test_class = "com.example.GreetingTest",
    deps = [
        ":greeting_lib",
        "//codegen_hello:generated_hello_lib",
        "@junit//jar",
    ],
)

java_test(
    name = "env_vars_test",
    srcs = ["tests/com/example/EnvVarsTest.java"],
    test_class = "com.example.EnvVarsTest",
    deps = [
        "@junit//jar",
    ],
)

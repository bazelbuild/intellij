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
        "@junit//jar",
    ],
)

load("@rules_java//java:java_binary.bzl", "java_binary")

java_binary(
    name = "hello_greeter",
    srcs = ["src/com/example/HelloGreeter.java"],
    main_class = "com.example.HelloGreeter",
    deps = ["//greeting_lib"],
)

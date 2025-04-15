load("@rules_java//java:java_binary.bzl", "java_binary")

java_binary(
    name = "hi_greeter",
    srcs = ["src/com/example/HiGreeter.java"],
    main_class = "com.example.HiGreeter",
    deps = ["//greeting_lib"],
)

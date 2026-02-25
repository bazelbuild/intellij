load("//build_defs:build_defs.bzl", "intellij_plugin_library")
load("//third_party/grammar_kit:grammar_kit_lexer.bzl", "grammar_kit_lexer")

grammar_kit_lexer(
    name = "proto_lexer",
    src = "proto.flex",
    class_name = "_ProtoLexer",
)

grammar_kit_lexer(
    name = "string_lexer",
    src = "string.flex",
    class_name = "_StringLexer",
)

intellij_plugin_library(
    name = "prototext",
    srcs = [
        ":proto_lexer",
        ":string_lexer",
    ] + glob([
        "*.kt",
        "*.java",
    ]),
    visibility = ["//base:__subpackages__"],
)

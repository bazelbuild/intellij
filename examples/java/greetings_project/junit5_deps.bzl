load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")

JUNIT_JUPITER_VERSION = "5.11.3"

JUNIT_PLATFORM_VERSION = "1.11.3"

_JUNIT5_DEPS_TO_FETCH = [
    {
        "name": "junit_platform_launcher",
        "coords": "org.junit.platform:junit-platform-launcher:%s" % JUNIT_PLATFORM_VERSION,
        "artifact_sha256": "b4727459201b0011beb0742bd807421a1fc8426b116193031ed87825bc2d4f04",
    },
    {
        "name": "junit_platform_reporting",
        "coords": "org.junit.platform:junit-platform-reporting:%s" % JUNIT_PLATFORM_VERSION,
        "artifact_sha256": "b8e19dbebcae7d1ff30b9d767047fbf3694027c33dfa423b371693b7f6679ed1",
    },
    {
        "name": "junit_platform_commons",
        "coords": "org.junit.platform:junit-platform-commons:%s" % JUNIT_PLATFORM_VERSION,
        "artifact_sha256": "be262964b0b6b48de977c61d4f931df8cf61e80e750cc3f3a0a39cdd21c1008c",
    },
    {
        "name": "junit_platform_engine",
        "coords": "org.junit.platform:junit-platform-engine:%s" % JUNIT_PLATFORM_VERSION,
        "artifact_sha256": "0043f72f611664735da8dc9a308bf12ecd2236b05339351c4741edb4d8fab0da",
    },
    {
        "name": "junit_jupiter_api",
        "coords": "org.junit.jupiter:junit-jupiter-api:%s" % JUNIT_JUPITER_VERSION,
        "artifact_sha256": "5d8147a60f49453973e250ed68701b7ff055964fe2462fc2cb1ec1d6d44889ba",
    },
    {
        "name": "junit_jupiter_params",
        "coords": "org.junit.jupiter:junit-jupiter-params:%s" % JUNIT_JUPITER_VERSION,
        "artifact_sha256": "0f798ebec744c4e6605fd4f2072f41a8e989e2d469e21db5aa67cf799c0b51ec",
    },
    {
        "name": "junit_jupiter_engine",
        "coords": "org.junit.jupiter:junit-jupiter-engine:%s" % JUNIT_JUPITER_VERSION,
        "artifact_sha256": "e62420c99f7c0d59a2159a2ef63e61877e9c80bd722c03ca8bf3bdcea050a589",
    },
    {
        "name": "opentest4j",
        "coords": "org.opentest4j:opentest4j:1.3.0",
        "artifact_sha256": "48e2df636cab6563ced64dcdff8abb2355627cb236ef0bf37598682ddf742f1b",
    },
]

JUNIT5_DEPS = ["@{name}//jar".format(name = dep["name"]) for dep in _JUNIT5_DEPS_TO_FETCH]

def instantiate_junit5_deps():
    for dep in _JUNIT5_DEPS_TO_FETCH:
        jvm_maven_import_external(
            name = dep["name"],
            artifact = dep["coords"],
            artifact_sha256 = dep["artifact_sha256"],
            licenses = ["notice"],  # Common Public License 1.0
            server_urls = ["https://repo1.maven.org/maven2"],
        )

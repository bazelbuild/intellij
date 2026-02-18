#include <catch2/catch_test_macros.hpp>

TEST_CASE("Test0") {
    REQUIRE(0 == 0);
}

TEST_CASE("Test1") {
    REQUIRE(1 == 1);
}

TEST_CASE("FilteredTest") {
    REQUIRE(2 == 2);
}

TEST_CASE("SkippedTest") {
    REQUIRE(3 == 3);
}